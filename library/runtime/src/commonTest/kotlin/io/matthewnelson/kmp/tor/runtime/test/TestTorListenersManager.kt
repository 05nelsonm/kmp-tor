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
package io.matthewnelson.kmp.tor.runtime.test

import io.matthewnelson.kmp.tor.runtime.TorListeners
import io.matthewnelson.kmp.tor.runtime.TorState

internal class TestTorListenersManager: TorListeners.Manager {

    val states = mutableListOf<Pair<TorState.Daemon?, TorState.Network?>>()
    val listeners = mutableListOf<Triple<String, String, Boolean>>()
    val unixConf = mutableListOf<Set<String>>()
    override fun onSocksConfChange(changes: Set<String>) {
        unixConf.add(changes)
    }
    override fun update(daemon: TorState.Daemon?, network: TorState.Network?) {
        states.add(daemon to network)
    }
    override fun update(type: String, address: String, wasClosed: Boolean) {
        listeners.add(Triple(type, address, wasClosed))
    }
}
