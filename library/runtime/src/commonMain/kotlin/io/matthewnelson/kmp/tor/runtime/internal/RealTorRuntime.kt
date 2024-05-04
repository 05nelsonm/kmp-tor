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
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob.Companion.toImmediateErrorJob
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TempTorCmdQueue
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.milliseconds

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
        + handler
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
        // TODO: Need to intercept cmd (e.g. if SetEvents)
        //  See Issue #371
        //  interceptors = setOf(),
        defaultExecutor = OnEvent.Executor.Immediate,
        debugger = ItBlock { log ->
            if (!debug) return@ItBlock

            // Debug logs are all formatted as TorCtrl@<hashCode> <log>
            val i = log.indexOf('@')
            val formatted = if (i == -1) {
                log
            } else {
                log.substring(0, i) + "[fid=" + fidEllipses + ']' + log.substring(i)
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
        var errorMsg = "$this.isDestroyed[true]"

        if (destroyed) {
            return onFailure.toImmediateErrorJob(
                action.name,
                IllegalStateException(errorMsg),
                handler,
            )
        }

        val job = synchronized(enqueueLock) {
            if (destroyed) return@synchronized null

            when (action) {
                Action.StartDaemon -> {
                    val start = ActionJob.StartJob(onSuccess, onFailure, handler)
                    if (_cmdQueue == null) {
                        _cmdQueue = factory.tempQueue()
                    }
                    start
                }
                Action.StopDaemon -> {
                    val stop = ActionJob.StopJob(onSuccess, onFailure, handler)
                    // TODO: Handle cmdQueue
                    stop
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
            }?.also { actionStack.push(it) }
        }

        if (job != null) actionProcessor.start()

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
        var errorMsg = "$this.isDestroyed[true]"

        if (destroyed) {
            return onFailure.toImmediateErrorJob(
                cmd.keyword,
                IllegalStateException(errorMsg),
                handler,
            )
        }

        val job = synchronized(enqueueLock) {
            if (destroyed) return@synchronized null

            errorMsg = "Tor is not started"
            _cmdQueue?.enqueue(cmd, onFailure, onSuccess)
        }

        return job ?: onFailure.toImmediateErrorJob(
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

    private inner class ActionProcessor {

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
            NOTIFIER.d(this@RealTorRuntime, "Processor Started")

            while (isActive) {
                val job = synchronized(processorLock) {
                    val next = popNextOrNull()

                    if (next == null) _processorJob = null
                    next
                }

                if (job == null) break

                EXECUTE.notifyObservers(job)

                try {
                    when (job) {
                        is ActionJob.RestartJob -> job.execute()
                        is ActionJob.StartJob -> job.execute()
                        is ActionJob.StopJob -> job.execute()
                    }
                } catch (t: Throwable) {
                    job.error(t)
                }

                job.completion()
            }
        }

        private fun popNextOrNull(): ActionJob.Sealed? = synchronized(processorLock) {
            if (destroyed) return@synchronized null

            return synchronized(enqueueLock) pop@ {
                var job: ActionJob.Sealed? = null

                while (!destroyed && actionStack.isNotEmpty()) {
                    // LIFO
                    job = actionStack.pop()

                    try {
                        job.executing()
                        break
                    } catch (_: IllegalStateException) {
                        job = null
                    }
                }

                when (job) {
                    null -> {}
                    is ActionJob.StartJob -> job.applyCompletionHandle()
                    is ActionJob.StopJob -> job.applyCompletionHandle()
                    is ActionJob.RestartJob -> job.applyCompletionHandle()
                }

                job
            }
        }

        // Must be applied within queueLock synchronized lambda
        private fun ActionJob.StartJob.applyCompletionHandle() {
            // TODO
        }

        // Must be applied within queueLock synchronized lambda
        private fun ActionJob.StopJob.applyCompletionHandle() {
            // TODO
        }

        // Must be applied within queueLock synchronized lambda
        private fun ActionJob.RestartJob.applyCompletionHandle() {
            // TODO
        }

        private suspend fun ActionJob.StartJob.execute() {
            // TODO
        }

        private suspend fun ActionJob.StopJob.execute() {
            // TODO
        }

        private suspend fun ActionJob.RestartJob.execute() {
            // TODO
        }
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

            val job = synchronized(lock) {
                _instance
                    ?.enqueue(action, onFailure, onSuccess)
                    ?.let { return@synchronized it }

                if (actionStack.isEmpty()) {
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
                        val start = ActionJob.StartJob(onSuccess, onFailure, handler, isStartService = true)

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
            }

            execute?.invoke()

            return job
        }

        public override fun <Success: Any> enqueue(
            cmd: TorCmd.Unprivileged<Success>,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Success>,
        ): QueuedJob {
            _instance
                ?.enqueue(cmd, onFailure, onSuccess)
                ?.let { return it }

            val job = synchronized(lock) {
                _instance
                    ?.enqueue(cmd, onFailure, onSuccess)
                    ?.let { return@synchronized it }

                // No instance, nor is there an ActionJob
                // in the queue. Start has not been called.
                if (actionStack.isEmpty()) return@synchronized null

                // Waiting to bind. Create a temporary job to transfer
                // to RealTorRuntime when bind is finally called or
                // timeout occurs.
                val queue = _cmdQueue ?: TorCtrl.Factory(handler = handler)
                    .tempQueue()
                    .also { _cmdQueue = it }

                queue.enqueue(cmd, onFailure, onSuccess)
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

            _startServiceJob = CoroutineScope(name + dispatcher + handler).launch {
                var failure: Throwable? = null

                try {
                    startService()
                } catch (t: RuntimeException) {
                    failure = t
                }

                // Wait for 500ms
                var i = 0
                while (failure == null && _instance == null) {
                    delay(100.milliseconds)
                    if (i++ < 5) continue
                    if (_instance != null) break
                    failure = InterruptedException("startService timed out after 500ms")
                }

                // Has been started
                if (failure == null) return@launch

                val cancellations = synchronized(lock) cancel@ {
                    // onBind occurred, do nothing
                    if (actionStack.isEmpty()) return@cancel emptyList()

                    val disposables = ArrayList<Disposable>(actionStack.size + 1)

                    // Cancel all ActionJob
                    while (actionStack.isNotEmpty()) {
                        when (val job = actionStack.pop()) {
                            is ActionJob.RestartJob,
                            is ActionJob.StartJob -> Disposable { job.error(failure) }
                            is ActionJob.StopJob -> Disposable { job.completion() }
                        }.let { disposables.add(it) }
                    }

                    // Cancel all TorCmdJob
                    _cmdQueue?.let { queue ->
                        disposables.add(Disposable { queue.destroy() })
                        _cmdQueue = null
                    }

                    disposables
                }

                if (cancellations.isEmpty()) return@launch

                handler.withSuppression {
                    val context = name.name + " timed out"

                    cancellations.forEach { disposable ->
                        tryCatch(context) { disposable.invoke() }
                    }
                }
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
                        is EXECUTE -> event.observer(tag) { event.notifyObservers(it) }
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
                val execute = ArrayList<Disposable>(3)

                _instance?.let { instance ->
                    execute.add(Disposable {
                        instance.destroy()
                        w(this, "onBind was called before previous instance was destroyed")
                    })
                    _instance = null
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

                actionStack.clear()
                _cmdQueue = null
                _instance = destroyable

                execute.add(Disposable {
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

                execute.add(Disposable {
                    runtime.actionProcessor.start()
                })

                execute to destroyable
            }.let { (execute, destroyable) ->
                execute.forEach { it() }
                destroyable
            }

            public override fun equals(other: Any?): Boolean = other is ServiceCtrlBinder && other.hashCode() == hashCode()
            public override fun hashCode(): Int = this@RealServiceFactoryCtrl.hashCode()
            public override fun toString(): String = toFIDString(includeHashCode = false)
        }

        init {
            lce(Lifecycle.Event.OnCreate(this))
        }
    }
}
