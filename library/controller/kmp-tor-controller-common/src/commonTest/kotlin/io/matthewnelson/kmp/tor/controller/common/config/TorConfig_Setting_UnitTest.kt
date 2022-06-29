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
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import kotlin.test.*

@Suppress("ClassName")
@OptIn(InternalTorApi::class)
class TorConfig_Setting_UnitTest {

    @Test
    fun givenTorConfigSetting_whenImmutable_becomesMutableWhenCloned() {
        val tunnelPort = Ports.HttpTunnel()
        val auto = AorDorPort.Auto

        val tunnelPort2 = tunnelPort.set(auto).setImmutable().clone()
        assertFalse(tunnelPort.isMutable)
        assertTrue(tunnelPort2.isMutable)
    }

    @Test
    fun givenSameSettings_whenValuesDifferent_settingsStillReturnTrueWhenCompared() {
        val padding1 = ConnectionPadding().set(AorTorF.Auto)
        val padding2 = padding1.clone().set(AorTorF.False)

        // equals override compares only the keyword for that setting, so they should register as equal
        assertEquals(padding1, padding2)

        assertNotEquals(padding1.value, padding2.value)
    }

    @Test
    fun givenCacheDirectory_whenEmptyPath_remainsNull() {
        val setting = CacheDirectory().set(FileSystemDir(Path("")))
        assertNull(setting.value)
    }

    @Test
    fun givenClientOnionAuthDir_whenEmptyPath_remainsNull() {
        val setting = ClientOnionAuthDir().set(FileSystemDir(Path("")))
        assertNull(setting.value)
    }

    @Test
    fun givenControlPortWriteToFile_whenEmptyPath_remainsNull() {
        val setting = ControlPortWriteToFile().set(FileSystemFile(Path("")))
        assertNull(setting.value)
    }

    @Test
    fun givenCookieAuthFile_whenEmptyPath_remainsNull() {
        val setting = CookieAuthFile().set(FileSystemFile(Path("")))
        assertNull(setting.value)
    }

    @Test
    fun givenDataDirectory_whenEmptyPath_remainsNull() {
        val setting = DataDirectory().set(FileSystemDir(Path("")))
        assertNull(setting.value)
    }

    @Test
    fun givenDormantClientTimeout_whenLessThan10Minutes_defaultsTo10Minutes() {
        val dormant = DormantClientTimeout().set(Time.Minutes(2))
        assertEquals(10, (dormant.value as Time.Minutes).time)
    }

    @Test
    fun givenGeoIpV4File_whenEmptyPath_remainsNull() {
        val setting = GeoIpV4File().set(FileSystemFile(Path("")))
        assertNull(setting.value)
    }

    @Test
    fun givenGeoIpV6File_whenEmptyPath_remainsNull() {
        val setting = GeoIpV6File().set(FileSystemFile(Path("")))
        assertNull(setting.value)
    }
}
