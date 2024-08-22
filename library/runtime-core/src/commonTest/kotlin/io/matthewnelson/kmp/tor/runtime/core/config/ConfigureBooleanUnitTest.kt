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

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigureBooleanUnitTest {

    @Test
    fun givenOption_whenImplementsConfigureBoolean_thenResultingSettingIsOneOrZero() {
        TorOption.DisableNetwork.asSetting(true).let { setting ->
            assertEquals(1, setting.items.size)
            assertEquals("1", setting.items.first().argument)
        }
        TorOption.DisableNetwork.asSetting(false).let { setting ->
            assertEquals(1, setting.items.size)
            assertEquals("0", setting.items.first().argument)
        }
    }
}
