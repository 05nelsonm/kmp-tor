/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.manager.internal.util.synchronous.SynchronizedMutableMap
import kotlin.jvm.JvmSynthetic

/**
 * Observe device's Network connectivity such that, when lost, Tor's config
 * setting `DisableNetwork` is automatically set to `true`, and upon regaining of
 * connectivity, to `false`.
 *
 * [RealTorManager] will [attach] a callback upon creation, and then [detach]
 * when [RealTorManager.destroy] is called (if ever).
 * */
abstract class NetworkObserver {

    private val callbacks = SynchronizedMutableMap<(Connectivity) -> Unit>()

    @JvmSynthetic
    internal fun attach(instanceId: String, callback: (Connectivity) -> Unit): Boolean {
        return callbacks.withLock {
            if (containsKey(instanceId)) return@withLock false

            val wasEmpty = isEmpty()
            this[instanceId] = callback

            if (wasEmpty) {
                // Only call when first attachment is had
                try {
                    onManagerAttach()
                } catch (_: Exception) {}
            }

            true
        }
    }

    @JvmSynthetic
    internal fun detach(instanceId: String) {
        callbacks.withLock {
            if (remove(instanceId) != null && isEmpty()) {
                try {
                    onManagerDetach()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Called lazily from [RealTorManager] upon first [TorManager.start]
     *
     * Will be called when first [RealTorManager] instance attaches itself.
     * */
    protected open fun onManagerAttach() {}

    /**
     * Called from [RealTorManager.destroy]
     *
     * Will be called when last [RealTorManager] instance detaches itself.
     * */
    protected open fun onManagerDetach() {}

    abstract fun isNetworkConnected(): Boolean

    /**
     * Use this in your implementation to push [Connectivity] changes
     * to [RealTorManager]
     * */
    protected fun dispatchConnectivityChange(connectivity: Connectivity) {
        callbacks.withLock {
            for (callback in values) {
                callback.invoke(connectivity)
            }
        }
    }

    enum class Connectivity {
        Connected,
        Disconnected,
    }
}
