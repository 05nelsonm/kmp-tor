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
package io.matthewnelson.kmp.tor.common.address

import kotlin.test.*

class OnionUrlUnitTest {

    @Test
    fun givenUrlString_whenValidOnionUrlString_returnsOnionUrl() {
        val expectedScheme = Scheme.WS
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedPort = 1234
        val expectedPath = "/some/path"
        val expectedUrl = "$expectedScheme$expectedAddress.onion:$expectedPort$expectedPath"

        val onionUrl = OnionUrl.fromString(expectedUrl)
        assertNotNull(onionUrl)
        assertEquals(expectedScheme, onionUrl.scheme)
        assertEquals(expectedAddress, onionUrl.address.value)
        assertEquals(expectedPath, onionUrl.path)
        assertEquals(expectedPort, onionUrl.port?.value)

        assertEquals(expectedUrl, onionUrl.toString())
    }

    @Test
    fun givenOnlyOnionAddress_whenValid_returnsOnionUrl() {
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedUrl = "${Scheme.HTTP}$expectedAddress.onion"

        val onionUrl = OnionUrl.fromString(expectedAddress)

        assertNotNull(onionUrl)
        assertEquals(expectedUrl, onionUrl.toString())
    }

    @Test
    fun givenOnlyOnionAddress_whenInvalid_returnsOnionUrl() {
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS.drop(1)
        val onionUrl = OnionUrl.fromStringOrNull(expectedAddress)
        assertNull(onionUrl)
    }

    @Test
    fun givenUrlString_whenInvalidOnionUrlString_returnNull() {
        val expectedScheme = Scheme.WS
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedPort = 1234
        val expectedPath = "some/path"
        val expectedUrl = "$expectedScheme$expectedAddress.com:$expectedPort/$expectedPath"

        val onionUrl = OnionUrl.fromStringOrNull(expectedUrl)
        assertNull(onionUrl)
    }

    @Test
    fun givenUrlStringWithPort_whenPathEmpty_returnsSameAddress() {
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedPath = ""
        val expectedPort = 12345
        val expectedUrl = "${Scheme.HTTP}$expectedAddress.onion:$expectedPort$expectedPath"

        val onionUrl = OnionUrl.fromString(expectedUrl)

        assertNotNull(onionUrl)
        assertEquals(expectedPath, onionUrl.path)
        assertEquals(expectedPort, onionUrl.port?.value)
        assertEquals(expectedUrl, onionUrl.toString())
    }

    @Test
    fun givenUrlStringWithoutPort_whenPathEmpty_returnsSameAddress() {
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedPath = ""
        val expectedUrl = "${Scheme.HTTP}$expectedAddress.onion$expectedPath"

        val onionUrl = OnionUrl.fromString(expectedUrl)

        assertNotNull(onionUrl)
        assertEquals(expectedPath, onionUrl.path)
        assertNull(onionUrl.port)
        assertEquals(expectedUrl, onionUrl.toString())
    }

    @Test
    fun givenUrlStringWithPort_whenPathNotEmpty_returnsSameAddress() {
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedPath = "/"
        val expectedPort = 12345
        val expectedUrl = "${Scheme.HTTP}$expectedAddress.onion:$expectedPort$expectedPath"

        val onionUrl = OnionUrl.fromString(expectedUrl)

        assertNotNull(onionUrl)
        assertEquals(expectedPath, onionUrl.path)
        assertEquals(expectedPort, onionUrl.port?.value)
        assertEquals(expectedUrl, onionUrl.toString())
    }

    @Test
    fun givenUrlStringWithoutPort_whenPathNotEmpty_returnsSameAddress() {
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedPath = "/"
        val expectedUrl = "${Scheme.HTTP}$expectedAddress.onion$expectedPath"

        val onionUrl = OnionUrl.fromString(expectedUrl)

        assertNotNull(onionUrl)
        assertEquals(expectedPath, onionUrl.path)
        assertNull(onionUrl.port)
        assertEquals(expectedUrl, onionUrl.toString())
    }

    @Test
    fun givenUrlString_whenSchemeAbsent_returnsDefaultHttp() {
        val expectedAddress = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val expectedPath = "/"
        val expectedPort = 12345
        val url = "$expectedAddress.onion:$expectedPort$expectedPath"
        val expectedUrl = Scheme.HTTP.toString() + url

        val onionUrl = OnionUrl.fromString(url)

        assertNotNull(onionUrl)
        assertEquals(expectedPath, onionUrl.path)
        assertEquals(expectedPort, onionUrl.port?.value)
        assertEquals(expectedUrl, onionUrl.toString())
    }
}
