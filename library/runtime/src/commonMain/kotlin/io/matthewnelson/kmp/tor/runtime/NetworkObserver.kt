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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * A Hook for [TorRuntime] that controls [TorConfig.DisableNetwork]
 * toggling when device connectivity is lost/gained.
 *
 * Multiple instances of [TorRuntime] can [subscribe] to a single
 * [NetworkObserver].
 *
 * @see [notify]
 * @see [noOp]
 * @see [TorRuntime.Builder.networkObserver]
 * */
public abstract class NetworkObserver {

    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()
    private val observers = LinkedHashSet<OnEvent<Connectivity>>(1, 1.0F)

    @JvmSynthetic
    internal open fun subscribe(observer: OnEvent<Connectivity>) {
        @OptIn(InternalKmpTorApi::class)
        val initialAttach = synchronized(lock) {
            val wasEmpty = observers.isEmpty()
            observers.add(observer) && wasEmpty
        }

        if (!initialAttach) return

        try {
            onObserversNotEmpty()
        } catch (_: Throwable) {}
    }

    @JvmSynthetic
    internal open fun unsubscribe(observer: OnEvent<Connectivity>) {
        @OptIn(InternalKmpTorApi::class)
        val lastRemoved = synchronized(lock) {
            observers.remove(observer) && observers.isEmpty()
        }

        if (!lastRemoved) return

        try {
            onObserversEmpty()
        } catch (_: Throwable) {}
    }

    /**
     * Optional override for being notified when [observers]
     * goes from:
     *
     *     empty -> not empty
     * */
    protected open fun onObserversNotEmpty() {}

    /**
     * Optional override for being notified when [observers]
     * goes from:
     *
     *     not empty -> empty
     * */
    protected open fun onObserversEmpty() {}

    public abstract fun isNetworkConnected(): Boolean

    /**
     * Notifies all registered [observers] of a change in
     * [Connectivity]
     * */
    protected fun notify(connectivity: Connectivity) {
        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            observers.toSet()
        }.forEach { it(connectivity) }
    }

    public enum class Connectivity {
        Connected,
        Disconnected,
    }

    public companion object {

        /**
         * A non-operational static instance [NetworkObserver] where
         * [isNetworkConnected] always returns `true`
         * */
        @JvmStatic
        public fun noOp(): NetworkObserver = NOOP
    }

    private data object NOOP: NetworkObserver() {
        internal override fun subscribe(observer: OnEvent<Connectivity>) {}
        internal override fun unsubscribe(observer: OnEvent<Connectivity>) {}
        public override fun isNetworkConnected(): Boolean = true
        public override fun toString(): String = "NetworkObserver.NOOP"
    }
}
