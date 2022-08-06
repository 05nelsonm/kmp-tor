/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.controller.common.config

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("ClassName")
@OptIn(InternalTorApi::class)
class TorConfig_Setting_VirtualAddr_UnitTest {

    @Test
    fun givenVirtualAddrNetworkIPv4_whenInstantiated_defaultSet() {
        val setting = TorConfig.Setting.VirtualAddrNetworkIPv4()
        assertTrue(setting.isDefault)
        assertEquals("127.192.0.0/10", setting.value.value)
    }

    @Test
    fun givenVirtualAddrNetworkIPv6_whenInstantiated_defaultSet() {
        val setting = TorConfig.Setting.VirtualAddrNetworkIPv6()
        assertTrue(setting.isDefault)
        assertEquals("[FE80::]/10", setting.value.value)
    }
}
