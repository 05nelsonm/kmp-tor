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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.address

import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.tryParsingEtcHosts
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.tryParsingIfConfig
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.tryPlatformResolve
import kotlin.test.Test
import kotlin.test.assertNotNull

class LocalHostUnitTest {

    @Test
    fun givenIPv4_whenResolved_thenIsCached() {
        LocalHost.IPv4.resolve()
        assertNotNull(LocalHost.IPv4.fromCache())
    }

    @Test
    fun givenIPv6_whenResolved_thenIsCached() {
        LocalHost.IPv6.resolve()
        assertNotNull(LocalHost.IPv6.fromCache())
    }

    @Test
    fun givenAnyHost_whenPlatformResolve_thenReturnsLocalHostIPs() {
        val set = LinkedHashSet<IPAddress>(2, 1.0F)
        LocalHost.tryPlatformResolve(set)
        // No assertions here as host machine running tests may not have
        println("PLATFORM: $set")
    }

    @Test
    fun givenUnixHost_whenIfConfig_thenReturnsLocalHostIPs() {
        val set = LinkedHashSet<IPAddress>(2, 1.0F)
        LocalHost.tryParsingIfConfig(set)
        // No assertions here as host machine running tests may not have
        println("IF_CONFIG: $set")
    }

    @Test
    fun givenUnixHost_whenEtcHosts_thenReturnsLocalHostIPs() {
        val set = LinkedHashSet<IPAddress>(2, 1.0F)
        LocalHost.tryParsingEtcHosts(set)
        // No assertions here as host machine running tests may not have
        println("ETC_HOSTS: $set")
    }
}
