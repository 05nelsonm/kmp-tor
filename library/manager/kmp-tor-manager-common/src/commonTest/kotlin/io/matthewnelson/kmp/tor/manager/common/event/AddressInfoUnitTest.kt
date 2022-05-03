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
package io.matthewnelson.kmp.tor.manager.common.event

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.AddressInfo
import kotlin.test.*

class AddressInfoUnitTest {

    companion object {
        const val ADDRESS = "127.0.0.1"
        const val PORT_DNS = "48494"
        const val PORT_HTTP = "48495"
        const val PORT_SOCKS = "48496"
        const val PORT_TRANS = "48497"
    }

    private val info = AddressInfo(
        dns = setOf("$ADDRESS:$PORT_DNS"),
        http = setOf("$ADDRESS:$PORT_HTTP"),
        socks = setOf("$ADDRESS:$PORT_SOCKS"),
        trans = setOf("$ADDRESS:$PORT_TRANS"),
    )

    @Test
    fun givenPortInfo_whenNoConstructorArgs_portsAreNull() {
        assertTrue(AddressInfo.NULL_VALUES.isNull)
    }

    @Test
    fun givenNonNullPortInfo_whenDnsSplit_returnsSuccessful() {
        val set = info.dnsInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().ipAddress)
        assertEquals(PORT_DNS.toInt(), set.first().port.value)
    }

    @Test
    fun givenNonNullPortInfo_whenHttpSplit_returnsSuccessful() {
        val set = info.httpInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().ipAddress)
        assertEquals(PORT_HTTP.toInt(), set.first().port.value)
    }

    @Test
    fun givenNonNullPortInfo_whenSocksSplit_returnsSuccessful() {
        val set = info.socksInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().ipAddress)
        assertEquals(PORT_SOCKS.toInt(), set.first().port.value)
    }

    @Test
    fun givenNonNullPortInfo_whenTransSplit_returnsSuccessful() {
        val set = info.transInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().ipAddress)
        assertEquals(PORT_TRANS.toInt(), set.first().port.value)
    }

    @Test
    fun givenNullPortInfo_whenSplit_returnsNull() {
        val newInfo = info.copy(http = null)
        assertNull(newInfo.http)
        val result = newInfo.httpInfoToProxyAddressOrNull()
        assertNull(result)
    }
}
