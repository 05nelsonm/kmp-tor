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
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmSynthetic

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
): AbstractRuntimeEventProcessor(
    generator.environment.staticTag(),
    observersRuntimeEvent,
    defaultExecutor,
    observersTorEvent,
), FileID by generator,
   TorRuntime
{

    private var lifecycle: Destroyable? = null
    protected override val debug: Boolean get() = generator.environment.debug
    protected override val isService: Boolean = serviceFactoryHandler != null
    protected override val handler: HandlerWithContext = serviceFactoryHandler ?: super.handler
    private val requiredTorEvents = requiredTorEvents.toImmutableSet()
    private val lock = SynchronizedObject()

    private val scope = CoroutineScope(context =
        CoroutineName(toString())
        + SupervisorJob()
        + dispatcher
        + handler
    )

    private val connectivity = ConnectivityObserver()

    @Suppress("PrivatePropertyName")
    private val NOTIFIER = object : Notifier {
        public override fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data) {
            event.notifyObservers(data)
        }
    }

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

    @JvmSynthetic
    internal fun handler(): UncaughtException.Handler = handler

    public override fun environment(): TorRuntime.Environment = generator.environment

    // TODO
    public override fun enqueue(
        action: Action,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Unit>,
    ): QueuedJob = onFailure.toImmediateErrorJob(action.name, NotImplementedError(), handler)

    // TODO
    public override fun <Response : Any> enqueue(
        cmd: TorCmd.Unprivileged<Response>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Response>
    ): QueuedJob = onFailure.toImmediateErrorJob(cmd.keyword, NotImplementedError(), handler)

    protected override fun onDestroy(): Boolean {
        if (!isService) {
            LOG.WARN.notifyObservers("onDestroy called but isService is false")
            return false
        }

        if (!super.onDestroy()) return false
        scope.cancel()
        NOTIFIER.d(this, "Scope Cancelled")
        if (networkObserver != NetworkObserver.noOp()) {
            networkObserver.unsubscribe(connectivity)
            NOTIFIER.lce(Lifecycle.Event.OnUnsubscribed(connectivity))
        }
        NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
        return true
    }

    public override fun toString(): String = toFIDString(includeHashCode = isService)

    init {
        NOTIFIER.lce(Lifecycle.Event.OnCreate(this))

        if (networkObserver != NetworkObserver.noOp()) {
            networkObserver.subscribe(connectivity)
            NOTIFIER.lce(Lifecycle.Event.OnSubscribed(connectivity))
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
            val ctrl = ServiceCtrl.of(
                generator,
                networkObserver,
                requiredTorEvents,
                observersTorEvent,
                defaultExecutor,
                observersRuntimeEvent,
            )

            val initializer = TorRuntime.ServiceFactory.Initializer.of(ctrl)
            val factory = loader.load(initializer)

            ctrl.binder().lce(Lifecycle.Event.OnCreate(factory))

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

    internal class ServiceCtrl private constructor(
        private val generator: TorConfigGenerator,
        private val builderObserver: NetworkObserver,
        builderRequiredEvents: Set<TorEvent>,
        observersTorEvent: Set<TorEvent.Observer>,
        defaultExecutor: OnEvent.Executor,
        @Suppress("RemoveRedundantQualifierName")
        observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
    ): AbstractRuntimeEventProcessor(
        generator.environment.staticTag(),
        observersRuntimeEvent,
        defaultExecutor,
        observersTorEvent
    ), FileID by generator,
       TorCmd.Unprivileged.Processor
    {

        private val builderRequiredEvents = builderRequiredEvents.toImmutableSet()
        protected override val debug: Boolean get() = generator.environment.debug

        // Using lazy in case TorRuntime.ServiceFactory.Loader throws
        // upon initialization of a bad implementation, we do not want
        // to create an unused dispatcher for Jvm & Native
        private val dispatcher by lazy { environment().newRuntimeDispatcher() }

        private val _binder = ServiceBinder()

        @JvmSynthetic
        internal fun binder(): TorRuntime.ServiceFactory.Binder = _binder
        @JvmSynthetic
        internal fun environment(): TorRuntime.Environment = generator.environment

        // TODO
        public override fun <Success : Any> enqueue(
            cmd: TorCmd.Unprivileged<Success>,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Success>,
        ): QueuedJob = onFailure.toImmediateErrorJob(cmd.keyword, NotImplementedError(), handler)

        // TODO
        @JvmSynthetic
        internal fun enqueue(
            startService: () -> Unit,
            action: Action,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Unit>,
        ): QueuedJob = onFailure.toImmediateErrorJob(action.name, NotImplementedError(), handler)

        private inner class ServiceBinder: TorRuntime.ServiceFactory.Binder, FileID by this {

            @Volatile
            private var _instance: Lifecycle.DestroyableTorRuntime? = null
            val instance: Lifecycle.DestroyableTorRuntime? get() = _instance
            private val bLock = SynchronizedObject()

            // Pipe all events to observers registered with Factory
            private val observersTorEvent = TorEvent.entries().let { events ->
                val tag = generator.environment.staticTag()
                events.mapTo(LinkedHashSet(events.size + 1, 1.0f)) { event ->
                    event.observer(tag) { event.notifyObservers(it) }
                }.toImmutableSet()
            }

            // Pipe all events to observers registered with Factory
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
            ): Lifecycle.DestroyableTorRuntime = synchronized(bLock) {

                // invokeOnCompletion handler should set instance to null,
                // so this means the instance was never destroyed before
                // calling onBind again
                if (_instance?.destroy() != null) {
                    w(this, "onBind was called before previous instance was destroyed")
                }

                lce(Lifecycle.Event.OnBind(this))

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
                runtime.lifecycle = destroyable
                _instance = destroyable
                lce(Lifecycle.Event.OnCreate(destroyable))

                destroyable.invokeOnCompletion {
                    _instance = null
                    runtime.onDestroy()
                }
                destroyable.invokeOnCompletion {
                    lce(Lifecycle.Event.OnDestroy(destroyable))
                }
                destroyable.invokeOnCompletion {
                    lce(Lifecycle.Event.OnUnbind(this))
                }

                return destroyable
            }

            public override fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data) { event.notifyObservers(data) }

            public override fun equals(other: Any?): Boolean = other is ServiceBinder && other.hashCode() == hashCode()
            public override fun hashCode(): Int = this@ServiceCtrl.hashCode()
            public override fun toString(): String = toFIDString(includeHashCode = false)
        }

        init {
            binder().lce(Lifecycle.Event.OnCreate(this))
        }

        internal companion object {

            @JvmSynthetic
            internal fun of(
                generator: TorConfigGenerator,
                builderObserver: NetworkObserver,
                builderRequiredEvents: Set<TorEvent>,
                observersTorEvent: Set<TorEvent.Observer>,
                defaultExecutor: OnEvent.Executor,
                @Suppress("RemoveRedundantQualifierName")
                observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
            ): ServiceCtrl = ServiceCtrl(
                generator,
                builderObserver,
                builderRequiredEvents,
                observersTorEvent,
                defaultExecutor,
                observersRuntimeEvent,
            )
        }
    }
}
