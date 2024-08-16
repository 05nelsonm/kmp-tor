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

import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPort
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class HSDirUnitTest {

    @Test
    fun givenSetting_whenConfigured_thenFirstItemIsHiddenServiceDir() {
        val setting = TorConfig.HiddenServiceDir.Builder {
            directory = "/some/path".toFile()
            port { virtual = Port.HTTP }
            version { HSv(3) }
        }!!

        assertIs<TorConfig.HiddenServiceDir.Companion>(setting.keyword)
    }

    @Test
    fun givenBuilder_whenNoDirectory_thenReturnsNull() {
        val setting = TorConfig.HiddenServiceDir.Builder {
//            directory = "/some/path".toFile()
            port { virtual = 80.toPort() }
            version { HSv(3) }
        }

        assertNull(setting)
    }

    @Test
    fun givenBuilder_whenNoPort_thenReturnsNull() {
        val setting = TorConfig.HiddenServiceDir.Builder {
            directory = "/some/path".toFile()
//            port { virtual = Port.HTTP }
            version { HSv(3) }
        }

        assertNull(setting)
    }

    @Test
    fun givenBuilder_whenNoVersion_thenReturnsNull() {
        val setting = TorConfig.HiddenServiceDir.Builder {
            directory = "/some/path".toFile()
            port { virtual = 80.toPort() }
//            version { HSv(3) }
        }

        assertNull(setting)
    }
}
