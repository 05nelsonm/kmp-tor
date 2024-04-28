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
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlinx.coroutines.*
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

@OptIn(InternalKmpTorApi::class)
internal class RealTorRuntime private constructor(
    private val generator: TorConfigGenerator,
    private val networkObserver: NetworkObserver,
    private val requiredTorEvents: Set<TorEvent>,
    serviceFactoryHandler: HandlerWithContext?,
    serviceFactoryDebugger: ItBlock<String>?,
    dispatcher: CoroutineDispatcher,
    observersTorEvent: Set<TorEvent.Observer>,
    defaultExecutor: OnEvent.Executor,
    @Suppress("RemoveRedundantQualifierName")
    observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
): AbstractRuntimeEventProcessor(
    generator.environment.staticTag,
    observersRuntimeEvent,
    defaultExecutor,
    observersTorEvent,
), FileID by generator,
   TorRuntime
{

    protected override val debug: Boolean get() = environment().debug
    public override fun environment(): TorRuntime.Environment = generator.environment
    protected override val handler: HandlerWithContext = serviceFactoryHandler ?: super.handler

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
        staticTag = environment().staticTag,
        observers = TorEvent.entries.let { events ->
            val tag = environment().staticTag
            events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
                when (event) {
                    TorEvent.CONF_CHANGED -> ConfChangedObserver()
                    TorEvent.NOTICE -> NoticeObserver()
                    else -> event.observer(tag) { event.notifyObservers(it) }
                }
            }.toImmutableSet()
        },
        defaultExecutor = OnEvent.Executor.Immediate,
        debugger = serviceFactoryDebugger ?: NOTIFIER.newCtrlDebugger(environment()),
        handler = handler,
    )

    private val connectivity = ConnectivityObserver()

    public final override fun enqueue(
        action: RuntimeAction,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Unit>,
    ): QueuedJob {
        TODO("Not yet implemented")
    }

    public final override fun <Response : Any> enqueue(
        cmd: TorCmd.Unprivileged<Response>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Response>
    ): QueuedJob {
        TODO("Not yet implemented")
    }

    init {
        NOTIFIER.lce(Lifecycle.Event.OnCreate(this))
        networkObserver.subscribe(connectivity)
        NOTIFIER.lce(Lifecycle.Event.OnSubscribed(connectivity))
    }

    public override fun toString(): String = toFIDString()

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
        ): TorRuntime = newServiceRuntimeOrNull(factory = {
            RealServiceFactory(
                generator,
                networkObserver,
                requiredTorEvents,
                observersTorEvent,
                defaultExecutor,
                observersRuntimeEvent,
            )
        }) ?: RealTorRuntime(
            generator,
            networkObserver,
            requiredTorEvents,
            null,
            null,
            generator.environment.newRuntimeDispatcher(),
            observersTorEvent,
            defaultExecutor,
            observersRuntimeEvent,
        )

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun TorRuntime.ServiceFactory.checkInstance() {
            check(this is RealServiceFactory) { "instance must be RealServiceFactory" }
        }

        @JvmStatic
        private fun Notifier.newCtrlDebugger(
            environment: TorRuntime.Environment,
        ) = ItBlock<String> { log ->
            if (!environment.debug) return@ItBlock

            val i = log.indexOf('@')
            val output = if (i == -1) {
                log
            } else {
                log.substring(0, i) + "[fid=" + environment.fid + ']' + log.substring(i)
            }

            notify(LOG.DEBUG, output)
        }
    }

    private inner class ConnectivityObserver: OnEvent<NetworkObserver.Connectivity>, FileID by this {

        public override fun invoke(it: NetworkObserver.Connectivity) {
            // TODO
        }

        public override fun equals(other: Any?): Boolean = other is ConnectivityObserver && other.hashCode() == hashCode()
        public override fun hashCode(): Int = this@RealTorRuntime.hashCode()
        public override fun toString(): String = toFIDString()
    }

    private inner class ConfChangedObserver: TorEvent.Observer(
        event = TorEvent.CONF_CHANGED,
        tag = environment().staticTag,
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
        tag = environment().staticTag,
        executor = null,
        onEvent = OnEvent.noOp(),
    ) {
        protected override fun notify(data: String) {
            // TODO: parse data

            event.notifyObservers(data)
        }
    }

    private class RealServiceFactory(
        private val generator: TorConfigGenerator,
        private val builderObserver: NetworkObserver,
        private val builderRequiredEvents: Set<TorEvent>,
        observersTorEvent: Set<TorEvent.Observer>,
        defaultExecutor: OnEvent.Executor,
        @Suppress("RemoveRedundantQualifierName")
        observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
    ): AbstractRuntimeEventProcessor(
        generator.environment.staticTag,
        observersRuntimeEvent,
        defaultExecutor,
        observersTorEvent
    ), TorRuntime.ServiceFactory,
       FileID by generator
    {

        protected override val debug: Boolean get() = environment().debug
        private val dispatcher by lazy { environment().newRuntimeDispatcher() }
        public override fun environment(): TorRuntime.Environment = generator.environment

        // Pipe all events to observers registered with Factory
        private val observersTorEvent = TorEvent.entries.let { events ->
            val tag = environment().staticTag
            events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
                event.observer(tag) { event.notifyObservers(it) }
            }.toImmutableSet()
        }

        // Pipe all events to observers registered with Factory
        private val observersRuntimeEvent = RuntimeEvent.entries.let { events ->
            val tag = environment().staticTag
            events.mapTo(LinkedHashSet(events.size, 1.0f)) { event ->
                when (event) {
                    is ERROR -> event.observer(tag) { event.notifyObservers(it) }
                    is LIFECYCLE -> event.observer(tag) { event.notifyObservers(it) }
                    is LOG -> event.observer(tag) { event.notifyObservers(it) }
                }
            }.toImmutableSet()
        }

        public override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }

        public override fun newRuntime(
            serviceEvents: Set<TorEvent>,
            serviceObserver: NetworkObserver?,
        ): Lifecycle.DestroyableTorRuntime {
            val lifecycle = Lifecycle.of(handler)

            val runtime = RealTorRuntime(
                generator,
                serviceObserver ?: builderObserver,
                (builderRequiredEvents + serviceEvents).toImmutableSet(),
                handler,
                newCtrlDebugger(environment()),
                dispatcher,
                observersTorEvent,

                // Want to utilize Immediate here as all events will be
                // piped to the observers registered to the ServiceFactory.
                OnEvent.Executor.Immediate,
                observersRuntimeEvent,
            )

            lifecycle.invokeOnCompletion {
                runtime.onDestroy()
                runtime.scope.cancel()
                d(runtime, "Scope Cancelled")
                runtime.networkObserver.unsubscribe(runtime.connectivity)
                lce(Lifecycle.Event.OnUnsubscribed(runtime.connectivity))
                lce(Lifecycle.Event.OnDestroy(runtime))
            }

            return Lifecycle.DestroyableTorRuntime.of(lifecycle, runtime)
        }

        init {
            lce(Lifecycle.Event.OnCreate(this))
        }
    }
}
