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

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
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

    private val callback: AtomicRef<((Connectivity) -> Unit)?> = atomic(null)

    @JvmSynthetic
    internal fun attach(callback: (Connectivity) -> Unit): Boolean {
        var attached = false
        this.callback.update {
            if (it == null) {
                try {
                    onManagerAttach()
                } catch (_: Exception) {}
                attached = true
                callback
            } else {
                it
            }
        }
        return attached
    }

    @JvmSynthetic
    internal fun detach() {
        callback.update {
            if (it != null) {
                try {
                    onManagerDetach()
                } catch (_: Exception) {}
                null
            } else {
                it
            }
        }
    }

    /**
     * Called lazily from [RealTorManager] upon first [TorManager.start]
     * */
    protected open fun onManagerAttach() {}

    /**
     * Called from [RealTorManager.destroy]
     * */
    protected open fun onManagerDetach() {}

    abstract fun isNetworkConnected(): Boolean

    /**
     * Use this in your implementation to push [Connectivity] changes
     * to [RealTorManager]
     * */
    protected fun dispatchConnectivityChange(connectivity: Connectivity) {
        callback.value?.invoke(connectivity)
    }

    enum class Connectivity {
        Connected,
        Disconnected,
    }
}
