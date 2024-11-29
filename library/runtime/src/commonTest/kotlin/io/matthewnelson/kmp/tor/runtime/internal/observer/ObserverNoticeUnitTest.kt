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

class ObserverNoticeUnitTest {

    private class TestNoticeObserver private constructor(
        val manager: TestTorListenersManager,
    ): ObserverNotice(manager, "") {
        constructor(): this(TestTorListenersManager())
        public override fun notify(data: String) { super.notify(data) }
    }

    private val observer = TestNoticeObserver()

    @Test
    fun givenBootstrapped_whenParsed_thenUpdatesTorStateManager() {
        val values = listOf(
            5 to "Bootstrapped 5% (conn): Connecting to a relay",
            10 to "Bootstrapped 10% (conn_done): Connected to a relay",
            14 to "Bootstrapped 14% (handshake): Handshaking with a relay",
            15 to "Bootstrapped 15% (handshake_done): Handshake with a relay done",
            75 to "Bootstrapped 75% (enough_dirinfo): Loaded enough directory info to build circuits",
            90 to "Bootstrapped 90% (ap_handshake_done): Handshake finished with a relay to build circuits",
            95 to "Bootstrapped 95% (circuit_create): Establishing a Tor circuit",
            100 to "Bootstrapped 100% (done): Done",
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
            Pair("Directory", "127.0.0.1:53085") to "Closing no-longer-configured Directory listener on 127.0.0.1:53085",
            Pair("DNS", "127.0.0.1:53085") to "Closing no-longer-configured DNS listener on 127.0.0.1:53085",
            Pair("HTTP tunnel", "127.0.0.1:48932") to "Closing no-longer-configured HTTP tunnel listener on 127.0.0.1:48932",
            Pair("Metrics", "127.0.0.1:9150") to "Closing no-longer-configured Metrics listener on 127.0.0.1:9150",
            Pair("OR", "127.0.0.1:9150") to "Closing no-longer-configured OR listener on 127.0.0.1:9150",
            Pair("Extended OR", "127.0.0.1:9150") to "Closing no-longer-configured Extended OR listener on 127.0.0.1:9150",
            Pair("Socks", "127.0.0.1:9150") to "Closing no-longer-configured Socks listener on 127.0.0.1:9150",
            Pair("Transparent natd", "127.0.0.1:45963") to "Closing no-longer-configured Transparent natd listener on 127.0.0.1:45963",
            Pair("Transparent pf/netfilter", "127.0.0.1:45963") to "Closing no-longer-configured Transparent pf/netfilter listener on 127.0.0.1:45963",
            Pair("Socks", "???:0") to "Closing no-longer-configured Socks listener on ???:0",
            Pair("Socks", "/tmp/kmp_tor_test/obs_conn_no_net/work/socks5.sock") to "Closing partially-constructed Socks listener connection (ready) on /tmp/kmp_tor_test/obs_conn_no_net/work/socks5.sock",
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
            Pair("Directory", "127.0.0.1:53085") to "Opened Directory listener connection (ready) on 127.0.0.1:53085",
            Pair("DNS", "127.0.0.1:53085") to "Opened DNS listener connection (ready) on 127.0.0.1:53085",
            Pair("HTTP tunnel", "127.0.0.1:48932") to "Opened HTTP tunnel listener connection (ready) on 127.0.0.1:48932",
            Pair("Metrics", "127.0.0.1:9150") to "Opened Metrics listener connection (ready) on 127.0.0.1:9150",
            Pair("OR", "127.0.0.1:9150") to "Opened OR listener connection (ready) on 127.0.0.1:9150",
            Pair("Extended OR", "127.0.0.1:9150") to "Opened Extended OR listener connection (ready) on 127.0.0.1:9150",
            Pair("Socks", "127.0.0.1:9150") to "Opened Socks listener connection (ready) on 127.0.0.1:9150",
            Pair("Transparent natd", "127.0.0.1:45963") to "Opened Transparent natd listener connection (ready) on 127.0.0.1:45963",
            Pair("Transparent pf/netfilter", "127.0.0.1:45963") to "Opened Transparent pf/netfilter listener connection (ready) on 127.0.0.1:45963",
            Pair("Socks", "/tmp/kmp_tor_test/sf_restart/work/socks.sock") to "Opened Socks listener connection (ready) on /tmp/kmp_tor_test/sf_restart/work/socks.sock",
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
