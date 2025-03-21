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

import kotlin.test.*

class TorStateManagerUnitTest {

    private class TestTorStateManager: TorState.AbstractManager(null) {

        val notifyReadies = mutableListOf<Unit>()
        val notifies = mutableListOf<Pair<TorState, TorState>>()

        protected override fun notify(old: TorState, new: TorState) {
            assertNotEquals(old, new)
            notifies.add(old to new)
        }

        protected override fun notifyReady() {
            notifyReadies.add(Unit)
        }
    }

    private val manager = TestTorStateManager()

    @Test
    fun givenInitialize_whenState_thenIsAsExpected() {
        val state = manager.state
        assertIs<TorState.Daemon.Off>(state.daemon)
        assertIs<TorState.Network.Disabled>(state.network)
    }

    @Test
    fun givenUpdate_whenNoChange_thenDoesNotNotify() {
        val state = manager.state
        manager.update()
        manager.update(state.daemon)
        manager.update(network = state.network)
        manager.update(state.daemon, state.network)

        assertEquals(0, manager.notifies.size)
        assertEquals(state, manager.state)
    }

    @Test
    fun givenDaemonOff_whenNetworkEnable_thenDoesNothing() {
        val state = manager.state
        manager.update(network = TorState.Network.Enabled)

        assertIs<TorState.Daemon.Off>(state.daemon)
        assertEquals(0, manager.notifies.size)
        assertEquals(state, manager.state)
    }

    @Test
    fun givenDaemonOn_whenDaemonStarting_thenDoesNothing() {
        manager.update(TorState.Daemon.Starting)
        manager.update(TorState.Daemon.On(0))

        assertIs<TorState.Daemon.On>(manager.state.daemon)
        assertEquals(2, manager.notifies.size)

        manager.update(TorState.Daemon.Starting)
        assertIs<TorState.Daemon.On>(manager.state.daemon)
        assertEquals(2, manager.notifies.size)
    }

    @Test
    fun givenDaemonOff_whenDaemonOnOrDaemonStopping_thenDoesNothing() {
        manager.update(TorState.Daemon.On(5))
        manager.update(TorState.Daemon.Stopping)
        assertEquals(0, manager.notifies.size)
        assertIs<TorState.Daemon.Off>(manager.state.daemon)

        manager.update(TorState.Daemon.Starting)
        manager.update(TorState.Daemon.Starting)
        assertEquals(1, manager.notifies.size)

        manager.update(TorState.Daemon.On(5))
        manager.update(TorState.Daemon.On(5))
        assertEquals(2, manager.notifies.size)
    }

    @Test
    fun givenDaemonStopping_whenDaemonOn_thenDoesNothing() {
        manager.update(TorState.Daemon.Starting)
        manager.update(TorState.Daemon.Stopping)
        assertEquals(2, manager.notifies.size)
        assertIs<TorState.Daemon.Stopping>(manager.state.daemon)

        manager.update(TorState.Daemon.On(0))
        assertEquals(2, manager.notifies.size)
        assertIs<TorState.Daemon.Stopping>(manager.state.daemon)
    }

    @Test
    fun givenBootstrapped_whenNetworkEnabled_thenNotifiesReady() {
        manager.update(TorState.Daemon.Starting)
        manager.update(TorState.Daemon.On(100))
        assertEquals(0, manager.notifyReadies.size)
        assertFalse(manager.isReady)

        // bootstrapped + network enabled
        manager.update(network = TorState.Network.Enabled)
        assertEquals(1, manager.notifyReadies.size)
        assertTrue(manager.isReady)

        // toggling network does nothing
        manager.update(network = TorState.Network.Disabled)
        assertEquals(1, manager.notifyReadies.size)
        assertTrue(manager.isReady)
        manager.update(network = TorState.Network.Enabled)
        assertEquals(1, manager.notifyReadies.size)
        assertTrue(manager.isReady)

        // will not happen in real life, but trigger again to see it was "reset"
        manager.update(TorState.Daemon.On(95), TorState.Network.Disabled)
        assertEquals(1, manager.notifyReadies.size)
        assertFalse(manager.isReady)
        manager.update(TorState.Daemon.On(100), TorState.Network.Enabled)
        assertEquals(2, manager.notifyReadies.size)
        assertTrue(manager.isReady)

        manager.update(TorState.Daemon.On(95), TorState.Network.Disabled)
        assertEquals(2, manager.notifyReadies.size)
        assertFalse(manager.isReady)
        manager.update(TorState.Daemon.On(100))
        assertEquals(2, manager.notifyReadies.size)
        assertFalse(manager.isReady)
        manager.update(network = TorState.Network.Enabled)
        assertEquals(3, manager.notifyReadies.size)
        assertTrue(manager.isReady)
    }
}
