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
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

public abstract class NetworkObserver {

    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()
    private val observers = mutableSetOf<(Connectivity) -> Unit>()

    @JvmSynthetic
    internal open fun subscribe(observer: (Connectivity) -> Unit) {
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
    internal open fun unsubscribe(observer: (Connectivity) -> Unit) {
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

    protected fun dispatch(connectivity: Connectivity) {
        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            observers.forEach { it(connectivity) }
        }
    }

    public enum class Connectivity {
        Connected,
        Disconnected,
    }

    public companion object {

        @JvmField
        public val NOOP: NetworkObserver = object : NetworkObserver() {
            override fun subscribe(observer: (Connectivity) -> Unit) {}
            override fun unsubscribe(observer: (Connectivity) -> Unit) {}
            override fun isNetworkConnected(): Boolean = true
        }
    }
}
