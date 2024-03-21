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
package io.matthewnelson.kmp.tor.runtime.core

import kotlin.test.Test
import kotlin.test.assertEquals

class VirtualAddrUnitTest {

    @Test
    fun givenIPv4_whenBitsMisconfigured_thenUsesDefault() {
        var argument = TorConfig.VirtualAddrNetworkIPv4.Builder { bits = 16 }.argument
        assertEquals("127.192.0.0/16", argument)

        argument = TorConfig.VirtualAddrNetworkIPv4.Builder { bits = 0 }.argument
        assertEquals("127.192.0.0/0", argument)

        argument = TorConfig.VirtualAddrNetworkIPv4.Builder { bits = 17 }.argument
        assertEquals("127.192.0.0/10", argument)

        argument = TorConfig.VirtualAddrNetworkIPv4.Builder { bits = -1 }.argument
        assertEquals("127.192.0.0/10", argument)
    }

    @Test
    fun givenIPv6_whenBitsMisconfigured_thenUsesDefault() {
        var argument = TorConfig.VirtualAddrNetworkIPv6.Builder { bits = 104 }.argument
        assertEquals("[FE80::]/104", argument)

        argument = TorConfig.VirtualAddrNetworkIPv6.Builder { bits = 0 }.argument
        assertEquals("[FE80::]/0", argument)

        argument = TorConfig.VirtualAddrNetworkIPv6.Builder { bits = 105 }.argument
        assertEquals("[FE80::]/10", argument)

        argument = TorConfig.VirtualAddrNetworkIPv6.Builder { bits = -1 }.argument
        assertEquals("[FE80::]/10", argument)
    }
}
