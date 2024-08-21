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
package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BuilderScopeVirtualAddrUnitTest {

    @Test
    fun givenIPv4_whenBitsMisconfigured_thenThrowsException() {
        val item = VirtualAddrNetworkIPv4.asSetting {
            // default
            build()

            // Max should be 16 (inclusive)
            bits(16)
            assertTrue(build().items.first().argument.endsWith("/16"))

            bits(17)
            assertFailsWith<IllegalArgumentException> { build() }

            // Min should be 0 (inclusive)
            bits(0)
            assertTrue(build().items.first().argument.endsWith("/0"))

            bits(-1)
            assertFailsWith<IllegalArgumentException> { build() }

            bits(15)
        }.items.first()

        assertTrue(item.argument.endsWith("/15"))
    }

    @Test
    fun givenIPv6_whenBitsMisconfigured_thenThrowsException() {
        val item = VirtualAddrNetworkIPv6.asSetting {
            // default
            build()

            // Max should be 104 (inclusive)
            bits(104)
            assertTrue(build().items.first().argument.endsWith("]/104"))

            bits(105)
            assertFailsWith<IllegalArgumentException> { build() }

            // Min should be 0 (inclusive)
            bits(0)
            assertTrue(build().items.first().argument.endsWith("]/0"))

            bits(-1)
            assertFailsWith<IllegalArgumentException> { build() }

            bits(24)
        }.items.first()

        assertTrue(item.argument.endsWith("]/24"))
    }

    @Test
    fun givenIPv4_whenSetting_thenIsProperlyFormatted() {
        val item = VirtualAddrNetworkIPv4.asSetting {
            address(IPAddress.V4.AnyHost)
            bits(11)
        }.items.first()

        assertTrue(item.argument.endsWith("/11"))
        assertTrue(item.argument.startsWith(IPAddress.V4.AnyHost.value))
    }

    @Test
    fun givenIPv6_whenSetting_thenIsProperlyFormatted() {
        val item = VirtualAddrNetworkIPv6.asSetting {
            address(IPAddress.V6.AnyHost)
            bits(20)
        }.items.first()

        assertTrue(item.argument.startsWith('['))
        assertTrue(item.argument.endsWith("]/20"))
        assertTrue(item.argument.contains(IPAddress.V6.AnyHost.value))
    }
}
