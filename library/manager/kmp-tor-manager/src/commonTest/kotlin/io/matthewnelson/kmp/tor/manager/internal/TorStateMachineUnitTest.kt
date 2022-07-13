/*
 * Copyright (c) 2021 Matthew Nelson
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
package io.matthewnelson.kmp.tor.manager.internal

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import kotlin.test.*

class TorStateMachineUnitTest {

    private val machine: TorStateMachine = TorStateMachine { _, new ->
        this.dispatched = new
    }
    private var dispatched = TorManagerEvent.State(machine.state, machine.networkState)

    @Test
    fun givenState_whenUpdateSameState_doesNotDispatch() {
        val before = dispatched
        val updated = machine.updateState(dispatched.torState)
        assertFalse(updated)
        assertEquals(before, dispatched)
    }

    @Test
    fun givenState_whenUpdateNewState_doesDispatch() {
        val before = dispatched
        val new = if (dispatched.isOff) {
            TorState.On(0)
        } else {
            TorState.Off
        }
        val actual = machine.updateState(new)
        assertTrue(actual)
        assertNotEquals(before, dispatched)
    }

    @Test
    fun givenNetworkState_whenUpdateSameState_doesNotDispatch() {
        val before = dispatched
        val updated = machine.updateState(dispatched.networkState)
        assertFalse(updated)
        assertEquals(before, dispatched)
    }

    @Test
    fun givenNetworkState_whenUpdateNewState_doesDispatch() {
        val before = dispatched
        val new = if (dispatched.isNetworkDisabled) {
            TorNetworkState.Enabled
        } else {
            TorNetworkState.Disabled
        }
        val updated = machine.updateState(new)
        assertTrue(updated)
        assertNotEquals(before, dispatched)
    }

    @Test
    fun givenStateAndNetworkState_whenUpdateSameState_doesNotDispatch() {
        val before = dispatched
        val updated = machine.updateState(dispatched.networkState)
        assertFalse(updated)
        assertEquals(before, dispatched)
    }

    @Test
    fun givenStateAndNetworkState_whenUpdated_doesDispatch() {
        val before = dispatched
        val newNetwork = if (dispatched.isNetworkDisabled) {
            TorNetworkState.Enabled
        } else {
            TorNetworkState.Disabled
        }
        val newState = if (dispatched.isOff) {
            TorState.On(0)
        } else {
            TorState.Off
        }
        val updated = machine.updateState(newState, newNetwork)
        assertTrue(updated)
        assertNotEquals(before, dispatched)
    }
}
