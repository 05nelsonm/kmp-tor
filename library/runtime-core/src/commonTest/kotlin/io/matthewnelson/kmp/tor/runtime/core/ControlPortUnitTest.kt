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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ControlPortUnitTest {

    @Test
    fun givenTCPPortConfiguration_whenUnixFlags_thenAreNotAddedAsOptionals() {
        val setting = TorConfig.__ControlPort.Builder {
            unixFlags { GroupWritable = true }
        }

        assertEquals("auto", setting.argument)
        assertEquals(0, setting.optionals.size)
        assertNull(setting[TorConfig.Extra.AllowReassign])
    }

    @Test
    fun givenTCPPortConfiguration_whenReassignFalse_thenNoExtras() {
        val setting = TorConfig.__ControlPort.Builder {
            asPort {
                port(9055.toPortEphemeral())
                reassignable(allow = false)
            }
        }

        assertNull(setting[TorConfig.Extra.AllowReassign])
    }

    @Test
    fun givenTCPPortConfiguration_whenReassignTrue_thenExtras() {
        val setting = TorConfig.__ControlPort.Builder {
            asPort {
                port(9055.toPortEphemeral())
                reassignable(allow = true)
            }
        }

        assertEquals(true, setting[TorConfig.Extra.AllowReassign])
    }
}
