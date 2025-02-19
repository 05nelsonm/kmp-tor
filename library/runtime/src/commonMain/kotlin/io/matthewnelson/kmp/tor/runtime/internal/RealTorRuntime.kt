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
@file:Suppress("PrivatePropertyName")

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
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
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.checkIsNotDestroyed
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob.Companion.toImmediateErrorJob
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.ctrl.TempTorCmdQueue
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.internal.observer.ObserverConfChanged
import io.matthewnelson.kmp.tor.runtime.internal.observer.ObserverConnectivity
import io.matthewnelson.kmp.tor.runtime.internal.observer.ObserverNotice
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
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
):  AbstractTorRuntime(
    generator.environment.staticTag(),
    observersRuntimeEvent,
    defaultExecutor,
    observersTorEvent,
    INIT,
),  FileID by generator.environment
{

    @Volatile
    private var _lifecycle: Destroyable? = null
    @Volatile
    private var _cmdQueue: TempTorCmdQueue? = null

    private val enqueueLock = SynchronizedObject()
    private val actionStack = ArrayDeque<ActionJob.Sealed>(16)
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

    private val scope = CoroutineScope(context =
        CoroutineName(toString())
        + SupervisorJob()
        + dispatcher
    )

    private val manager = StateManager(scope)

    private val NOTIFIER = object : Notifier {
        override fun <Data : Any, E : RuntimeEvent<Data>> notify(event: E, data: Data) {
            event.notifyObservers(data)
        }
    }

    private val factory = TorCtrl.Factory(
        staticTag = generator.environment.staticTag(),
        observers = TorEvent.entries().let { events ->
            val tag = generator.environment.staticTag()

            events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
                when (event) {
                    is TorEvent.CONF_CHANGED -> ConfChangedObserver(manager)
                    is TorEvent.NOTICE -> NoticeObserver(manager)
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
            manager.interceptorConfigSet,
            manager.interceptorConfigReset,
            TorCmdJob.interceptor(handler, notify = { job ->
                EXECUTE.CMD.notifyObservers(job)
            }),
        ),
        defaultExecutor = OnEvent.Executor.Immediate,
        debugger = ItBlock { log ->
            if (!debug) return@ItBlock

            // Debug logs are all formatted as RealTorCtrl@<hashCode> <log>
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

    @JvmSynthetic
    internal fun handler(): UncaughtException.Handler = handler

    public override fun environment(): TorRuntime.Environment = generator.environment
    public override fun isReady(): Boolean = manager.isReady
    public override fun listeners(): TorListeners = manager.listenersOrEmpty
    public override fun state(): TorState = manager.state

    public override fun enqueue(
        action: Action,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Unit>,
    ): EnqueuedJob {
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
                Action.StartDaemon -> ActionJob.StartJob(onSuccess, onFailure, handler)
                Action.StopDaemon -> ActionJob.StopJob(onSuccess, onFailure, handler)
                Action.RestartDaemon -> ActionJob.RestartJob(onSuccess, onFailure, handler)

                // A result of expect/actual. Will never occur.
                else -> {
                    errorMsg = "Action[name=${action.name}] is not supported"
                    null
                }
            }

            if (job is ActionJob.StartJob || job is ActionJob.RestartJob) {
                if (_cmdQueue?.isDestroyed() != false) {
                    // queue is null or destroyed
                    _cmdQueue = factory.tempQueue()
                }
            }

            if (job != null) actionStack.add(job)

            job
        }

        if (job != null) actionProcessor.start(job)

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
    ): EnqueuedJob {
        var errorMsg = destroyedErrMsg

        if (destroyed) {
            return cmd.toImmediateIllegalStateJob(
                onFailure,
                errorMsg,
                handler,
            )
        }

        val cmdQueue = synchronized(enqueueLock) {
            if (destroyed) return@synchronized null

            errorMsg = "Tor is stopped or stopping"
            if (_cmdQueue?.isDestroyed() == false) {
                _cmdQueue
            } else {
                null
            }
        }

        return cmdQueue?.enqueue(cmd, onFailure, onSuccess) ?: cmd.toImmediateIllegalStateJob(
            onFailure,
            errorMsg,
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

        val (stack, cmdQueue) = synchronized(enqueueLock) {
            val stack = actionStack.toList()
            actionStack.clear()

            val queue = _cmdQueue
            _cmdQueue = null

            stack to queue
        }

        cmdQueue?.connection?.destroy()
        cmdQueue?.destroy()

        manager.update(TorState.Daemon.Off)

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

        NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
        return true
    }

    public override fun toString(): String = toFIDString(includeHashCode = isService)

    init {
        NOTIFIER.lce(Lifecycle.Event.OnCreate(this))
    }

    private inner class ActionProcessor: FileID by generator.environment {

        @Volatile
        private var _processorJob: Job? = null
        @Volatile
        private var _executingJob: ActionJob.Sealed? = null
        private val processorLock = SynchronizedObject()

        internal fun start(enqueuedJob: ActionJob.Sealed?) {
            synchronized(processorLock) {
                if (destroyed) return@synchronized

                run {
                    if (enqueuedJob !is ActionJob.StopJob) return@run
                    val executingJob = _executingJob ?: return@run
                    if (executingJob !is ActionJob.Started) return@run
                    executingJob.interruptBy(enqueuedJob)
                }

                if (_processorJob?.isActive == true) return@synchronized

                _processorJob = scope.launch { loop() }
            }
        }

        private suspend fun loop() {
            NOTIFIER.d(this, "Processing Jobs")

            var previousStartedActionFailed = false
            var notifyInterrupted: Executable? = null

            while (true) {
                yield()

                val (executables, job) = synchronized(processorLock) {
                    val result = processStack(previousStartedActionFailed)
                    previousStartedActionFailed = false

                    val job = result.second
                    if (job == null) _processorJob = null
                    _executingJob = job

                    result
                }

                if (notifyInterrupted != null) {
                    val executable = notifyInterrupted
                    notifyInterrupted = null
                    executable.execute()
                }

                executables.forEach { it.execute() }

                if (job == null) break

                if (job is ActionJob.Started) {
                    job.invokeOnCompletion {
                        val cause = job.onErrorCause ?: return@invokeOnCompletion

                        previousStartedActionFailed = true

                        if (cause !is InterruptedException) return@invokeOnCompletion
                        val message = cause.message ?: return@invokeOnCompletion
                        if (!message.contains(" was interrupted by StopJob")) return@invokeOnCompletion

                        // Move out of completion handle and execute on next loop.
                        notifyInterrupted = Executable {
                            NOTIFIER.d(this, message)
                        }
                    }
                }

                EXECUTE.ACTION.notifyObservers(job)

                try {
                    when (job) {
                        is ActionJob.StartJob -> {
                            job.doStart()
                        }
                        is ActionJob.StopJob -> {
                            job.doStop()
                        }
                        is ActionJob.RestartJob -> {
                            job.doStop()
                            job.doStart()
                        }
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

        private fun processStack(previousStartedActionFailed: Boolean): Pair<List<Executable.Once>, ActionJob.Sealed?> {
            if (destroyed) return emptyList<Executable.Once>() to null

            return synchronized(enqueueLock) {
                if (destroyed) return@synchronized emptyList<Executable.Once>() to null

                var execute: ActionJob.Sealed? = null
                val executables = ArrayList<Executable.Once>((actionStack.size - 1).coerceAtLeast(0))

                while (actionStack.isNotEmpty()) {
                    if (execute == null) {
                        // LIFO
                        val popped = actionStack.removeLast()

                        try {
                            popped.executing()
                            execute = popped
                        } catch (_: IllegalStateException) {
                            // cancelled
                        }
                        continue
                    }

                    // Attach all others from FIFI order so they complete
                    // in the order for which they were enqueued.
                    val dangler = actionStack.removeFirst()

                    if (dangler.isCompleting || !dangler.isActive) continue

                    // Last job on the stack is executed, while all others are
                    // grouped such that the appropriate completion is had for each
                    // depending on what it is compared to what is being executed.
                    //
                    // e.g. StopJob is being executed, so all StartJob & RestartJob
                    // should be interrupted & all StopJobs complete alongside the
                    // one executing.
                    when (execute) {
                        is ActionJob.Started -> execute.configureCompletionFor(dangler)
                        is ActionJob.StopJob -> execute.configureCompletionFor(dangler)
                    }.let { executables.add(it) }
                }

                // No executable actions were on the stack, and the prior
                // action (StartJob or RestartJob) had failed. Execute
                // StopJob before letting the ActionProcessor stop looping.
                if (execute == null && previousStartedActionFailed) {
                    execute = ActionJob.StopJob({}, {}, handler)
                        .also { it.executing() }
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

                if (execute is ActionJob.Started) {
                    // If null or destroyed, ensure there is fresh queue.
                    if (_cmdQueue?.isDestroyed() != false) {
                        _cmdQueue = factory.tempQueue()
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
                    }
                }

                if (isOldQueueAttached) {
                    executables.add(Executable.Once.of {
                        NOTIFIER.d(this@ActionProcessor, "TorCtrl queue attached to $execute")
                    })
                }

                if (execute == null) {
                    // Stack was empty. Processor is about to stop.
                    // Ensure state is correct before we get stopped
                    if (_cmdQueue?.connection?.isDestroyed() != false) {
                        // Is null or destroyed (no active control connection)
                        executables.add(Executable.Once.of {
                            manager.update(TorState.Daemon.Off)
                        })
                    }
                }

                executables to execute
            }
        }

        // Executing ActionJob's final state is that of Started (so either
        // StartJob or RestartJob).
        //
        // - Attach all previously queued StartJob or RestartJob to the job executing.
        // - Interrupt all StopJob
        private fun ActionJob.Started.configureCompletionFor(
            popped: ActionJob.Sealed,
        ): Executable.Once = when (popped) {
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

                Executable.Once.of {
                    NOTIFIER.d(this@ActionProcessor, "Attaching $popped as a child to $executing")
                }
            }
            is ActionJob.StopJob -> {
                Executable.Once.of {
                    NOTIFIER.d(this@ActionProcessor, "$popped was interrupted by $this")
                    popped.error(InterruptedException("Interrupted by $this"))
                }
            }
        }

        // Executing ActionJob's final state is that of Stopped (or destroyed if is a service).
        //
        // - Attach all previously queued StopJob to the job executing.
        // - Interrupt all StartJob & RestartJob
        private fun ActionJob.StopJob.configureCompletionFor(
            popped: ActionJob.Sealed,
        ): Executable.Once = when (popped) {
            is ActionJob.RestartJob,
            is ActionJob.StartJob -> {
                Executable.Once.of {
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

                Executable.Once.of {
                    NOTIFIER.d(this@ActionProcessor, "Attaching $popped as a child to $executing")
                }
            }
        }

        private suspend fun ActionJob.Started.doStart() {
            ensureActive()
            checkCancellationOrInterrupt()

            val cmdQueue = _cmdQueue

            // Should never be the case, but...
            check(cmdQueue != null) { "cmdQueue cannot be null" }
            cmdQueue.checkIsNotDestroyed()

            cmdQueue.connection?.let { ctrl ->
                // Should never be the case, but...
                ctrl.checkIsNotDestroyed()

                // Already started
                NOTIFIER.d(this@ActionProcessor, "TorCtrl connection present. Already started.")
                return
            }

            TorDaemon.start(generator, manager, NOTIFIER, scope, ::checkCancellationOrInterrupt, connect = {
                val ctrl = connection.openWith(factory)

                val lceCtrl = RealTorCtrl(ctrl)
                NOTIFIER.lce(Lifecycle.Event.OnCreate(lceCtrl))
                val observer = ConnectivityObserver(ctrl, NOTIFIER, scope)

                ctrl.invokeOnDestroy { instance ->
                    processJob.cancel()

                    if (_cmdQueue?.connection == instance) {
                        synchronized(enqueueLock) {
                            if (_cmdQueue?.connection == instance) {
                                _cmdQueue = null
                            }
                        }
                    }

                    observer.unsubscribe()
                    NOTIFIER.lce(Lifecycle.Event.OnDestroy(lceCtrl))
                }

                val processJobCompletionHandle = processJob.invokeOnCompletion { ctrl.destroy() }

                checkCancellationOrInterrupt()
                ctrl.executeAsync(authenticate)
                checkCancellationOrInterrupt()
                ctrl.executeAsync(TorCmd.Ownership.Take)
                checkCancellationOrInterrupt()
                ctrl.executeAsync(configLoad)
                checkCancellationOrInterrupt()
                ctrl.executeAsync(TorCmd.SetEvents(requiredTorEvents))

                checkCancellationOrInterrupt()
                observer.subscribe()

                ctrl.executeAsync(TorCmd.Config.Reset(options = buildSet {
                    if (networkObserver.isNetworkConnected()) {
                        add(TorOption.DisableNetwork)
                    } else {
                        NOTIFIER.w(this@RealTorRuntime, "No Network Connectivity. Waiting...")
                    }

                    add(TorOption.__OwningControllerProcess)
                }))

                checkCancellationOrInterrupt()
                cmdQueue.attach(ctrl)
                processJobCompletionHandle.dispose()
            })
        }

        private suspend fun ActionJob.Sealed.doStop() {
            require(this !is ActionJob.StartJob) { "doStop cannot be called for $this" }
            ensureActive()

            val lifecycle = _lifecycle
            if (this is ActionJob.StopJob && lifecycle != null) {
                NOTIFIER.i(this@ActionProcessor, "Lifecycle is present (Service). Destroying immediately.")
                manager.update(TorState.Daemon.Stopping)
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

            manager.update(TorState.Daemon.Stopping)

            try {
                // Try a clean shutdown which will destroy itself
                // and interrupt all the jobs.
                connection.executeAsync(TorCmd.Signal.Shutdown)
            } catch (_: Throwable) {
                NOTIFIER.w(this@ActionProcessor, "Clean shutdown failed. Closing connection forcefully.")
                connection.destroy()
            }
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

        private inner class ConnectivityObserver(
            ctrl: TorCtrl,
            @Suppress("LocalVariableName")
            NOTIFIER: RuntimeEvent.Notifier,
            scope: CoroutineScope,
        ): ObserverConnectivity(
            ctrl,
            networkObserver,
            NOTIFIER,
            scope,
        ), FileID by generator.environment {

            private val _hashCode = ctrl.hashCode()

            public override fun equals(other: Any?): Boolean = other is ConnectivityObserver && other.hashCode() == hashCode()
            public override fun hashCode(): Int = _hashCode
            public override fun toString(): String = this.toFIDString()
        }

        private inner class SuccessCancellationException: CancellationException()

        // For Lifecycle.Events notification only
        private inner class RealTorCtrl(ctrl: TorCtrl): FileID by generator.environment {
            private val _hashCode = ctrl.hashCode()

            override fun equals(other: Any?): Boolean = other is RealTorCtrl && other.hashCode() == hashCode()
            override fun hashCode(): Int = _hashCode
            override fun toString(): String = this.toFIDString()
        }

        public override fun equals(other: Any?): Boolean = other is ActionProcessor && other.hashCode() == hashCode()
        public override fun hashCode(): Int = this@RealTorRuntime.hashCode()
        public override fun toString(): String = this.toFIDString(includeHashCode = isService)
    }

    private inner class ConfChangedObserver(
        manager: TorListeners.Manager,
    ): ObserverConfChanged(manager, generator.environment.staticTag()) {
        protected override fun notify(data: String) {
            super.notify(data)
            event.notifyObservers(data)
        }
    }

    private inner class NoticeObserver(
        manager: TorListeners.Manager,
    ): ObserverNotice(manager, generator.environment.staticTag()) {
        protected override fun notify(data: String) {
            super.notify(data)
            event.notifyObservers(data)
        }
    }

    private inner class StateManager(
        scope: CoroutineScope,
    ): TorListeners.AbstractManager(scope, generator.environment) {

        val isReadyString by lazy { "Tor[fid=$fidEllipses] IS READY" }

        protected override fun notify(listeners: TorListeners) {
            LISTENERS.notifyObservers(listeners)
        }
        protected override fun notify(state: TorState) {
            STATE.notifyObservers(state)
        }
        protected override fun notifyReady() {
            READY.notifyObservers(isReadyString)
        }
    }

    private class RealServiceFactoryDriver(
        private val generator: TorConfigGenerator,
        private val builderObserver: NetworkObserver,
        builderRequiredEvents: Set<TorEvent>,
        observersTorEvent: Set<TorEvent.Observer>,
        @Suppress("RemoveRedundantQualifierName")
        observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
        private val startService: () -> Unit,
    ):  ServiceFactoryDriver(
        generator.environment.staticTag(),
        observersRuntimeEvent,
        generator.environment.defaultExecutor(),
        observersTorEvent,
        INIT,
    ),  FileID by generator.environment,
        RuntimeEvent.Notifier
    {

        @Volatile
        private var _instance: Lifecycle.DestroyableTorRuntime? = null
        @Volatile
        private var _startServiceJob: Job? = null
        @Volatile
        private var _cmdQueue: TempTorCmdQueue? = null
        private val actionStack = ArrayDeque<ActionJob.Sealed>(16)
        private val lock = SynchronizedObject()

        private val EMPTY = TorListeners.of(fid = generator.environment)
        private val STATE_OFF = TorState.of(TorState.Daemon.Off, TorState.Network.Disabled, fid = generator.environment)
        private val STATE_STARTING = STATE_OFF.copy(daemon = TorState.Daemon.Starting)

        private val builderRequiredEvents = builderRequiredEvents.toImmutableSet()
        protected override val debug: Boolean get() = generator.environment.debug

        // Using lazy in case TorRuntime.ServiceFactory.Loader throws
        // upon initialization of a bad implementation, we do not want
        // to create an unused dispatcher for Jvm & Native
        private val dispatcher by lazy { generator.environment.newRuntimeDispatcher() }

        @get:JvmSynthetic
        internal override val binder: TorRuntime.ServiceFactory.Binder = ServiceFactoryBinder()

        public override fun environment(): TorRuntime.Environment = generator.environment
        public override fun isReady(): Boolean = _instance?.isReady() ?: false
        public override fun listeners(): TorListeners = _instance?.listeners() ?: EMPTY
        public override fun state(): TorState = _instance?.state()
            ?: if (_startServiceJob?.isActive == true) STATE_STARTING else STATE_OFF

        public override fun enqueue(
            action: Action,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Unit>,
        ): EnqueuedJob {
            _instance
                ?.enqueue(action, onFailure, onSuccess)
                ?.let { return it }

            var execute: (() -> Unit)? = null
            var job: EnqueuedJob? = null

            val instance: Lifecycle.DestroyableTorRuntime? = synchronized(lock) {
                // If there's an instance available after obtaining the
                // lock use that instead.
                //
                // Want to enqueue outside the lock lambda b/c it could
                // invoke things immediately and such.
                _instance?.let { return@synchronized it }


                val nonNullJob: EnqueuedJob = if (actionStack.isEmpty()) {
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

                        actionStack.add(start)

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

                    actionStack.add(tempJob)
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
        ): EnqueuedJob {
            _instance
                ?.enqueue(cmd, onFailure, onSuccess)
                ?.let { return it }

            var job: EnqueuedJob? = null

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

            return job ?: cmd.toImmediateIllegalStateJob(
                onFailure,
                "Tor is not started",
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

                // Wait for service startup
                TimeSource.Monotonic.markNow().let { mark ->
                    // Node.js uses Dispatchers.Main so the test coroutine
                    // library will not wait an actual timeout. Make it so
                    // there's a delay no matter what.
                    val interval = 100.milliseconds

                    while (isActive) {
                        if (_failure != null) break
                        if (_instance != null) break
                        delay(interval)
                        if (_instance != null) break
                        if (mark.elapsedNow() < TIMEOUT_START_SERVICE) continue
                        _failure = InterruptedException("${name.name} timed out after 1000ms")
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

                w(this@RealServiceFactoryDriver, "Failed to start service. Interrupting EnqueuedJobs.")

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

        private inner class ServiceFactoryBinder:
            TorRuntime.ServiceFactory.Binder,
            FileID by generator.environment,
            RuntimeEvent.Notifier by this
        {

            // Pipe all events to observers registered with ServiceFactoryDriver
            private val observersTorEvent = TorEvent.entries().let { events ->
                val tag = generator.environment.staticTag()
                events.mapTo(LinkedHashSet(events.size + 1, 1.0f)) { event ->
                    event.observer(tag) { event.notifyObservers(it) }
                }.toImmutableSet()
            }

            // Pipe all events to observers registered with ServiceFactoryDriver
            private val observersRuntimeEvent = RuntimeEvent.entries().let { events ->
                val tag = generator.environment.staticTag()
                events.mapTo(LinkedHashSet(events.size + 1, 1.0f)) { event ->
                    when (event) {
                        is ERROR -> event.observer(tag) { event.notifyObservers(it) }
                        is EXECUTE.ACTION -> event.observer(tag) { event.notifyObservers(it) }
                        is EXECUTE.CMD -> event.observer(tag) { event.notifyObservers(it) }
                        is LIFECYCLE -> event.observer(tag) { event.notifyObservers(it) }
                        is LISTENERS -> event.observer(tag) { event.notifyObservers(it) }
                        is LOG -> event.observer(tag) { event.notifyObservers(it) }
                        is READY -> event.observer(tag) { event.notifyObservers(it) }
                        is STATE -> event.observer(tag) { event.notifyObservers(it) }
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
                    // BEFORE static observers redirect to ServiceFactoryDriver observers.
                    (serviceObserversTorEvent + observersTorEvent),

                    // Want to utilize Immediate here as all events will be
                    // piped to the observers registered to the ServiceFactory.
                    OnEvent.Executor.Immediate,

                    // Put services observers first so that they are notified
                    // BEFORE static observers redirect to ServiceFactoryDriver observers.
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
                        runtime.actionProcessor.start(null)
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

            public override fun equals(other: Any?): Boolean = other is ServiceFactoryBinder && other.hashCode() == hashCode()
            public override fun hashCode(): Int = this@RealServiceFactoryDriver.hashCode()
            public override fun toString(): String = toFIDString(includeHashCode = false)
        }

        public override fun toString(): String = toFIDString(includeHashCode = false)

        init {
            lce(Lifecycle.Event.OnCreate(this))
        }
    }

    internal companion object {

        internal val TIMEOUT_START_SERVICE: Duration = 1_000.milliseconds

        @JvmSynthetic
        @Suppress("RemoveRedundantQualifierName")
        internal fun of(
            generator: TorConfigGenerator,
            networkObserver: NetworkObserver,
            requiredTorEvents: Set<TorEvent>,
            observersTorEvent: Set<TorEvent.Observer>,
            observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
        ): TorRuntime = generator.environment.serviceFactoryLoader()?.let { loader ->
            var n: RuntimeEvent.Notifier? = null

            val initializer = TorRuntime.ServiceFactory.Initializer.of(create = { startService ->
                RealServiceFactoryDriver(
                    generator,
                    networkObserver,
                    requiredTorEvents,
                    observersTorEvent,
                    observersRuntimeEvent,
                    startService,
                ).also { n = it }
            })

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
            generator.environment.defaultExecutor(),
            observersRuntimeEvent,
        )
    }
}
