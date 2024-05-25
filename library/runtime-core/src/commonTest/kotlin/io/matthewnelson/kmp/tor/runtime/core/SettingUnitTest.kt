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

import io.matthewnelson.kmp.file.absoluteFile
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class SettingUnitTest {

    @Test
    fun givenEqualsHash_whenSameKeywordBothUniqueDifferentArguments_thenAreTheSame() {
        val a = TorConfig.RunAsDaemon.Builder { enable = true }
        val b = TorConfig.RunAsDaemon.Builder { enable = false }

        assertEquals(a, b)
        assertNotEquals(a.argument, b.argument)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenDifferentKeywordBothUniqueSameArguments_thenAreDifferent() {
        val a = TorConfig.RunAsDaemon.Builder { enable = true }
        val b = TorConfig.AutomapHostsOnResolve.Builder { enable = true }

        assertNotEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertEquals(2, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenDifferentKeywordOneUniqueSameArguments_thenAreDifferent() {
        val a = TorConfig.__DNSPort.Builder { auto() }
        val b = TorConfig.ConnectionPadding.Builder { auto() }

        assertNotEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertEquals(2, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenDifferentPortTypesSamePort_thenAreSame() {
        val port = 5555.toPortEphemeral()
        val a = TorConfig.__DNSPort.Builder { port(port) }
        val b = TorConfig.__SocksPort.Builder { asPort { port(port) } }

        assertEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenDifferentPortTypesBothDisabled_thenAreDifferent() {
        val a = TorConfig.__DNSPort.Builder { disable() }
        val b = TorConfig.__SocksPort.Builder { asPort { disable() } }

        assertNotEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertEquals(2, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenDifferentPortTypesBothAuto_thenAreDifferent() {
        val a = TorConfig.__DNSPort.Builder { auto() }
        val b = TorConfig.__SocksPort.Builder { asPort { auto() } }

        assertNotEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertEquals(2, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenSamePortTypesBothDisabled_thenAreSame() {
        val a = TorConfig.__DNSPort.Builder { disable() }
        val b = TorConfig.__DNSPort.Builder { disable() }

        assertEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenSamePortTypesBothAuto_thenAreSame() {
        val a = TorConfig.__DNSPort.Builder { auto() }
        val b = TorConfig.__DNSPort.Builder { auto() }

        assertEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenSamePortTypesOneAutoOneDisabled_thenAreDifferent() {
        val a = TorConfig.__DNSPort.Builder { auto() }
        val b = TorConfig.__DNSPort.Builder { disable() }

        assertNotEquals(a, b)
        assertNotEquals(a.argument, b.argument)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertEquals(2, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenHiddenServiceSameDirectory_thenAreSame() {
        val dir = ".".toFile()
        val a = TorConfig.HiddenServiceDir.Builder {
            directory = dir
            port { virtual = 80.toPort() }
            port { virtual = 443.toPort() }
            version { HSv(3) }
        }!!
        val b = TorConfig.HiddenServiceDir.Builder {
            directory = dir
            port { virtual = 80.toPort() }
            version { HSv(3) }
        }!!

        assertEquals(a, b)
        assertEquals(a.argument, b.argument)
        assertNotEquals(a.items, b.items)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(1, setOf(a, b).size)
    }

    @Test
    fun givenEqualsHash_whenHiddenServiceDifferentDirectory_thenAreDifferent() {
        val a = TorConfig.HiddenServiceDir.Builder {
            directory = ".".toFile()
            port { virtual = 80.toPort() }
            port { virtual = 443.toPort() }
            version { HSv(3) }
        }!!
        val b = TorConfig.HiddenServiceDir.Builder {
            directory = "/some/random/path".toFile()
            port { virtual = 80.toPort() }
            version { HSv(3) }
        }!!

        assertNotEquals(a, b)
        assertNotEquals(a.argument, b.argument)
        assertNotEquals(a.items, b.items)
        assertNotEquals(a.hashCode(), b.hashCode())
        assertEquals(2, setOf(a, b).size)
    }

    @Test
    fun givenTCPPort_whenNotConfiguredWithPortArgument_thenValidationReturnsNull() {
        TorConfig.__DNSPort.Builder { disable() }.let { dns ->
            assertNull(dns.reassignTCPPortAutoOrNull())
        }
        TorConfig.__SocksPort.Builder {
            // Will check functionality on non-windows hosts
            try {
                asUnixSocket {
                    file = "".toFile().absoluteFile
                        .resolve("socks.sock")
                }
            } catch (_: UnsupportedOperationException) {
                asPort { disable() }
            }
        }.let { socks ->
            assertNull(socks.reassignTCPPortAutoOrNull())
        }
        TorConfig.__ControlPort.Builder { asPort { auto() } }.let { ctrl ->
            assertNull(ctrl.reassignTCPPortAutoOrNull())
        }
    }

    @Test
    fun givenTCPPort_whenConfiguredWithPortArgument_thenReassignsToAuto() {
        TorConfig.__SocksPort.Builder { asPort { /* 9050 */ } }.let { socks ->
            assertNotNull(socks.reassignTCPPortAutoOrNull())
        }
    }

    @Test
    fun givenNonPortSetting_whenValidate_thenReturnsNull() {
        TorConfig.RunAsDaemon.Builder { enable = true }.let { setting ->
            assertNull(setting.reassignTCPPortAutoOrNull())
        }
    }
}
