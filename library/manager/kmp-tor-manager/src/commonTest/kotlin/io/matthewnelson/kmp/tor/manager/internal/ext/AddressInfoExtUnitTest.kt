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
package io.matthewnelson.kmp.tor.manager.internal.ext

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.*
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddressInfoExtUnitTest {

    private val info = AddressInfo(socks = setOf("127.0.0.1:9050"))
    private val nullInfo = AddressInfo()

    @Test
    fun givenNonNullPorts_whenTorStateChangeOnToOff_portsNulledOut() {
        val old = State(TorState.On(100), TorNetworkState.Enabled)
        val new = State(TorState.Off, old.networkState)

        val result = info.onStateChange(old, new)
        assertTrue(result?.isNull == true)
    }

    @Test
    fun givenNonNullPorts_whenNetworkDisabled_portsNulledOut() {
        val old = State(TorState.On(100), TorNetworkState.Enabled)
        val new = old.copy(networkState = TorNetworkState.Disabled)

        val result = info.onStateChange(old, new)
        assertTrue(result?.isNull == true)
    }

    @Test
    fun givenNonNullPortsAndNetworkEnabled_whenBootstrapped_selfReturned() {
        val old = State(TorState.On(95), TorNetworkState.Enabled)
        val new = old.copy(torState = TorState.On(100))

        val result = info.onStateChange(old, new)
        assertEquals(info, result)
    }

    @Test
    fun givenNullPortsAndNetworkEnabled_whenBootstrapped_selfIsReturned() {
        val old = State(TorState.On(95), TorNetworkState.Enabled)
        val new = old.copy(torState = TorState.On(100))

        val result = nullInfo.onStateChange(old, new)
        assertEquals(nullInfo, result)
    }

    @Test
    fun givenNonNullPortsAndNetworkDisabled_whenBootstrapped_nullIsReturned() {
        val old = State(TorState.On(95), TorNetworkState.Disabled)
        val new = old.copy(torState = TorState.On(100))

        val result = info.onStateChange(old, new)
        assertNull(result)
    }

    @Test
    fun givenNullPortsAndNetworkDisabled_whenBootstrapped_nullIsReturned() {
        val old = State(TorState.On(95), TorNetworkState.Disabled)
        val new = old.copy(torState = TorState.On(100))

        val result = nullInfo.onStateChange(old, new)
        assertNull(result)
    }

    @Test
    fun givenNonNullPortsAndBootstrapped_whenNetworkReEnabled_selfIsReturned() {
        val old = State(TorState.On(100), TorNetworkState.Disabled)
        val new = old.copy(networkState = TorNetworkState.Enabled)

        val result = info.onStateChange(old, new)
        assertEquals(info, result)
    }

    @Test
    fun givenNullPortsAndBootstrapped_whenNetworkReEnabled_selfIsReturned() {
        val old = State(TorState.On(100), TorNetworkState.Disabled)
        val new = old.copy(networkState = TorNetworkState.Enabled)

        val result = nullInfo.onStateChange(old, new)
        assertEquals(nullInfo, result)
    }
}
