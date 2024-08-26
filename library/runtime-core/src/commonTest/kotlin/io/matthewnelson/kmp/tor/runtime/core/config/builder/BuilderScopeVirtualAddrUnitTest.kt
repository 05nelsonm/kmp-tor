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

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V4.Companion.toIPAddressV4
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V6.Companion.toIPAddressV6
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import kotlin.test.Test
import kotlin.test.assertTrue

class BuilderScopeVirtualAddrUnitTest {

    @Test
    fun givenIPv4_whenSetting_thenIsProperlyFormatted() {
        val item = VirtualAddrNetworkIPv4.asSetting {
            // do not use AnyHost for real... just doing so to test.
            address(IPAddress.V4.AnyHost)
            bits(11)
        }.items.first()

        assertTrue(item.argument.endsWith("/11"))
        assertTrue(item.argument.startsWith(IPAddress.V4.AnyHost.value))
    }

    @Test
    fun givenIPv6_whenSetting_thenIsProperlyFormatted() {
        val item = VirtualAddrNetworkIPv6.asSetting {
            // do not use AnyHost for real... just doing so to test.
            address(IPAddress.V6.AnyHost)
            bits(20)
        }.items.first()

        assertTrue(item.argument.startsWith('['))
        assertTrue(item.argument.endsWith("]/20"))
        assertTrue(item.argument.contains(IPAddress.V6.AnyHost.value))
    }

    @Test
    fun givenIPv4_whenNoModification_thenOptionDefaultIsUsed() {
        val argument = VirtualAddrNetworkIPv4.asSetting {}.items.first().argument
        val defIP = VirtualAddrNetworkIPv4.default.toIPAddressV4()
        val defBits = VirtualAddrNetworkIPv4.default.substringAfterLast('/')
        assertTrue(argument.startsWith(defIP.canonicalHostName()))
        assertTrue(argument.endsWith(defBits))
    }

    @Test
    fun givenIPv6_whenNoModification_thenOptionDefaultIsUsed() {
        val argument = VirtualAddrNetworkIPv6.asSetting {}.items.first().argument
        val defIP = VirtualAddrNetworkIPv6.default.toIPAddressV6()
        val defBits = VirtualAddrNetworkIPv6.default.substringAfterLast('/')
        assertTrue(argument.startsWith(defIP.canonicalHostName()))
        assertTrue(argument.endsWith(defBits))
    }
}
