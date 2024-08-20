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
package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.ConnectionPadding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuilderScopeAutoBooleanUnitTest {

    @Test
    fun givenScope_whenConfigured_thenResultingSettingIsAsExpected() {
        listOf(
            ConnectionPadding.asSetting { auto() } to "auto",
            ConnectionPadding.asSetting { enable() } to "1",
            ConnectionPadding.asSetting { disable() } to "0",
        ).forEach { (setting, expectedArgument) ->
            assertEquals(1, setting.items.size)
            assertEquals(ConnectionPadding, setting.items.first().option)
            assertEquals(expectedArgument, setting.items.first().argument)
            assertTrue(setting.items.first().optionals.isEmpty())
        }
    }
}
