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
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlinx.coroutines.*
import kotlin.jvm.JvmSynthetic

@OptIn(InternalKmpTorApi::class)
internal class RealTorRuntime private constructor(
    private val generator: TorConfigGenerator,
    private val networkObserver: NetworkObserver,
    private val requiredTorEvents: Set<TorEvent>,
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
), TorRuntime {

    protected override val debug: Boolean get() = generator.environment.debug
    public override fun environment(): TorRuntime.Environment = generator.environment
    private val lock = SynchronizedObject()

    private val scope = CoroutineScope(context =
        CoroutineName("TorRuntime@${hashCode()}")
        + SupervisorJob()
        + dispatcher
        + handler
    )

    private val factory = TorCtrl.Factory(
        staticTag = environment().staticTag,
        observers = TorEvent.entries.let { events ->
            val tag = environment().staticTag
            val set = LinkedHashSet<TorEvent.Observer>(events.size)

            events.forEach { event ->
                val observer = when (event) {
                    TorEvent.CONF_CHANGED -> event.observer(tag) { output ->
                        // TODO
                        event.notifyObservers(output)
                    }
                    TorEvent.NOTICE -> event.observer(tag) { output ->
                        // TODO
                        event.notifyObservers(output)
                    }
                    else -> event.observer(tag) { event.notifyObservers(it) }
                }

                set.add(observer)
            }

            set.toImmutableSet()
        },
        defaultExecutor = OnEvent.Executor.Immediate,
        debugger = ItBlock { log ->
            if (!debug) return@ItBlock

            val i = log.indexOf('@')

            val output = if (i == -1) {
                log
            } else {
                log.substring(0, i) +
                "[id=${environment().id}]" +
                log.substring(i)
            }

            LOG.DEBUG.notifyObservers(output)
        },
        handler = handler,
    )

    @Suppress("PrivatePropertyName")
    private val NOTIFIER = object : Notifier {
        override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) {
            event.notifyObservers(output)
        }
    }

    private val connectivity = ConnectivityObserver()

    public final override fun enqueue(
        action: RuntimeAction,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Unit>,
    ): QueuedJob {
        TODO("Not yet implemented")
    }

    @Throws(IllegalStateException::class)
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

    // Only ever invoked from RealServiceFactory
    protected override fun onDestroy(): Boolean {
        if (!scope.isActive) return false

        scope.cancel()
        NOTIFIER.d(this, "Scope Cancelled")

        try {
            networkObserver.unsubscribe(connectivity)
            NOTIFIER.lce(Lifecycle.Event.OnUnsubscribed(connectivity))

            // TODO: Destroy connection
            // TODO: Cancel ActionJobs
        } finally {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))

            // Must come last as RuntimeEvent.ERROR needs to be present
            // to pipe UncaughtException, otherwise it will throw
            super.onDestroy()
        }

        return true
    }

    public override fun toString(): String = "RealTorRuntime[id=${environment().id}]@${hashCode()}"

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
    }

    private inner class ConnectivityObserver: OnEvent<NetworkObserver.Connectivity> {

        override fun invoke(it: NetworkObserver.Connectivity) {
            // TODO
        }

        override fun equals(other: Any?): Boolean = other is ConnectivityObserver && other.hashCode() == hashCode()
        override fun hashCode(): Int = this@RealTorRuntime.hashCode()
        override fun toString(): String = "ConnectivityObserver[id=${environment().id}]@${hashCode()}"
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
    ), TorRuntime.ServiceFactory {

        protected override val debug: Boolean get() = generator.environment.debug
        private val dispatcher by lazy { generator.environment.newRuntimeDispatcher() }
        public override val environment: TorRuntime.Environment get() = generator.environment

        // Pipe all events to observers registered with Factory
        private val observersTorEvent = buildSet {
            val tag = environment.staticTag

            TorEvent.entries.forEach { event ->
                val observer = event.observer(tag) { event.notifyObservers(it) }
                add(observer)
            }
        }.toImmutableSet()

        // Pipe all events to observers registered with Factory
        private val observersRuntimeEvent = buildSet {
            val tag = environment.staticTag

            RuntimeEvent.entries.forEach { event ->
                val observer = when (event) {
                    is ERROR -> event.observer(tag) { event.notifyObservers(it) }
                    is LIFECYCLE -> event.observer(tag) { event.notifyObservers(it) }
                    is LOG -> event.observer(tag) { event.notifyObservers(it) }
                }
                add(observer)
            }
        }.toImmutableSet()

        public override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }

        public override fun newRuntime(
            serviceEvents: Set<TorEvent>,
            serviceObserver: NetworkObserver?,
        ): Pair<TorRuntime, Lifecycle> {
            val lifecycle = Lifecycle.of(handler)

            val runtime = RealTorRuntime(
                generator,
                serviceObserver ?: builderObserver,
                (builderRequiredEvents + serviceEvents).toImmutableSet(),
                dispatcher,
                observersTorEvent,

                // Want to utilize Immediate here as all events will be
                // piped to the observers registered to the ServiceFactory.
                OnEvent.Executor.Immediate,
                observersRuntimeEvent,
            )

            lifecycle.invokeOnCompletion {
                runtime.onDestroy()
            }

            return runtime to lifecycle
        }

        init {
            lce(Lifecycle.Event.OnCreate(this))
        }
    }
}
