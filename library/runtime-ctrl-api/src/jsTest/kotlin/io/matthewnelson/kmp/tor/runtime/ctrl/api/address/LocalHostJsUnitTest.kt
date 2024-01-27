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

import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.tryOsNetworkInterfaces
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.tryParseEtcHosts
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.tryParseIfConfig
import kotlin.test.Test

class LocalHostJsUnitTest: LocalHostBaseTest() {

    @Test
    fun givenUnixHost_whenOsNetworkInterfaces_thenReturnsLocalHostIPs() {
        val set = LinkedHashSet<IPAddress>(2, 1.0F)
        LocalHost.tryOsNetworkInterfaces(set)
        // No assertions here as host machine running tests may not have
        println(set)
    }

    @Test
    fun givenUnixHost_whenIfConfig_thenReturnsLocalHostIPs() {
        val set = LinkedHashSet<IPAddress>(2, 1.0F)
        LocalHost.tryParseIfConfig(set)
        // No assertions here as host machine running tests may not have
        println(set)
    }

    @Test
    fun givenUnixHost_whenEtcHosts_thenReturnsLocalHostIPs() {
        val set = LinkedHashSet<IPAddress>(2, 1.0F)
        LocalHost.tryParseEtcHosts(set)
        // No assertions here as host machine running tests may not have
        println(set)
    }
}
