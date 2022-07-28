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

import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.AddressInfo
import kotlin.test.*

class AddressInfoUnitTest {

    companion object {
        const val ADDRESS = "127.0.0.1"

        const val PORT_DNS = "48494"
        const val PORT_HTTP = "48495"
        const val PORT_SOCKS = "48496"
        const val PORT_TRANS = "48497"

        const val UNIX_SOCKS = "/tmp/app/tor/data/${TorConfig.Setting.UnixSockets.Socks.DEFAULT_NAME}"
    }

    private val info = AddressInfo(
        dns = setOf("$ADDRESS:$PORT_DNS"),
        http = setOf("$ADDRESS:$PORT_HTTP"),
        socks = setOf("$ADDRESS:$PORT_SOCKS"),
        trans = setOf("$ADDRESS:$PORT_TRANS"),
        unixSocks = setOf(Path(UNIX_SOCKS)),
    )

    @Test
    fun givenAddressInfo_whenNoConstructorArgs_portsAreNull() {
        val nullInfo = AddressInfo.NULL_VALUES

        assertNull(nullInfo.dns)
        assertNull(nullInfo.http)
        assertNull(nullInfo.socks)
        assertNull(nullInfo.trans)
        assertNull(nullInfo.unixSocks)

        assertTrue(nullInfo.isNull)
    }

    @Test
    fun givenAddressInfo_whenArgs_infoIsNotNull() {
        assertFalse(AddressInfo(dns = setOf("")).isNull)
        assertFalse(AddressInfo(http = setOf("")).isNull)
        assertFalse(AddressInfo(socks = setOf("")).isNull)
        assertFalse(AddressInfo(trans = setOf("")).isNull)
        assertFalse(AddressInfo(unixSocks = setOf(Path(""))).isNull)
    }

    @Test
    fun givenNonNullAddressInfo_whenDnsSplit_returnsSuccessful() {
        val set = info.dnsInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().address.value)
        assertEquals(PORT_DNS.toInt(), set.first().port.value)
    }

    @Test
    fun givenNonNullAddressInfo_whenHttpSplit_returnsSuccessful() {
        val set = info.httpInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().address.value)
        assertEquals(PORT_HTTP.toInt(), set.first().port.value)
    }

    @Test
    fun givenNonNullAddressInfo_whenSocksSplit_returnsSuccessful() {
        val set = info.socksInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().address.value)
        assertEquals(PORT_SOCKS.toInt(), set.first().port.value)
    }

    @Test
    fun givenNonNullAddressInfo_whenTransSplit_returnsSuccessful() {
        val set = info.transInfoToProxyAddress()
        assertEquals(ADDRESS, set.first().address.value)
        assertEquals(PORT_TRANS.toInt(), set.first().port.value)
    }

    @Test
    fun givenNullAddressInfo_whenSplit_returnsNull() {
        val newInfo = info.copy(http = null)
        assertNull(newInfo.http)
        val result = newInfo.httpInfoToProxyAddressOrNull()
        assertNull(result)
    }
}
