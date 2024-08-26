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
package io.matthewnelson.kmp.tor.runtime.core.net

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V6.Companion.toIPAddressV6
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddressV6UnitTest.Companion.TEST_ADDRESSES_IPV6
import java.net.Inet6Address
import kotlin.test.*

class IPAddressV6JvmUnitTest {

    @Test
    fun givenIPv6String_whenIPAddressV6_thenMatchesJavaInet6Address() {
        for (address in TEST_ADDRESSES_IPV6.lines()) {
            assertFalse(address.contains('%'))

            run {
                val ip = address.toIPAddressV6()

                val inet = Inet6Address.getByAddress(null, ip.address(), null)
                assertEquals(inet.hostAddress, ip.value)
                assertContentEquals(inet.address, ip.address())
            }

            run {
                val inet = Inet6Address.getByName(address)
                assertIs<Inet6Address>(inet)

                val ip = inet.address.toIPAddressV6()
                assertEquals(ip.value, inet.hostAddress)
                assertContentEquals(ip.address(), inet.address)
            }
        }
    }
}
