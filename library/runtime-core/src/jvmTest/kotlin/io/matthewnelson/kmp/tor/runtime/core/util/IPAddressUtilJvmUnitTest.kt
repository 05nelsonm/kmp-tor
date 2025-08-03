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
package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V6.Companion.toIPAddressV6
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.internal.IsUnixLikeHost
import java.net.Inet6Address
import java.net.NetworkInterface
import kotlin.test.*

class IPAddressUtilJvmUnitTest {

    @Test
    fun givenIPAddressV4_whenToInetAddress_thenIsSuccess() {
        IPAddress.V4.AnyHost.toInetAddress()
    }

    @Test
    fun givenIPAddressV6NoScope_whenToInetAddress_thenIsSuccess() {
        val inet = IPAddress.V6.AnyHost.NoScope.toInetAddress()
        assertIs<Inet6Address>(inet)
        assertEquals(0, inet.scopeId)
        assertNull(inet.scopedInterface)
    }

    @Test
    fun givenIPAddressV6ScopeDigit_whenToInetAddress_thenIsSuccess() {
        val inet = IPAddress.V6.AnyHost.of("1").toInetAddress()
        assertIs<Inet6Address>(inet)
        assertEquals(1, inet.scopeId)
    }

    @Test
    fun givenIPAddressV6ScopeName_whenToInetAddress_thenIsSuccessful() {
        val localHost = LocalHost.IPv6.resolve()
        val inet6 = localHost.toInet6Address()

        val nif = NetworkInterface.getByInetAddress(inet6)

        val localHostScoped = localHost.address().toIPAddressV6(scope = nif.name)
        assertNotNull(localHostScoped.scope)
        assertNull(localHostScoped.scope.toIntOrNull())

        if (IsUnixLikeHost) {
            val inetLocalHost = localHostScoped.toInet6Address()
            assertTrue(inetLocalHost.scopeId > 0)
            assertEquals(inetLocalHost.scopeId.toString(), inetLocalHost.toIPAddressV6().scope)
        }
    }
}
