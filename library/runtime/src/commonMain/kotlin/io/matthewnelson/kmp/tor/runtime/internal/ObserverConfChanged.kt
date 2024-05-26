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

import io.matthewnelson.kmp.tor.runtime.TorListeners
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorEvent

internal open class ObserverConfChanged internal constructor(
    private val manager: TorListeners.Manager,
    staticTag: String,
): TorEvent.Observer(
    TorEvent.CONF_CHANGED,
    staticTag,
    OnEvent.Executor.Immediate,
    OnEvent.noOp(),
) {

    protected override fun notify(data: String) {
        var network: TorState.Network? = null
        var socks: LinkedHashSet<String>? = null

        for (line in data.lines()) { when {
            // DisableNetwork=0
            // DisableNetwork=1
            // DisableNetwork  << Implied 1
            line.startsWith(TorConfig.DisableNetwork.name, ignoreCase = true) -> {
                network = if (line.substringAfter('=') == "0") {
                    TorState.Network.Enabled
                } else {
                    TorState.Network.Disabled
                }
            }

            // __SocksPort=unix:"/tmp/kmp_tor_test/obs_conn_no_net/work/socks2.sock" OnionTrafficOnly GroupWritable
            // SocksPort=unix:"/tmp/kmp_tor_test/obs_conn_no_net/work/socks3.sock"
            // SocksPort=unix:/tmp/kmp_tor_test/obs_conn_no_net/work/socks4.sock
            // __SocksPort=9055
            // SocksPort=127.0.0.1:9056 OnionTrafficOnly
            // SocksPort=[::1]:9055
            line.startsWith(SOCKS_PORT_EPHEMERAL, ignoreCase = true)
            || line.startsWith(SOCKS_PORT, ignoreCase = true) -> {
                if (socks == null) {
                    socks = LinkedHashSet(1, 1.0F)
                }

                socks.add(line.substringAfter(SOCKS_PORT, ""))
            }
        } }

        if (network != null) {
            manager.update(network = network)
        }

        if (socks != null) {
            manager.onListenerConfChange("Socks", socks)
        }
    }

    private companion object {
        private const val SOCKS_PORT = "SocksPort="
        private const val SOCKS_PORT_EPHEMERAL = "__$SOCKS_PORT"
    }
}
