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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.TorState
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmStatic

@OptIn(InternalKmpTorApi::class)
internal abstract class TorStateManager internal constructor(fid: FileID?) {

    @Volatile
    private var _state: TorState = TorState.of(
        daemon = TorState.Daemon.Off,
        network = TorState.Network.Disabled,
        fid = fid,
    )
    internal val state: TorState get() = _state

    private val lock = SynchronizedObject()

    protected abstract fun notify(old: TorState, new: TorState)

    internal fun update(daemon: TorState.Daemon? = null, network: TorState.Network? = null) {
        if (daemon == null && network == null) return

        val notify = synchronized(lock) {
            val old = _state
            val new = old.copy(daemon ?: old.daemon, network ?: old.network)
            val n = Notify.of(old, new) ?: return@synchronized null
            _state = n.new
            n
        } ?: return

        notify(notify.old, notify.new)
    }

    private class Notify private constructor(val old: TorState, val new: TorState) {

        companion object {

            @JvmStatic
            fun of(old: TorState, new: TorState): Notify? {
                // No changes
                if (old == new) return null

                // on -> off
                // on -> stopping
                // on -> on
                if (old.isOn && new.isStarting) return null

                // off -> starting
                // off -> off
                if (old.isOff && new.isOn) return null
                if (old.isOff && new.isStopping) return null

                // stopping -> off
                // stopping -> starting
                // stopping -> stopping
                if (old.isStopping && new.isOn) return null

                // starting -> on
                // starting -> off
                // starting -> stopping
                // starting -> starting
                return Notify(old, new)
            }
        }
    }
}
