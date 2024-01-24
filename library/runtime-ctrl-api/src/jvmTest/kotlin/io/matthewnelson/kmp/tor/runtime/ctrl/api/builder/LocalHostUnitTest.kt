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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.builder

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import kotlin.test.Test
import kotlin.test.assertNotNull

// TODO: Move to commonMain
class LocalHostUnitTest {

    @Test
    fun givenIPv4_whenResolved_thenIsCached() {
        LocalHost.resolveIPv4()
        assertNotNull(LocalHost.cachedIPv4OrNull())
    }

    @Test
    fun givenIPv6_whenResolved_thenIsCached() {
        try {
            LocalHost.resolveIPv6()
        } catch (e: IOException) {
            println("IPv6 unavailable for host. Skipping...")
            return
        }

        assertNotNull(LocalHost.cachedIPv6OrNull())
    }
}
