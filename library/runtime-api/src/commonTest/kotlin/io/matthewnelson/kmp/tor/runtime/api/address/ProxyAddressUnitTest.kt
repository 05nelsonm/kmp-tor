/*
 * Copyright (c) 2023 Matthew Nelson
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
package io.matthewnelson.kmp.tor.runtime.api.address

import io.matthewnelson.kmp.tor.runtime.api.address.ProxyAddress.Companion.toProxyAddress
import io.matthewnelson.kmp.tor.runtime.api.address.ProxyAddress.Companion.toProxyAddressOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProxyAddressUnitTest {

    @Test
    fun givenIPAddressV4WithPort_whenProxyAddress_thenToStringIsAsExpected() {
        val expected = "127.0.0.1:9050"
        val actual = expected.toProxyAddress().toString()
        assertEquals(expected, actual)
    }

    @Test
    fun givenIPAddressV4WithoutPort_whenProxyAddress_thenToStringIsAsExpected() {
        assertNull("127.0.0.1".toProxyAddressOrNull())
    }

    @Test
    fun givenIPAddressV6WithPort_whenProxyAddress_thenToStringIsAsExpected() {
        val expected = "[35f4:c60a:8296:4c90:79ad:3939:69d9:ba10]:9050"
        val actual = expected.toProxyAddress().toString()
        assertEquals(expected, actual)
    }

    @Test
    fun givenIPAddressV6WithoutPort_whenProxyAddress_thenToStringIsAsExpected() {
        assertNull("[35f4:c60a:8296:4c90:79ad:3939:69d9:ba10]".toProxyAddressOrNull())
    }
    @Test
    fun givenURL_whenProxyAddress_thenToStringIsAsExpected() {
        val expected = "192.168.10.100:8080"
        val actual = "http://$expected/some/path.html".toProxyAddress().toString()
        assertEquals(expected, actual)
    }
}
