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
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlin.jvm.JvmSynthetic

@OptIn(InternalKmpTorApi::class)
internal class RealTorRuntime private constructor(
    private val generator: TorConfigGenerator,
    private val networkObserver: NetworkObserver,
    private val requiredTorEvents: Set<TorEvent>,
    observersTorEvent: Set<TorEvent.Observer>,
    defaultExecutor: OnEvent.Executor,
    observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
): AbstractRuntimeEventProcessor(
    generator.environment.staticTag,
    observersRuntimeEvent,
    defaultExecutor,
    observersTorEvent,
), TorRuntime {

    protected override val debug: Boolean get() = generator.environment.debug

    public override fun environment(): TorRuntime.Environment = generator.environment

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
        RuntimeEvent.LIFECYCLE.notifyObservers(Lifecycle.Event.OnCreate(this))
    }

    protected override fun onDestroy(): Boolean {
        val wasDestroyed = super.onDestroy()
        return wasDestroyed
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            generator: TorConfigGenerator,
            networkObserver: NetworkObserver,
            requiredTorEvents: Set<TorEvent>,
            observersTorEvent: Set<TorEvent.Observer>,
            defaultExecutor: OnEvent.Executor,
            observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
        ): TorRuntime {

            val runtime = TorRuntime.ServiceFactory.serviceRuntimeOrNull(block = {
                RealServiceFactory(
                    generator,
                    networkObserver,
                    requiredTorEvents,
                    observersTorEvent,
                    defaultExecutor,
                    observersRuntimeEvent,
                )
            })

            if (runtime != null) return runtime

            return RealTorRuntime(
                generator,
                networkObserver,
                requiredTorEvents,
                observersTorEvent,
                defaultExecutor,
                observersRuntimeEvent,
            )
        }

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun TorRuntime.ServiceFactory.checkInstance() {
            check(this is RealServiceFactory) {
                "factory instance must be RealTorRuntime.ServiceFactory"
            }
        }
    }

    private class RealServiceFactory(
        private val generator: TorConfigGenerator,
        private val builderObserver: NetworkObserver,
        private val builderRequiredEvents: Set<TorEvent>,
        observersTorEvent: Set<TorEvent.Observer>,
        defaultExecutor: OnEvent.Executor,
        observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
    ): AbstractRuntimeEventProcessor(
        generator.environment.staticTag,
        observersRuntimeEvent,
        defaultExecutor,
        observersTorEvent
    ), TorRuntime.ServiceFactory {

        public override val environment: TorRuntime.Environment get() = generator.environment
        protected override val debug: Boolean get() = environment.debug

        // Pipe all events to observers registered with Factory
        private val observersTorEvent = buildSet {
            val static = environment.staticTag

            TorEvent.entries.forEach { event ->
                val observer = event.observer(static) { event.notifyObservers(it) }
                add(observer)
            }
        }.toImmutableSet()

        // Pipe all events to observers registered with Factory
        private val observersRuntimeEvent = buildSet {
            val static = environment.staticTag

            RuntimeEvent.entries.forEach { event ->
                val observer = when (event) {
                    is RuntimeEvent.ERROR -> event.observer(static) { event.notifyObservers(it) }
                    is RuntimeEvent.LIFECYCLE -> event.observer(static) { event.notifyObservers(it) }
                    is RuntimeEvent.LOG -> event.observer(static) { event.notifyObservers(it) }
                }
                add(observer)
            }
        }.toImmutableSet()

        public override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }

        public override fun newLifecycle(): Lifecycle = Lifecycle.of(handler)

        public override fun newRuntime(
            lifecycle: Lifecycle,
            requiredEvents: Set<TorEvent>,
            observer: NetworkObserver?,
        ): TorRuntime = RealTorRuntime(
            generator,
            observer ?: builderObserver,
            (builderRequiredEvents + requiredEvents).toImmutableSet(),
            observersTorEvent,

            // Want to utilize Immediate here as all events will be
            // piped to the observers registered to the ServiceFactory.
            OnEvent.Executor.Immediate,
            observersRuntimeEvent,
        ).also { runtime ->
            lifecycle.invokeOnCompletion {
                runtime.onDestroy()
                RuntimeEvent.LIFECYCLE.notifyObservers(Lifecycle.Event.OnDestroy(runtime))
            }
        }

        init {
            RuntimeEvent.LIFECYCLE.notifyObservers(Lifecycle.Event.OnCreate(this))
        }
    }
}
