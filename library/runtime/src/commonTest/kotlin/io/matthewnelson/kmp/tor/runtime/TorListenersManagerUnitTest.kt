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

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress.Companion.toIPSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TorListenersManagerUnitTest {

    private class TestTorListenersManager(
        scope: TestScope,
        notifyDelay: Duration,
    ): TorListeners.AbstractManager(scope, null, notifyDelay) {

        val notifyListeners = mutableListOf<TorListeners>()
        val notifyState = mutableListOf<TorState>()

        override fun notify(listeners: TorListeners) {
            notifyListeners.add(listeners)
        }
        override fun notify(state: TorState) {
            notifyState.add(state)
        }
        override fun notifyReady() {}
    }

    private fun runListenerTest(
        context: CoroutineContext = EmptyCoroutineContext,
        timeout: Duration = 60.seconds,
        notifyDelay: Duration = 100.milliseconds,
        testBody: suspend TestScope.(manager: TestTorListenersManager) -> Unit
    ): TestResult = runTest(context, timeout) {
        testBody(this, TestTorListenersManager(this, notifyDelay))
    }

    private companion object {
        val address = "127.0.0.1:9055".toIPSocketAddress()
    }

    @Test
    fun givenListeners_whenEmpty_thenIsEmptyTrue() {
        val listeners = TorListeners()
        assertTrue(listeners.isEmpty)
        assertEquals(0, listeners.dir.size)
        assertEquals(0, listeners.dns.size)
        assertEquals(0, listeners.http.size)
        assertEquals(0, listeners.metrics.size)
        assertEquals(0, listeners.natd.size)
        assertEquals(0, listeners.or.size)
        assertEquals(0, listeners.orExt.size)
        assertEquals(0, listeners.socks.size)
        assertEquals(0, listeners.socksUnix.size)
        assertEquals(0, listeners.trans.size)
    }

    @Test
    fun givenListeners_whenNotEmpty_thenIsEmptyFalse() {
        val empty = TorListeners()
        assertTrue(empty.isEmpty)

        fun TorListeners.assertIsNotEmpty(copyTarget: TorListeners.() -> Set<*>) {
            assertFalse(isEmpty)

            val target = copyTarget(this)
            assertTrue(target.isNotEmpty())

            val all = listOf(dir, dns, http, metrics, natd, or, orExt, socks, socksUnix, trans).flatten()
            assertEquals(target.size, all.size)
        }

        empty.copy(dir = setOf(address)).assertIsNotEmpty { dir }
        empty.copy(dns = setOf(address)).assertIsNotEmpty { dns }
        empty.copy(http = setOf(address)).assertIsNotEmpty { http }
        empty.copy(metrics = setOf(address)).assertIsNotEmpty { metrics }
        empty.copy(natd = setOf(address)).assertIsNotEmpty { natd }
        empty.copy(or = setOf(address)).assertIsNotEmpty { or }
        empty.copy(orExt = setOf(address)).assertIsNotEmpty { orExt }
        empty.copy(socks = setOf(address)).assertIsNotEmpty { socks }
        empty.copy(socksUnix = setOf(SysTempDir)).assertIsNotEmpty { socksUnix }
        empty.copy(trans = setOf(address)).assertIsNotEmpty { trans }
    }

    @Test
    fun givenInitialize_whenListeners_thenIsEmpty() = runListenerTest { manager ->
        assertTrue(manager.listeners.isEmpty)
    }

    @Test
    fun givenOpen_whenDirectory_thenUpdates() = runListenerTest { manager ->
        manager.update("Directory", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.dir.size)
    }

    @Test
    fun givenOpen_whenDNS_thenUpdates() = runListenerTest { manager ->
        manager.update("DNS", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.dns.size)
    }

    @Test
    fun givenOpen_whenHTTP_thenUpdates() = runListenerTest { manager ->
        manager.update("HTTP tunnel", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.http.size)
    }

    @Test
    fun givenOpen_whenMetrics_thenUpdates() = runListenerTest { manager ->
        manager.update("Metrics", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.metrics.size)
    }

    @Test
    fun givenOpen_whenOR_thenUpdates() = runListenerTest { manager ->
        manager.update("OR", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.or.size)
    }

    @Test
    fun givenOpen_whenExtendedOR_thenUpdates() = runListenerTest { manager ->
        manager.update("Extended OR", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.orExt.size)
    }

    @Test
    fun givenOpen_whenSocksIP_thenUpdates() = runListenerTest { manager ->
        manager.update("Socks", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.socks.size)
    }

    @Test
    fun givenOpen_whenSocksUnix_thenUpdates() = runListenerTest { manager ->
        manager.update("Socks", "/unix/path", wasClosed = false)
        assertEquals(1, manager.listeners.socksUnix.size)

        manager.update("Socks", "non/absolute/path", wasClosed = false)
        assertEquals(1, manager.listeners.socksUnix.size)

        manager.update("Socks", "C:\\non_unix\\path", wasClosed = false)
        assertEquals(1, manager.listeners.socksUnix.size)

        manager.update("Socks", "/unix/path", wasClosed = true)
        assertTrue(manager.listeners.isEmpty)
    }

    @Test
    fun givenOpen_whenTrans_thenUpdates() = runListenerTest { manager ->
        manager.update("Transparent pf/netfilter", address.value, wasClosed = false)
        assertEquals(1, manager.listeners.trans.size)
    }

    @Test
    fun givenListenersOrEmpty_whenNotBootstrapped_thenReturnsEmpty() = runListenerTest { manager ->
        manager.update("DNS", address.value, wasClosed = false)
        assertFalse(manager.listeners.isEmpty)
        assertTrue(manager.listenersOrEmpty.isEmpty)
    }

    @Test
    fun givenClose_whenAddressPresent_thenUpdates() = runListenerTest { manager ->
        val types = listOf(
            "Directory",
            "DNS",
            "HTTP tunnel",
            "Socks",
            "Metrics",
            "OR",
            "Extended OR",
            "Transparent natd",
            "Transparent pf/netfilter"
        )

        types.forEach { type ->
            manager.update(type, address.value, wasClosed = false)
        }
        assertFalse(manager.listeners.isEmpty)

        types.forEach { type ->
            manager.update(type, address.value, wasClosed = true)
        }
        assertTrue(manager.listeners.isEmpty)
    }

    @Test
    fun givenClose_whenAddressNotPresent_thenDoesNotRemove() = runListenerTest { manager ->
        val types = listOf(
            "Directory",
            "DNS",
            "HTTP tunnel",
            "Socks",
            "Metrics",
            "OR",
            "Extended OR",
            "Transparent natd",
            "Transparent pf/netfilter"
        )

        types.forEach { type ->
            manager.update(type, address.value, wasClosed = false)
        }
        manager.update("DNS", "127.0.0.1:1080", wasClosed = false)
        assertFalse(manager.listeners.isEmpty)

        types.forEach { type ->
            manager.update(type, address.value, wasClosed = true)
        }
        assertFalse(manager.listeners.isEmpty)
    }

    @Test
    fun givenStateChange_whenBootstrapAndNetwork_thenNotifiesAsExpected() = runListenerTest { manager ->
        manager.update(TorState.Daemon.Starting)
        manager.update(TorState.Daemon.On(10))
        // not bootstrapped & no network
        assertEquals(0, manager.notifyListeners.size)

        manager.update(network = TorState.Network.Enabled)
        // not bootstrapped
        assertEquals(0, manager.notifyListeners.size)
        manager.update("DNS", address.value, wasClosed = false)

        manager.update(network = TorState.Network.Disabled)
        manager.update(TorState.Daemon.On(100))
        // network is not enabled
        assertEquals(0, manager.notifyListeners.size)

        manager.update(network = TorState.Network.Enabled)
        // bootstrapped + network enabled
        assertEquals(1, manager.notifyListeners.size)
        assertFalse(manager.listeners.isEmpty)
        assertEquals(manager.notifyListeners[0], manager.listeners)

        manager.update(network = TorState.Network.Disabled)
        assertEquals(2, manager.notifyListeners.size)
        assertTrue(manager.listeners.isEmpty)
        assertTrue(manager.notifyListeners[1].isEmpty)

        manager.update(network = TorState.Network.Enabled)
        assertEquals(3, manager.notifyListeners.size)
        // Were cleared and haven't been updated in test, so should
        // be what was last set as current listeners.
        assertTrue(manager.listeners.isEmpty)
    }

    @Test
    fun givenBootstrappedAndNetwork_whenUpdated_thenNotifyIsDelayed() = runListenerTest(
        notifyDelay = 5.milliseconds,
    ) { manager ->
        manager.update(TorState.Daemon.Starting)
        manager.update(TorState.Daemon.On(5), TorState.Network.Enabled)
        manager.update("DNS", address.value, wasClosed = false)

        // notifyJob was not launched b/c not bootstrapped
        withContext(Dispatchers.Default) { delay(25.milliseconds) }
        assertEquals(0, manager.notifyListeners.size)

        manager.update(TorState.Daemon.On(100))
        assertEquals(1, manager.notifyListeners.size)

        manager.update("Socks", address.value, wasClosed = false)
        assertFalse(manager.listeners.socks.isEmpty())
        // Should be delayed for a moment and not have
        // dispatched the change immediately.
        assertEquals(1, manager.notifyListeners.size)

        withContext(Dispatchers.Default) { delay(25.milliseconds) }
        assertEquals(2, manager.notifyListeners.size)

        manager.update("Socks", address.value, wasClosed = true)
        manager.update("Transparent pf/netfilter", address.value, wasClosed = false)
        assertEquals(2, manager.notifyListeners.size)

        // job for dispatching Socks closure should have been cancelled
        // by Transparent open update.
        withContext(Dispatchers.Default) { delay(25.milliseconds) }
        assertEquals(3, manager.notifyListeners.size)
        assertTrue(manager.notifyListeners.last().socks.isEmpty())
    }
}
