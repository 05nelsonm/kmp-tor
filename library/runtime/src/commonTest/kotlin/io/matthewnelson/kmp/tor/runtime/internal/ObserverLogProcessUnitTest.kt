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

import io.matthewnelson.kmp.tor.runtime.TorState
import kotlin.test.*

class ObserverLogProcessUnitTest {

    private class TestTorStateManager: TorState.Manager {
        val states = mutableListOf<Pair<TorState.Daemon?, TorState.Network?>>()
        override fun update(daemon: TorState.Daemon?, network: TorState.Network?) {
            states.add(daemon to network)
        }
    }

    private class TestLogProcessObserver private constructor(
        val manager: TestTorStateManager,
    ): ObserverLogProcess(manager) {
        constructor(): this(TestTorStateManager())
        public override fun notify(line: String) { super.notify(line) }
    }

    private val observer = TestLogProcessObserver()

    @Test
    fun givenBootstrapped_whenParsed_thenUpdatesTorStateManager() {
        val values = listOf(
            5 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 5% (conn): Connecting to a relay",
            10 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 10% (conn_done): Connected to a relay",
            14 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 14% (handshake): Handshaking with a relay",
            15 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 15% (handshake_done): Handshake with a relay done",
            75 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 75% (enough_dirinfo): Loaded enough directory info to build circuits",
            90 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 90% (ap_handshake_done): Handshake finished with a relay to build circuits",
            95 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 95% (circuit_create): Establishing a Tor circuit",
            100 to "TorProcess[fid=A3C2…6595]@1414604497 May 24 18:10:08.000 [notice] Bootstrapped 100% (done): Done",
        )

        values.forEach { (_, line) -> observer.notify(line) }

        assertEquals(values.size, observer.manager.states.size)

        values.forEachIndexed { i, (expected, _) ->
            val (daemon, network) = observer.manager.states[i]
            assertNull(network)
            assertIs<TorState.Daemon.On>(daemon)
            assertEquals(expected.toByte(), daemon.bootstrap)
        }
    }
}
