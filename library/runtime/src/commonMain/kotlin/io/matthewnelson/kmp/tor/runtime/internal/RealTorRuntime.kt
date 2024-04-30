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
    private val requiredTorEvents: Set<TorEvent>,
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
    protected override val debug: Boolean get() = environment().debug
    public override fun environment(): TorRuntime.Environment = generator.environment
    protected override val isService: Boolean = serviceFactoryHandler != null
    protected override val handler: HandlerWithContext = serviceFactoryHandler ?: super.handler

    @JvmSynthetic
    internal fun handler(): UncaughtException.Handler = handler

    private val lock = SynchronizedObject()

    private val scope = CoroutineScope(context =
        CoroutineName(toString())
        + SupervisorJob()
        + dispatcher
        + handler
    )

    @Suppress("PrivatePropertyName")
    private val NOTIFIER = object : Notifier {
        public override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) {
            event.notifyObservers(output)
        }
    }

    private val factory = TorCtrl.Factory(
        staticTag = environment().staticTag(),
        observers = TorEvent.entries.let { events ->
            val tag = environment().staticTag()
            events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
                when (event) {
                    TorEvent.CONF_CHANGED -> ConfChangedObserver()
                    TorEvent.NOTICE -> NoticeObserver()
                    else -> event.observer(tag) { event.notifyObservers(it) }
                }
            }.toImmutableSet()
        },
        defaultExecutor = OnEvent.Executor.Immediate,
        debugger = ItBlock { log ->
            if (!debug) return@ItBlock

            val i = log.indexOf('@')
            val output = if (i == -1) {
                log
            } else {
                log.substring(0, i) + "[fid=" + fidEllipses + ']' + log.substring(i)
            }

            LOG.DEBUG.notifyObservers(output)
        },
        handler = handler,
    )

    private val connectivity = ConnectivityObserver()

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

    init {
        NOTIFIER.lce(Lifecycle.Event.OnCreate(this))
        networkObserver.subscribe(connectivity)
        NOTIFIER.lce(Lifecycle.Event.OnSubscribed(connectivity))
    }

    public override fun toString(): String = toFIDString(includeHashCode = isService)

    protected override fun onDestroy(): Boolean {
        if (!isService) {
            LOG.WARN.notifyObservers("onDestroy called but isService is false")
            return false
        }

        if (!super.onDestroy()) return false
        scope.cancel()
        NOTIFIER.d(this, "Scope Cancelled")
        networkObserver.unsubscribe(connectivity)
        NOTIFIER.lce(Lifecycle.Event.OnUnsubscribed(connectivity))
        NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
        return true
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

            val factory = loader.load(TorRuntime.ServiceFactory.Initializer.of(ctrl))

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

    private inner class ConnectivityObserver: OnEvent<NetworkObserver.Connectivity>, FileID by this {

        public override fun invoke(it: NetworkObserver.Connectivity) {
            // TODO
        }

        public override fun equals(other: Any?): Boolean = other is ConnectivityObserver && other.hashCode() == hashCode()
        public override fun hashCode(): Int = this@RealTorRuntime.hashCode()
        public override fun toString(): String = toFIDString(includeHashCode = isService)
    }

    private inner class ConfChangedObserver: TorEvent.Observer(
        event = TorEvent.CONF_CHANGED,
        tag = environment().staticTag(),
        executor = null,
        onEvent = OnEvent.noOp(),
    ) {
        protected override fun notify(data: String) {
            // TODO: parse data

            event.notifyObservers(data)
        }
    }

    private inner class NoticeObserver: TorEvent.Observer(
        event = TorEvent.NOTICE,
        tag = environment().staticTag(),
        executor = null,
        onEvent = OnEvent.noOp(),
    ) {
        protected override fun notify(data: String) {
            // TODO: parse data

            event.notifyObservers(data)
        }
    }

    internal class ServiceCtrl private constructor(
        private val generator: TorConfigGenerator,
        private val builderObserver: NetworkObserver,
        private val builderRequiredEvents: Set<TorEvent>,
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

        protected override val debug: Boolean get() = environment().debug
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
            private val observersTorEvent = TorEvent.entries.let { events ->
                val tag = environment().staticTag()
                events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
                    event.observer(tag) { event.notifyObservers(it) }
                }.toImmutableSet()
            }

            // Pipe all events to observers registered with Factory
            private val observersRuntimeEvent = RuntimeEvent.entries.let { events ->
                val tag = environment().staticTag()
                events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
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
                    (builderRequiredEvents + serviceEvents).toImmutableSet(),
                    handler,
                    dispatcher,
                    (observersTorEvent + serviceObserversTorEvent).toImmutableSet(),

                    // Want to utilize Immediate here as all events will be
                    // piped to the observers registered to the ServiceFactory.
                    OnEvent.Executor.Immediate,
                    (observersRuntimeEvent + serviceObserversRuntimeEvent).toImmutableSet(),
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

            override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }

            override fun equals(other: Any?): Boolean = other is ServiceBinder && other.hashCode() == hashCode()
            override fun hashCode(): Int = this@ServiceCtrl.hashCode()
            override fun toString(): String = toFIDString(includeHashCode = false)
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
