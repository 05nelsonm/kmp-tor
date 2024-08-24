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
package io.matthewnelson.kmp.tor.runtime.core.ctrl

import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.internal.byte
import kotlin.test.Test
import kotlin.test.assertEquals

class TorCmdConfigSetUnitTest {

    @Test
    fun givenCollection_whenMultipleUniques_ConfigSetSanitizesInputWithTorConfig() {
        val set = buildSet {
            add(TorOption.DisableNetwork.asSetting(true))
            add(TorOption.DisableNetwork.asSetting(false))
        }
        assertEquals(2, set.size)

        val cmd = TorCmd.Config.Set(set)
        assertEquals(1, cmd.config.settings.size)

        // When all items in the set were added to TorConfig.BuilderScope, the
        // final argument should have overridden the first one.
        assertEquals(false.byte.toString(), cmd.config.settings.first().items.first().argument)
    }
}
