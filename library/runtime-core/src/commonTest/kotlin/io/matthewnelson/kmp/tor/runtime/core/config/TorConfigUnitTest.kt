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
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorConfigUnitTest {

    @Test
    fun givenUniqueOption_whenMultipleConfigured_thenIsReplacedWithLatest() {
        val expected = "/some/path/2".toFile()

        val settings = TorConfig2.Builder {
            CacheDirectory.configure("/some/other/path".toFile())

            DataDirectory.configure("/some/path/data".toFile())

            CacheDirectory.configure(expected)
        }.settings

        assertEquals(2, settings.size)
        // Should still be the first element in underlying map
        assertEquals(expected.path, settings.first().items.first().argument)
    }

    @Test
    fun givenNonUniqueOption_whenMultipleConfiguredDifferentArgs_thenIsNotReplaced() {
        val settings = TorConfig2.Builder {
            SocksPort.configure { port(9050.toPortEphemeral()) }
            SocksPort.configure { port(9055.toPortEphemeral()) }
        }.settings

        assertEquals(2, settings.size)
    }

    @Test
    fun givenNonUniqueOption_whenMultipleConfiguredSameArgs_thenIsReplaced() {
        val port = 9055.toPortEphemeral()

        val settings = TorConfig2.Builder {
            SocksPort.configure { port(9050.toPortEphemeral()) }
            SocksPort.configure { port(port) }

            DNSPort.configure { port(port) }

            SocksPort.configure {
                port(port)
                flagsSocks { OnionTrafficOnly = true }
            }
        }.settings

        assertEquals(3, settings.size)

        // Should still be 2nd element in underlying map
        val setting = settings.elementAt(1)
        assertTrue(setting.items.first().optionals.contains("OnionTrafficOnly"))
    }
}
