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
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlin.apply
import kotlin.jvm.JvmSynthetic

@OptIn(InternalKmpTorApi::class)
internal class RealTorRuntime private constructor(
    private val environment: TorRuntime.Environment,
    private val lifecycleHook: Job,
    private val networkObserver: NetworkObserver,
    private val requiredTorEvents: Set<TorEvent>,
    staticTorEventObservers: Set<TorEvent.Observer>,
    defaultExecutor: OnEvent.Executor,
    staticRuntimeEventObservers: Set<RuntimeEvent.Observer<*>>,
): AbstractRuntimeEventProcessor(
    environment.staticObserverTag,
    staticRuntimeEventObservers,
    defaultExecutor,
    staticTorEventObservers,
), TorRuntime {

    protected override val debug: Boolean get() = environment.debug

    override fun enqueue(
        action: RuntimeAction,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Unit>,
    ): QueuedJob {
        TODO("Not yet implemented")
    }

    override fun <Response : Any> enqueue(
        cmd: TorCmd.Unprivileged<Response>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Response>
    ): QueuedJob {
        TODO("Not yet implemented")
    }

    override fun environment(): TorRuntime.Environment = environment

    internal companion object {

        @JvmSynthetic
        internal fun of(
            environment: TorRuntime.Environment,
            networkObserver: NetworkObserver,
            omitGeoIPFileSettings: Boolean,
            config: Set<ConfigBuilderCallback>,
            requiredTorEvents: Set<TorEvent>,
            staticTorEventObservers: Set<TorEvent.Observer>,
            defaultExecutor: OnEvent.Executor,
            staticRuntimeEventObservers: Set<RuntimeEvent.Observer<*>>,
        ): TorRuntime {

            val runtime = TorRuntime.ServiceFactory.serviceRuntimeOrNull {
                ServiceFactory(
                    environment,
                    requiredTorEvents,
                    staticTorEventObservers,
                    defaultExecutor,
                    staticRuntimeEventObservers,
                )
            }

            if (runtime != null) return runtime

            return RealTorRuntime(
                environment,
                NonCancellable,
                networkObserver,
                requiredTorEvents,
                staticTorEventObservers,
                defaultExecutor,
                staticRuntimeEventObservers,
            )
        }

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun TorRuntime.ServiceFactory.checkInstance() {
            check(this is ServiceFactory) {
                "factory instance must be RealTorRuntime.ServiceFactory"
            }
        }
    }

    private class ServiceFactory(
        override val environment: TorRuntime.Environment,
        requiredTorEvents: Set<TorEvent>,
        staticTorEventObservers: Set<TorEvent.Observer>,
        defaultExecutor: OnEvent.Executor,
        staticRuntimeEventObservers: Set<RuntimeEvent.Observer<*>>,
    ): AbstractRuntimeEventProcessor(
        environment.staticObserverTag,
        staticRuntimeEventObservers,
        defaultExecutor,
        staticTorEventObservers
    ), TorRuntime.ServiceFactory {

        protected override val debug: Boolean get() = environment.debug

        private val requiredTorEvents = if (!requiredTorEvents.contains(TorEvent.BW)) {
            requiredTorEvents.toMutableSet().apply {
                add(TorEvent.BW)
            }.toImmutableSet()
        } else {
            requiredTorEvents
        }

        private val staticTorEventObservers = buildSet {
            val tag = environment.staticObserverTag

            TorEvent.entries.forEach { event ->
                val observer = event.observer(tag) { event.notifyObservers(it) }
                add(observer)
            }
        }.toImmutableSet()

        private val staticRuntimeEventObservers = buildSet {
            val tag = environment.staticObserverTag

            RuntimeEvent.entries.forEach { event ->
                val observer = when (event) {
                    is RuntimeEvent.LOG -> event.observer(tag) { event.notifyObservers(it) }
                    is RuntimeEvent.ERROR -> event.observer(tag) { event.notifyObservers(it) }
                }
                add(observer)
            }
        }.toImmutableSet()

        override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }

        override fun create(
            lifecycleHook: Job,
            observer: NetworkObserver?,
        ): TorRuntime = RealTorRuntime(
            environment,
            lifecycleHook,
            observer ?: NetworkObserver.NOOP,
            requiredTorEvents,
            staticTorEventObservers,
            OnEvent.Executor.Immediate,
            staticRuntimeEventObservers,
        )
    }
}
