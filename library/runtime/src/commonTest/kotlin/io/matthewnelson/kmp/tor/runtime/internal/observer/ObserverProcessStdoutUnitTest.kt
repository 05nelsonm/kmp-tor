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
package io.matthewnelson.kmp.tor.runtime.internal.observer

import io.matthewnelson.kmp.tor.runtime.test.TestTorListenersManager
import io.matthewnelson.kmp.tor.runtime.TorState
import kotlin.test.*

class ObserverProcessStdoutUnitTest {

    private class TestProcessStdoutObserver private constructor(
        val manager: TestTorListenersManager,
    ): ObserverProcessStdout(manager) {
        constructor(): this(TestTorListenersManager())
        public override fun notify(line: String) { super.notify(line) }
    }

    private val observer = TestProcessStdoutObserver()

    @Test
    fun givenBootstrapped_whenParsed_thenUpdatesTorStateManager() {
        val values = listOf(
            5 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 5% (conn): Connecting to a relay",
            10 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 10% (conn_done): Connected to a relay",
            14 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 14% (handshake): Handshaking with a relay",
            15 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 15% (handshake_done): Handshake with a relay done",
            75 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 75% (enough_dirinfo): Loaded enough directory info to build circuits",
            90 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 90% (ap_handshake_done): Handshake finished with a relay to build circuits",
            95 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Bootstrapped 95% (circuit_create): Establishing a Tor circuit",
            100 to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:08.000 [notice] Bootstrapped 100% (done): Done",
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

    @Test
    fun givenCloseListener_whenParsed_thenUpdatesTorListenersManager() {
        val values = listOf(
            Pair("Directory", "127.0.0.1:53085") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured Directory listener on 127.0.0.1:53085",
            Pair("DNS", "127.0.0.1:53085") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured DNS listener on 127.0.0.1:53085",
            Pair("HTTP tunnel", "127.0.0.1:48932") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured HTTP tunnel listener on 127.0.0.1:48932",
            Pair("Metrics", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured Metrics listener on 127.0.0.1:9150",
            Pair("OR", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured OR listener on 127.0.0.1:9150",
            Pair("Extended OR", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured Extended OR listener on 127.0.0.1:9150",
            Pair("Socks", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured Socks listener on 127.0.0.1:9150",
            Pair("Transparent natd", "127.0.0.1:45963") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured Transparent natd listener on 127.0.0.1:45963",
            Pair("Transparent pf/netfilter", "127.0.0.1:45963") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured Transparent pf/netfilter listener on 127.0.0.1:45963",
            Pair("Socks", "???:0") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing no-longer-configured Socks listener on ???:0",
            Pair("Socks", "/tmp/kmp_tor_test/obs_conn_no_net/work/socks5.sock") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Closing partially-constructed Socks listener connection (ready) on /tmp/kmp_tor_test/obs_conn_no_net/work/socks5.sock",
        )

        values.forEach { (_, line) -> observer.notify(line) }

        assertEquals(values.size, observer.manager.listeners.size)
        values.forEachIndexed { i, (expected, _) ->
            val (expectedType, expectedAddress) = expected
            val (type, address, wasClosed) = observer.manager.listeners[i]

            assertEquals(expectedType, type)
            assertEquals(expectedAddress, address)
            assertTrue(wasClosed)
        }
    }

    @Test
    fun givenOpenListener_whenParsed_thenUpdatesTorListenersManager() {
        val values = listOf(
            Pair("Directory", "127.0.0.1:53085") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened Directory listener connection (ready) on 127.0.0.1:53085",
            Pair("DNS", "127.0.0.1:53085") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened DNS listener connection (ready) on 127.0.0.1:53085",
            Pair("HTTP tunnel", "127.0.0.1:48932") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened HTTP tunnel listener connection (ready) on 127.0.0.1:48932",
            Pair("Metrics", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened Metrics listener connection (ready) on 127.0.0.1:9150",
            Pair("OR", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened OR listener connection (ready) on 127.0.0.1:9150",
            Pair("Extended OR", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened Extended OR listener connection (ready) on 127.0.0.1:9150",
            Pair("Socks", "127.0.0.1:9150") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened Socks listener connection (ready) on 127.0.0.1:9150",
            Pair("Transparent natd", "127.0.0.1:45963") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened Transparent natd listener connection (ready) on 127.0.0.1:45963",
            Pair("Transparent pf/netfilter", "127.0.0.1:45963") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened Transparent pf/netfilter listener connection (ready) on 127.0.0.1:45963",
            Pair("Socks", "/tmp/kmp_tor_test/sf_restart/work/socks.sock") to "TorDaemon[fid=A3C2…6595]@1414604497 May 24 18:10:05.000 [notice] Opened Socks listener connection (ready) on /tmp/kmp_tor_test/sf_restart/work/socks.sock",
        )

        values.forEach { (_, line) -> observer.notify(line) }

        assertEquals(values.size, observer.manager.listeners.size)
        values.forEachIndexed { i, (expected, _) ->
            val (expectedType, expectedAddress) = expected
            val (type, address, wasClosed) = observer.manager.listeners[i]

            assertEquals(expectedType, type)
            assertEquals(expectedAddress, address)
            assertFalse(wasClosed)
        }
    }
}
