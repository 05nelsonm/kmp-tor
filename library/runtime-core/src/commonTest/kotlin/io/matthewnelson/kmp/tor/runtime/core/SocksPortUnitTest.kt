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
import kotlin.test.assertNull

class SocksPortUnitTest {

    @Test
    fun givenTCPPortConfiguration_whenUnixFlags_thenAreNotAddedAsOptionals() {
        val setting = TorConfig.__SocksPort.Builder {
            unixFlags { GroupWritable = true }
        }

        assertEquals("9050", setting.argument)
        assertEquals(0, setting.optionals.size)
        assertEquals(true, setting[TorConfig.Extra.AllowReassign])
    }

    @Test
    fun givenTCPPortConfiguration_whenReassignFalse_thenNoExtras() {
        val setting = TorConfig.__SocksPort.Builder {
            asPort {
                reassignable(allow = false)
            }
        }

        assertNull(setting[TorConfig.Extra.AllowReassign])
    }
}
