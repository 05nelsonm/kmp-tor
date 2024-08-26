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

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.Companion.toIPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddressV4UnitTest.Companion.TEST_ADDRESSES_IPV4
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddressV6UnitTest.Companion.TEST_ADDRESSES_IPV6
import kotlin.test.Test
import kotlin.test.assertIs

class IPAddressUnitTest {

    @Test
    fun givenIPAddress_whenIPv4_thenIsIPAddressV4() {
        for (ipv4 in TEST_ADDRESSES_IPV4.lines()) {
            assertIs<IPAddress.V4>("http://$ipv4:5050/some/path".toIPAddress())
        }
    }

    @Test
    fun givenIPAddress_whenIPv6_thenIsIPAddressV6() {
        for (ipv6 in TEST_ADDRESSES_IPV6.lines()) {
            assertIs<IPAddress.V6>("http://[$ipv6]:1023/some/path".toIPAddress())
        }
    }
}
