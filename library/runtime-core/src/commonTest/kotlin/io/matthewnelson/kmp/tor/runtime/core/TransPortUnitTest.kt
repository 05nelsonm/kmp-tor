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

import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.internal.IsUnixLikeHost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TransPortUnitTest {

    @Test
    fun givenHostMachine_whenConfigured_thenAllowsIfSupported() {
        val setting = TorConfig.__TransPort.Builder {
            port(1080.toPortEphemeral())
        }

        val argument = setting.argument

        println("IsUnixLikeHost[$IsUnixLikeHost]")
        println("__TransPort $argument")

        if (IsUnixLikeHost) {
            assertEquals("1080", argument)
            assertEquals(true, setting[TorConfig.Extra.AllowReassign])
        } else {
            assertEquals("0", argument)
            assertNull(setting[TorConfig.Extra.AllowReassign])
        }
    }
}
