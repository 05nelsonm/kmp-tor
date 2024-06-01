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
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.core.internal.byte
import kotlin.test.Test
import kotlin.test.assertEquals

class TorConfigUnitTest {

    @Test
    fun givenMultiplePortsConfigured_whenDisabled_thenAllOfThatTypeAreRemoved() {
        val settings = TorConfig.Builder {
            put(TorConfig.__ControlPort) { /* defaults to auto */ }
            put(TorConfig.__SocksPort) { asPort { auto() } }
            put(TorConfig.__SocksPort) { asPort { disable() } }
            put(TorConfig.__SocksPort) { asPort { port(9055.toPortEphemeral()) } }

            try {
                // Should also be removed if SocksPort is set to disabled
                put(TorConfig.__SocksPort) { asUnixSocket { file = "/some/path".toFile() } }
            } catch (_: UnsupportedOperationException) {
                // ignore
            }

            put(TorConfig.__DNSPort) { auto() }
            put(TorConfig.__DNSPort) { port(1080.toPortEphemeral()) }
        }.settings

        assertEquals(1, settings.filterByKeyword<TorConfig.__ControlPort.Companion>().size)
        assertEquals(2, settings.filterByKeyword<TorConfig.__DNSPort.Companion>().size)

        val socksSettings = settings.filterByKeyword<TorConfig.__SocksPort.Companion>()
        assertEquals(1, socksSettings.size)
        assertEquals("0", socksSettings.first().argument)
    }

    @Test
    fun givenUniqueKeyword_whenMultipleExpressions_thenPutRemovesLast() {
        val settings = TorConfig.Builder {
            put(TorConfig.RunAsDaemon) { enable = true }
            put(TorConfig.RunAsDaemon) { enable = false }
        }.settings

        assertEquals(1, settings.size)
        assertEquals(false.byte.toString(), settings.first().argument)
    }

    @Test
    fun givenUniqueKeyword_whenMultipleExpressions_thenPutIfAbsentDoesNotRemoveLast() {
        val settings = TorConfig.Builder {
            put(TorConfig.RunAsDaemon) { enable = true }
            putIfAbsent(TorConfig.RunAsDaemon) { enable = false }
        }.settings

        assertEquals(1, settings.size)
        assertEquals(true.byte.toString(), settings.first().argument)
    }

    @Test
    fun givenInheritingConfig_whenContainsDisabledPorts_thenAreRemovedAtFirstOverride() {
        val other = TorConfig.Builder {
            put(TorConfig.__SocksPort) { asPort { port(9055.toPortEphemeral()) } }
            put(TorConfig.__SocksPort) { asPort { disable() } }
            put(TorConfig.__HTTPTunnelPort) { disable() }
            put(TorConfig.__DNSPort) { auto() }
        }

        assertEquals(3, other.settings.size)

        val settings1 = TorConfig.Builder(other) {}.settings
        assertEquals(3, settings1.size)
        assertEquals("0", settings1.filterByKeyword<TorConfig.__SocksPort.Companion>().first().argument)
        assertEquals("0", settings1.filterByKeyword<TorConfig.__HTTPTunnelPort.Companion>().first().argument)

        val settings2 = TorConfig.Builder(other) {
            put(TorConfig.__SocksPort) { asPort { auto() } }
        }.settings

        assertEquals(3, settings2.size)
        assertEquals("auto", settings2.filterByKeyword<TorConfig.__SocksPort.Companion>().first().argument)
        assertEquals("0", settings2.filterByKeyword<TorConfig.__HTTPTunnelPort.Companion>().first().argument)
    }
}
