/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.i
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob.Companion.toImmediateErrorJob
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.ctrl.TempTorCmdQueue
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@OptIn(ExperimentalKmpTorApi::class, InternalKmpTorApi::class)
internal class RealTorRuntime private constructor(
    private val generator: TorConfigGenerator,
    private val networkObserver: NetworkObserver,
    requiredTorEvents: Set<TorEvent>,
    serviceFactoryHandler: HandlerWithContext?,
    dispatcher: CoroutineDispatcher,
    observersTorEvent: Set<TorEvent.Observer>,
    defaultExecutor: OnEvent.Executor,
    @Suppress("RemoveRedundantQualifierName")
    observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
):  AbstractRuntimeEventProcessor(
    generator.environment.staticTag(),
    observersRuntimeEvent,
    defaultExecutor,
    observersTorEvent,
),  FileID by generator,
    TorRuntime
{

    @Volatile
    private var _lifecycle: Destroyable? = null
    @Volatile
    private var _cmdQueue: TempTorCmdQueue? = null

    private val enqueueLock = SynchronizedObject()
    private val actionStack = Stack<ActionJob.Sealed>(10)
    private val actionProcessor = ActionProcessor()

    private val destroyedErrMsg by lazy { "$this.isDestroyed[true]" }

    private val requiredTorEvents = LinkedHashSet<TorEvent>(3 + requiredTorEvents.size, 1.0f).apply {
        add(TorEvent.CONF_CHANGED)
        add(TorEvent.NOTICE)
        addAll(requiredTorEvents)
    }.toImmutableSet()

    protected override val debug: Boolean get() = generator.environment.debug
    protected override val isService: Boolean = serviceFactoryHandler != null
    protected override val handler: HandlerWithContext = serviceFactoryHandler ?: super.handler

    @Suppress("PrivatePropertyName")
    private val NOTIFIER = object : Notifier {
        public override fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data) {
            event.notifyObservers(data)
        }
    }

    private val scope = CoroutineScope(context =
        CoroutineName(toString())
        + SupervisorJob()
        + dispatcher
    )

    private val factory = TorCtrl.Factory(
        staticTag = generator.environment.staticTag(),
        observers = TorEvent.entries().let { events ->
            val tag = generator.environment.staticTag()
            events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
                when (event) {
                    is TorEvent.CONF_CHANGED -> ConfChangedObserver()
                    is TorEvent.NOTICE -> NoticeObserver()
                    else -> event.observer(tag) { event.notifyObservers(it) }
                }
            }
        },
        interceptors = setOf(
            TorCmdInterceptor.intercept<TorCmd.SetEvents> { _, cmd ->
                if (cmd.events.containsAll(requiredTorEvents)) {
                    cmd
                } else {
                    TorCmd.SetEvents(cmd.events + requiredTorEvents)
                }
            },
            TorCmdInterceptor.intercept<TorCmd.Signal.NewNym> { job, cmd ->
                job.invokeOnCompletion {
                    if (job.isError) return@invokeOnCompletion
                    // TODO: Listen for TorEvent.NOTICE rate-limit
                }
                cmd
            },
            TorCmdJob.interceptor { job ->
                EXECUTE.CMD.notifyObservers(job)
            },
        ),
        defaultExecutor = OnEvent.Executor.Immediate,
        debugger = ItBlock { log ->
            if (!debug) return@ItBlock

            // Debug logs are all formatted as TorCtrl@<hashCode> <log>
            val i = log.indexOf('@')
            val formatted = if (i == -1) {
                log
            } else {
                log.substring(0, i) + "[fid=$fidEllipses]" + log.substring(i)
            }

            LOG.DEBUG.notifyObservers(formatted)
        },
        handler = handler,
    )

    private val connectivity = if (networkObserver == NetworkObserver.noOp()) null else ConnectivityObserver()

    @JvmSynthetic
    internal fun handler(): UncaughtException.Handler = handler

    public override fun environment(): TorRuntime.Environment = generator.environment

    public override fun enqueue(
        action: Action,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Unit>,
    ): QueuedJob {
        var errorMsg = destroyedErrMsg

        if (destroyed) {
            return onFailure.toImmediateErrorJob(
                action.name,
                IllegalStateException(errorMsg),
                handler,
            )
        }

        val job = synchronized(enqueueLock) {
            if (destroyed) return@synchronized null

            val job = when (action) {
                Action.StartDaemon -> {
                    val start = ActionJob.StartJob(onSuccess, onFailure, handler)
                    if (_cmdQueue == null) {
                        _cmdQueue = factory.tempQueue()
                    }
                    start
                }
                Action.StopDaemon -> {
                    ActionJob.StopJob(onSuccess, onFailure, handler)
                }
                Action.RestartDaemon -> {
                    val restart = ActionJob.RestartJob(onSuccess, onFailure, handler)
                    if (_cmdQueue == null) {
                        _cmdQueue = factory.tempQueue()
                    }
                    restart
                }

                // A result of expect/actual. Will never occur.
                else -> {
                    errorMsg = "Action[name=${action.name}] is not supported"
                    null
                }
            }

            if (job != null) {
                actionStack.push(job)
                actionProcessor.start()
            }

            job
        }

        return job ?: onFailure.toImmediateErrorJob(
            action.name,
            IllegalStateException(errorMsg),
            handler,
        )
    }

    public override fun <Response: Any> enqueue(
        cmd: TorCmd.Unprivileged<Response>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Response>
    ): QueuedJob {
        var errorMsg = destroyedErrMsg

        if (destroyed) {
            return onFailure.toImmediateErrorJob(
                cmd.keyword,
                IllegalStateException(errorMsg),
                handler,
            )
        }

        val cmdQueue = synchronized(enqueueLock) {
            if (destroyed) return@synchronized null

            errorMsg = "Tor is stopped or stopping"
            _cmdQueue
        }

        return cmdQueue?.enqueue(cmd, onFailure, onSuccess) ?: onFailure.toImmediateErrorJob(
            cmd.keyword,
            IllegalStateException(errorMsg),
            handler,
        )
    }

    protected override fun onDestroy(): Boolean {
        if (!isService) {
            NOTIFIER.w(this, "onDestroy called but isService is false")
            return false
        }

        if (!super.onDestroy()) return false
        scope.cancel()
        NOTIFIER.d(this, "Scope Cancelled")

        val (stack, queue) = synchronized(enqueueLock) {
            val stack = actionStack.toList()
            actionStack.clear()

            val queue = _cmdQueue
            _cmdQueue = null

            stack to queue
        }

        queue?.connection?.destroy()
        queue?.destroy()

        if (stack.isNotEmpty()) {
            NOTIFIER.d(this, "Interrupting/Completing ActionJobs")
        }

        stack.forEach { job ->
            if (job is ActionJob.StopJob) {
                job.completion()
            } else {
                job.error(InterruptedException(destroyedErrMsg))
            }
        }

        connectivity?.let { observer ->
            networkObserver.unsubscribe(observer)
            NOTIFIER.lce(Lifecycle.Event.OnUnsubscribed(observer))
        }

        NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
        return true
    }

    public override fun toString(): String = toFIDString(includeHashCode = isService)

    init {
        NOTIFIER.lce(Lifecycle.Event.OnCreate(this))

        connectivity?.let { observer ->
            networkObserver.subscribe(observer)
            NOTIFIER.lce(Lifecycle.Event.OnSubscribed(observer))
        }
    }

    private inner class ActionProcessor: FileID by this {

        @Volatile
        private var _processorJob: Job? = null
        private val processorLock = SynchronizedObject()

        internal fun start() {
            synchronized(processorLock) {
                if (destroyed) return@synchronized
                if (_processorJob?.isActive == true) return@synchronized

                _processorJob = scope.launch { loop() }
            }
        }

        private suspend fun CoroutineScope.loop() {
            NOTIFIER.d(this@ActionProcessor, "Processing Jobs")

            while (isActive) {
                val job = synchronized(processorLock) {
                    val job = processStack()

                    if (job == null) _processorJob = null
                    job
                }

                if (job == null) break

                EXECUTE.ACTION.notifyObservers(job)

                try {
                    when (job) {
                        is ActionJob.StartJob -> job.doStart()
                        is ActionJob.StopJob -> job.doStop()
                        is ActionJob.RestartJob -> job.doRestart()
                    }
                } catch (t: Throwable) {
                    if (t !is SuccessCancellationException) {
                        job.error(t)
                    }

                    Unit
                } finally {
                    job.completion()

                // Ensures that suspend functions return
                }.let {}
            }
        }

        private fun processStack(): ActionJob.Sealed? {
            if (destroyed) return null

            val (executables, execute) = synchronized(enqueueLock) {

                var execute: ActionJob.Sealed? = null
                val executables = ArrayList<Executable>((actionStack.size - 1).coerceAtLeast(1))

                while (actionStack.isNotEmpty()) {
                    // LIFO
                    val popped = actionStack.pop()

                    if (execute == null) {
                        try {
                            popped.executing()
                            execute = popped
                        } catch (_: IllegalStateException) {
                            // cancelled
                        }
                        continue
                    }

                    if (popped.isCompleting || !popped.isActive) continue

                    // Last job on the stack is executed, while all others are
                    // grouped such that the appropriate completion is had for each
                    // depending on what it is compared to what is being executed.
                    //
                    // e.g. StopJob is being executed, so all StartJob & RestartJob
                    // should be interrupted & all StopJobs complete alongside the
                    // one executing.
                    when (execute) {
                        is ActionJob.RestartJob,
                        is ActionJob.StartJob -> execute.configureStartedCompletion(popped)
                        is ActionJob.StopJob -> execute.configureStoppedCompletion(popped)
                    }.let { executables.add(it) }
                }

                var isOldQueueAttached = false

                if (execute is ActionJob.StopJob) {
                    val oldQueue = _cmdQueue
                    _cmdQueue = null
                    if (oldQueue != null) {
                        execute.attachOldQueue(oldQueue)
                        isOldQueueAttached = true
                    }
                }

                if (execute is ActionJob.RestartJob) {
                    val oldQueue = _cmdQueue

                    // If there is no connection attached, then those
                    // jobs are waiting for tor to start and attach a
                    // connection. e.g. Restart was issued while tor
                    // was stopped.
                    if (oldQueue?.connection != null) {
                        val newQueue = factory.tempQueue()
                        _cmdQueue = newQueue
                        execute.attachOldQueue(oldQueue)
                        isOldQueueAttached = true

                        // If restart fails. Need to destroy the new queue
                        execute.invokeOnCompletion {
                            if (execute.isSuccess) return@invokeOnCompletion
                            // Restart failed.

                            if (_cmdQueue == newQueue) {
                                synchronized(enqueueLock) {
                                    if (_cmdQueue == newQueue) {
                                        _cmdQueue = null
                                    }
                                }
                            }

                            newQueue.connection?.destroy()
                            newQueue.destroy()
                        }
                    }
                }

                // Ensure that, in the event of a failure when starting, onDestroy
                // is called in the event we are acting as a service.
                if (execute is ActionJob.StartJob || execute is ActionJob.RestartJob) {
                    val lifecycle = _lifecycle
                    if (lifecycle != null) {
                        execute.invokeOnCompletion {
                            if (execute.isError) {
                                lifecycle.destroy()
                            }
                        }
                    }
                }

                if (isOldQueueAttached) {
                    executables.add(Executable {
                        NOTIFIER.d(this@ActionProcessor, "Old TorCtrl queue was attached to $execute")
                    })
                }

                executables to execute
            }

            executables.forEach { it.execute() }

            return execute
        }

        // Executing ActionJob's final state is that of Started (so either
        // StartJob or RestartJob).
        //
        // - Attach all previously queued StartJob or RestartJob to the job executing.
        // - Interrupt all StopJob
        private fun ActionJob.Sealed.configureStartedCompletion(popped: ActionJob.Sealed): Executable = when (popped) {
            is ActionJob.StartJob,
            is ActionJob.RestartJob -> {
                val executing = this

                executing.invokeOnCompletion {
                    executing.onErrorCause?.let { cause ->
                        popped.error(cause)
                        return@invokeOnCompletion
                    }

                    popped.completion()
                }

                Executable {
                    NOTIFIER.d(this@ActionProcessor, "Attaching $popped as a child to $executing")
                }
            }
            is ActionJob.StopJob -> {
                Executable {
                    NOTIFIER.d(this@ActionProcessor, "$popped was interrupted by $this")
                    popped.error(InterruptedException("Interrupted by $this"))
                }
            }
        }

        // Executing ActionJob's final state is that of Stopped (or destroyed if is a service).
        //
        // - Attach all previously queued StopJob to the job executing.
        // - Interrupt all StartJob & RestartJob
        private fun ActionJob.StopJob.configureStoppedCompletion(popped: ActionJob.Sealed): Executable = when (popped) {
            is ActionJob.RestartJob,
            is ActionJob.StartJob -> {
                Executable {
                    NOTIFIER.d(this@ActionProcessor, "$popped was interrupted by $this")
                    popped.error(InterruptedException("Interrupted by $this"))
                }
            }
            is ActionJob.StopJob -> {
                val executing = this

                executing.invokeOnCompletion {
                    executing.onErrorCause?.let { cause ->
                        popped.error(cause)
                        return@invokeOnCompletion
                    }

                    popped.completion()
                }

                Executable {
                    NOTIFIER.d(this@ActionProcessor, "Attaching $popped as a child to $executing")
                }
            }
        }

        private suspend fun ActionJob.Sealed.doStart() {
            require(this !is ActionJob.StopJob) { "doStart cannot be called for $this" }
            ensureActive()

            delay(500.milliseconds)
            // TODO

            // If there is a lifecycle, attach a handler to destroy the connection.
            // Also attach a handler on the control connection to dispose of the
            // lifecycle handler when it is destroyed.
        }

        private suspend fun ActionJob.Sealed.doStop() {
            require(this !is ActionJob.StartJob) { "doStop cannot be called for $this" }
            ensureActive()

            val lifecycle = _lifecycle
            if (this is ActionJob.StopJob && lifecycle != null) {
                NOTIFIER.i(this@ActionProcessor, "Lifecycle is present (Service). Destroying immediately.")

                oldCmdQueue?.connection?.destroy()
                oldCmdQueue?.destroy()
                lifecycle.destroy()
                return
            }

            val oldQueue = oldCmdQueue

            if (oldQueue == null) {
                NOTIFIER.d(this@ActionProcessor, "TorCtrl queue not present. Already shutdown.")
                return
            }

            val connection = oldQueue.connection

            if (connection == null) {
                NOTIFIER.d(this@ActionProcessor, "TorCtrl connection not present. Already shutdown.")

                // Interrupt any temporarily queued jobs. If this is RestartJob,
                // it will only attach a queue if there's a connection present.
                oldQueue.destroy()
                return
            }

            try {
                // Try a clean shutdown which will destroy itself
                // and interrupt all the jobs.
                connection.executeAsync(TorCmd.Signal.Shutdown)
            } catch (_: Throwable) {
                NOTIFIER.w(this@ActionProcessor, "Clean shutdown failed. Closing connection forcefully.")
                connection.destroy()
            }
        }

        private suspend fun ActionJob.RestartJob.doRestart() {
            doStop()
            doStart()
        }

        private suspend fun ActionJob.Sealed.ensureActive() {
            if (!currentCoroutineContext().isActive) {
                // Scope cancelled (onDestroy was called)
                throw if (this is ActionJob.StopJob) {
                    SuccessCancellationException()
                } else {
                    InterruptedException(destroyedErrMsg)
                }
            }

            if (isCompleting || !isActive) {
                throw SuccessCancellationException()
            }
        }

        private inner class SuccessCancellationException: CancellationException()

        public override fun equals(other: Any?): Boolean = other is ActionProcessor && other.hashCode() == hashCode()
        public override fun hashCode(): Int = this@RealTorRuntime.hashCode()
        public override fun toString(): String = this.toFIDString(includeHashCode = isService)
    }

    private inner class ConnectivityObserver: AbstractConnectivityObserver(scope), FileID by this {
        public override fun equals(other: Any?): Boolean = other is ConnectivityObserver && other.hashCode() == hashCode()
        public override fun hashCode(): Int = this@RealTorRuntime.hashCode()
        public override fun toString(): String = this.toFIDString(includeHashCode = isService)
    }

    private inner class ConfChangedObserver: TorCtrlObserver.ConfChanged(generator.environment.staticTag()) {
        protected override fun notify(data: String) {
            super.notify(data)
            event.notifyObservers(data)
        }
    }

    private inner class NoticeObserver: TorCtrlObserver.Notice(generator.environment.staticTag()) {
        protected override fun notify(data: String) {
            super.notify(data)
            event.notifyObservers(data)
        }
    }

    internal companion object {

        @JvmSynthetic
        @Suppress("RemoveRedundantQualifierName")
        internal fun of(
            generator: TorConfigGenerator,
            networkObserver: NetworkObserver,
            requiredTorEvents: Set<TorEvent>,
            observersTorEvent: Set<TorEvent.Observer>,
            defaultExecutor: OnEvent.Executor,
            observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
        ): TorRuntime = generator.environment.serviceFactoryLoader()?.let { loader ->
            var n: Notifier? = null

            val initializer = TorRuntime.ServiceFactory.Initializer.of { startService ->
                RealServiceFactoryCtrl(
                    generator,
                    networkObserver,
                    requiredTorEvents,
                    observersTorEvent,
                    defaultExecutor,
                    observersRuntimeEvent,
                    startService,
                ).also { n = it }
            }

            val factory = loader.load(initializer)

            n?.lce(Lifecycle.Event.OnCreate(factory))

            factory
        } ?: RealTorRuntime(
            generator,
            networkObserver,
            requiredTorEvents,
            null,
            generator.environment.newRuntimeDispatcher(),
            observersTorEvent,
            defaultExecutor,
            observersRuntimeEvent,
        )
    }

    private class RealServiceFactoryCtrl(
        private val generator: TorConfigGenerator,
        private val builderObserver: NetworkObserver,
        builderRequiredEvents: Set<TorEvent>,
        observersTorEvent: Set<TorEvent.Observer>,
        defaultExecutor: OnEvent.Executor,
        @Suppress("RemoveRedundantQualifierName")
        observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
        private val startService: () -> Unit,
    ):  AbstractRuntimeEventProcessor(
        generator.environment.staticTag(),
        observersRuntimeEvent,
        defaultExecutor,
        observersTorEvent
    ),  ServiceFactoryCtrl,
        FileID by generator,
        Notifier
    {

        @Volatile
        private var _instance: Lifecycle.DestroyableTorRuntime? = null
        @Volatile
        private var _startServiceJob: Job? = null
        @Volatile
        private var _cmdQueue: TempTorCmdQueue? = null
        private val actionStack = Stack<ActionJob.Sealed>(1)
        private val lock = SynchronizedObject()

        private val builderRequiredEvents = builderRequiredEvents.toImmutableSet()
        protected override val debug: Boolean get() = generator.environment.debug

        // Using lazy in case TorRuntime.ServiceFactory.Loader throws
        // upon initialization of a bad implementation, we do not want
        // to create an unused dispatcher for Jvm & Native
        private val dispatcher by lazy { generator.environment.newRuntimeDispatcher() }

        override val binder: ServiceCtrlBinder = ServiceCtrlBinder()

        public override fun environment(): TorRuntime.Environment = generator.environment

        public override fun enqueue(
            action: Action,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Unit>,
        ): QueuedJob {
            _instance
                ?.enqueue(action, onFailure, onSuccess)
                ?.let { return it }

            var execute: (() -> Unit)? = null
            var job: QueuedJob? = null

            val instance: Lifecycle.DestroyableTorRuntime? = synchronized(lock) {
                // If there's an instance available after obtaining the
                // lock use that instead.
                //
                // Want to enqueue outside the lock lambda b/c it could
                // invoke things immediately and such.
                _instance?.let { return@synchronized it }


                val nonNullJob: QueuedJob = if (actionStack.isEmpty()) {
                    // First call to enqueue for starting the service

                    if (action == Action.StopDaemon) {
                        val stop = ActionJob.StopJob(onSuccess, onFailure, handler)

                        // We're already stopped, complete successfully,
                        // but do so outside the synchronized lambda before
                        // returning the job
                        execute = { stop.completion() }

                        stop
                    } else {
                        // Whether it's Restart or Start action, always use Start
                        // for the first ActionJob (restart will do nothing...)
                        val start = ActionJob.StartJob(onSuccess, onFailure, handler, immediateExecute = true)

                        actionStack.push(start)

                        // Start the service
                        execute = ::executeStartService

                        start
                    }
                } else {
                    // Waiting to bind. Add to temporary queue. Once
                    // transferred, RealTorCtrl will handle it.
                    val tempJob = if (action == Action.StopDaemon) {
                        ActionJob.StopJob(onSuccess, onFailure, handler)
                    } else {
                        // No RestartDaemon while first starting.
                        ActionJob.StartJob(onSuccess, onFailure, handler)
                    }

                    actionStack.push(tempJob)
                    tempJob
                }

                job = nonNullJob

                null
            }

            if (instance != null) {
                job = instance.enqueue(action, onFailure, onSuccess)
            }

            execute?.invoke()

            return job!!
        }

        public override fun <Success: Any> enqueue(
            cmd: TorCmd.Unprivileged<Success>,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Success>,
        ): QueuedJob {
            _instance
                ?.enqueue(cmd, onFailure, onSuccess)
                ?.let { return it }

            var job: QueuedJob? = null

            val instance: Lifecycle.DestroyableTorRuntime? = synchronized(lock) {
                // If there's an instance available after obtaining the
                // lock use that instead of creating a local queue.
                //
                // Want to enqueue outside the lock lambda b/c it could
                // invoke things immediately and such.
                _instance?.let { return@synchronized it }

                _cmdQueue?.let { cmdQueue ->
                    job = cmdQueue.enqueue(cmd, onFailure, onSuccess)
                    return@synchronized null
                }

                // No instance, nor is there an ActionJob
                // in the queue. Start has not been called.
                if (actionStack.isEmpty()) return@synchronized null

                // Waiting to bind. Create a temporary job to transfer
                // to RealTorRuntime when bind is finally called or
                val cmdQueue = TorCtrl.Factory(handler = handler).tempQueue()
                _cmdQueue = cmdQueue

                // Enqueue here
                job = cmdQueue.enqueue(cmd, onFailure, onSuccess)

                null
            }

            if (instance != null) {
                job = instance.enqueue(cmd, onFailure, onSuccess)
            }

            return job ?: onFailure.toImmediateErrorJob(
                cmd.keyword,
                IllegalStateException("Tor is not started"),
                handler
            )
        }

        public override fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data) {
            event.notifyObservers(data)
        }

        private fun executeStartService() {
            val name = CoroutineName("StartService[fid=$fidEllipses]")

            val job = CoroutineScope(name + dispatcher).launch {
                @Suppress("LocalVariableName")
                var _failure: Throwable? = null

                try {
                    startService()
                } catch (t: RuntimeException) {
                    _failure = t
                }

                // Wait for 500ms
                TimeSource.Monotonic.markNow().let { mark ->
                    // Node.js uses Dispatchers.Main so the test coroutine
                    // library will not wait an actual 500ms. Make it so
                    // there's a 500ms delay no matter what.
                    while (_failure == null && _instance == null) {
                        delay(100.milliseconds)
                        if (mark.elapsedNow() < 500.milliseconds) continue
                        if (_instance != null) break
                        _failure = InterruptedException("${name.name} timed out after 500ms")
                    }
                }

                // Has been started
                val failure = _failure ?: return@launch

                val executables = synchronized(lock) cancel@ {
                    val executables = ArrayList<Executable>(actionStack.size + 3)

                    // Interrupt or complete all ActionJob
                    actionStack.mapTo(executables) { job ->
                        when (job) {
                            is ActionJob.RestartJob,
                            is ActionJob.StartJob -> Executable { job.error(failure) }
                            is ActionJob.StopJob -> Executable { job.completion() }
                        }
                    }
                    actionStack.clear()

                    // Interrupt all TorCmdJob
                    _cmdQueue?.let { queue ->
                        _cmdQueue = null
                        executables.add(Executable { queue.connection?.destroy() })
                        executables.add(Executable { queue.destroy() })
                    }

                    executables
                }

                // onBind was called and stack + queue were transferred
                if (executables.isEmpty()) return@launch

                w(this@RealServiceFactoryCtrl, "Failed to start service. Interrupting QueuedJobs.")

                handler.withSuppression {
                    val context = name.name + " timed out"

                    executables.forEach { executable ->
                        tryCatch(context) { executable.execute() }
                    }
                }
            }

            _startServiceJob = job

            d(this, "StartService: $job")
            job.invokeOnCompletion {
                d(this, "StartService: $job")
            }
        }

        private inner class ServiceCtrlBinder: TorRuntime.ServiceFactory.Binder, FileID by this, Notifier by this {

            // Pipe all events to observers registered with ServiceFactoryCtrl
            private val observersTorEvent = TorEvent.entries().let { events ->
                val tag = generator.environment.staticTag()
                events.mapTo(LinkedHashSet(events.size + 1, 1.0f)) { event ->
                    event.observer(tag) { event.notifyObservers(it) }
                }.toImmutableSet()
            }

            // Pipe all events to observers registered with ServiceFactoryCtrl
            private val observersRuntimeEvent = RuntimeEvent.entries().let { events ->
                val tag = generator.environment.staticTag()
                events.mapTo(LinkedHashSet(events.size + 1, 1.0f)) { event ->
                    when (event) {
                        is ERROR -> event.observer(tag) { event.notifyObservers(it) }
                        is EXECUTE.ACTION -> event.observer(tag) { event.notifyObservers(it) }
                        is EXECUTE.CMD -> event.observer(tag) { event.notifyObservers(it) }
                        is LIFECYCLE -> event.observer(tag) { event.notifyObservers(it) }
                        is LOG -> event.observer(tag) { event.notifyObservers(it) }
                    }
                }.toImmutableSet()
            }

            public override fun onBind(
                serviceEvents: Set<TorEvent>,
                serviceObserverNetwork: NetworkObserver?,
                serviceObserversTorEvent: Set<TorEvent.Observer>,
                @Suppress("RemoveRedundantQualifierName")
                serviceObserversRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
            ): Lifecycle.DestroyableTorRuntime = synchronized(lock) {
                // Do not want to invoke any callbacks
                // within synchronized lambda.
                val executables = ArrayList<Executable>(3)

                _instance?.let { instance ->
                    _instance = null
                    executables.add(Executable {
                        w(this, "onBind was called before previous instance was destroyed")
                        instance.destroy()
                    })
                }

                _startServiceJob?.cancel()

                val runtime = RealTorRuntime(
                    generator,
                    serviceObserverNetwork ?: builderObserver,
                    (serviceEvents + builderRequiredEvents),
                    handler,
                    dispatcher,

                    // Put services observers first so that they are notified
                    // BEFORE static observers redirect to ServiceCtrl observers.
                    (serviceObserversTorEvent + observersTorEvent),

                    // Want to utilize Immediate here as all events will be
                    // piped to the observers registered to the ServiceFactory.
                    OnEvent.Executor.Immediate,

                    // Put services observers first so that they are notified
                    // BEFORE static observers redirect to ServiceCtrl observers.
                    (serviceObserversRuntimeEvent + observersRuntimeEvent),
                )

                val destroyable = Lifecycle.DestroyableTorRuntime.of(runtime)

                runtime._lifecycle = destroyable
                runtime.actionStack.addAll(actionStack)
                runtime._cmdQueue = _cmdQueue

                if (runtime._cmdQueue == null && runtime.actionStack.isNotEmpty()) {
                    runtime._cmdQueue = runtime.factory.tempQueue()
                }

                executables.add(Executable {
                    lce(Lifecycle.Event.OnCreate(destroyable))
                    lce(Lifecycle.Event.OnBind(this))
                })

                destroyable.invokeOnDestroy {
                    if (_instance == destroyable) {
                        synchronized(lock) {
                            if (_instance == destroyable) {
                                _instance = null
                            }
                        }
                    }

                    runtime.onDestroy()
                }
                destroyable.invokeOnDestroy {
                    lce(Lifecycle.Event.OnDestroy(destroyable))
                }
                destroyable.invokeOnDestroy {
                    lce(Lifecycle.Event.OnUnbind(this))
                }

                if (runtime.actionStack.isEmpty()) {
                    // Want to use enqueue here b/c will set up its
                    // TorCtrl command queue and all that as well.
                    val job = runtime.enqueue(
                        Action.StartDaemon,
                        OnFailure { t ->
                            if (t is CancellationException) return@OnFailure
                            if (t is InterruptedException) return@OnFailure

                            // Pipe error to RuntimeEvent.ERROR
                            // observers or crash.
                            throw t
                        },
                        OnSuccess.noOp(),
                    )

                    executables.add(Executable {
                        w(this, "Stack was empty (onBind called externally). Enqueued $job")
                    })
                } else {
                    // Stack was transferred
                    executables.add(Executable {
                        runtime.actionProcessor.start()
                    })
                }

                _instance = destroyable
                _cmdQueue = null
                actionStack.clear()

                executables to destroyable
            }.let { (executables, destroyable) ->
                executables.forEach { it.execute() }

                destroyable
            }

            public override fun equals(other: Any?): Boolean = other is ServiceCtrlBinder && other.hashCode() == hashCode()
            public override fun hashCode(): Int = this@RealServiceFactoryCtrl.hashCode()
            public override fun toString(): String = toFIDString(includeHashCode = false)
        }

        public override fun toString(): String = toFIDString(includeHashCode = false)

        init {
            lce(Lifecycle.Event.OnCreate(this))
        }
    }
}
