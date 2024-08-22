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
package io.matthewnelson.kmp.tor.runtime.core.config

import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.toFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigureFileUnitTest {

    @Test
    fun givenOption_whenImplementsConfigureFile_thenResultingSettingIsSanatized() {
        val setting = TorOption.GeoIPFile.asSetting("some/path/to/geoip/file/../.".toFile())
        assertEquals(1, setting.items.size)
        val argument = setting.items.first().argument
        assertTrue(argument.endsWith("some/path/to/geoip".toFile().path))
        assertTrue(argument.toFile().isAbsolute())
    }
}
