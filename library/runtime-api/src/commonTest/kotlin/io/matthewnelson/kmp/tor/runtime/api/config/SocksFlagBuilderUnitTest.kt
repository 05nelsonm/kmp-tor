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
package io.matthewnelson.kmp.tor.runtime.api.config

import kotlin.test.Test
import kotlin.test.assertEquals

class SocksFlagBuilderUnitTest {

    @Test
    fun givenFlags_whenFalse_thenAreRemoved() {
        val flags = mutableSetOf<String>()
        val allFlags = mutableSetOf<String>()
        var i = 0

        listOf<Triple<String, SocksFlagBuilder.() -> Unit, SocksFlagBuilder.() -> Unit>>(
            Triple("NoIPv4Traffic", { NoIPv4Traffic = true }, { NoIPv4Traffic = false }),
            Triple("IPv6Traffic", { IPv6Traffic = true }, { IPv6Traffic = false }),
            Triple("PreferIPv6", { PreferIPv6 = true }, { PreferIPv6 = false }),
            Triple("NoDNSRequest", { NoDNSRequest = true }, { NoDNSRequest = false }),
            Triple("NoOnionTraffic", { NoOnionTraffic = true }, { NoOnionTraffic = false }),
            Triple("OnionTrafficOnly", { OnionTrafficOnly = true }, { OnionTrafficOnly = false }),
            Triple("CacheIPv4DNS", { CacheIPv4DNS = true }, { CacheIPv4DNS = false }),
            Triple("CacheIPv6DNS", { CacheIPv6DNS = true }, { CacheIPv6DNS = false }),
            Triple("CacheDNS", { CacheDNS = true }, { CacheDNS = false }),
            Triple("UseIPv4Cache", { UseIPv4Cache = true }, { UseIPv4Cache = false }),
            Triple("UseIPv6Cache", { UseIPv6Cache = true }, { UseIPv6Cache = false }),
            Triple("UseDNSCache", { UseDNSCache = true }, { UseDNSCache = false }),
            Triple("PreferIPv6Automap", { PreferIPv6Automap = true }, { PreferIPv6Automap = false }),
            Triple("PreferSOCKSNoAuth", { PreferSOCKSNoAuth = true }, { PreferSOCKSNoAuth = false }),
        ).forEach { (expected, enable, disable) ->
            i++
            SocksFlagBuilder.configure(allFlags) { enable() }

            SocksFlagBuilder.configure(flags) { enable() }
            assertEquals(1, flags.size)
            assertEquals(expected, flags.first())

            SocksFlagBuilder.configure(flags) { /* no action */ }
            assertEquals(1, flags.size)

            SocksFlagBuilder.configure(flags) { disable() }
            assertEquals(0, flags.size)
        }

        assertEquals(i, allFlags.size)
    }
}
