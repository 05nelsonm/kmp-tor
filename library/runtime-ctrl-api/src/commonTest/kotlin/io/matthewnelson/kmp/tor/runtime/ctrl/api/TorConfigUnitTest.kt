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
package io.matthewnelson.kmp.tor.runtime.ctrl.api

import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.ctrl.api.builder.ExtendedTorConfigBuilder
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.toByte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalKmpTorApi::class)
class TorConfigUnitTest {

    @Test
    fun givenMultiplePortsConfigured_whenDisabled_thenAllOfThatTypeAreRemoved() {
        val settings = TorConfig.Builder {
            put(TorConfig.__ControlPort) { /* defaults to auto */ }
            put(TorConfig.__SocksPort) { asPort { auto() } }
            put(TorConfig.__SocksPort) { asPort { disable() } }
            put(TorConfig.__SocksPort) { asPort { port(9055.toPortProxy()) } }

            try {
                // Should also be removed if SocksPort is set to disabled
                put(TorConfig.__SocksPort) { asUnixSocket { file = "/some/path".toFile() } }
            } catch (_: UnsupportedOperationException) {
                // ignore
            }

            put(TorConfig.__DNSPort) { auto() }
            put(TorConfig.__DNSPort) { port(1080.toPortProxy()) }
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
        assertEquals(false.toByte().toString(), settings.first().argument)
    }

    @Test
    fun givenUniqueKeyword_whenMultipleExpressions_thenPutIfAbsentDoesNotRemoveLast() {
        val settings = TorConfig.Builder {
            put(TorConfig.RunAsDaemon) { enable = true }
            putIfAbsent(TorConfig.RunAsDaemon) { enable = false }
        }.settings

        assertEquals(1, settings.size)
        assertEquals(true.toByte().toString(), settings.first().argument)
    }

    @Test
    fun givenInheritingConfig_whenContainsDisabledPorts_thenAreRemovedAtFirstOverride() {
        val other = TorConfig.Builder {
            put(TorConfig.__SocksPort) { asPort { port(9055.toPortProxy()) } }
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

    @Test
    fun givenExtendedConfig_whenCastAs_thenWorksAsExpected() {
        TorConfig.Builder {
            val dns = TorConfig.__DNSPort.Builder { port(1080.toPortProxy()) }

            put(dns)
            put(TorConfig.__SocksPort) { asPort {} }
            put(TorConfig.HiddenServiceDir) {
                directory = "".toFile()
                version { HSv(3) }
                port {
                    virtual = 80.toPort()
                    targetAsPort { target = 443.toPort() }
                }
            }

            // contains
            assertTrue((this as ExtendedTorConfigBuilder).contains(TorConfig.__SocksPort))
            assertTrue(contains(TorConfig.HiddenServiceMaxStreams))
            assertFalse(contains(TorConfig.__ControlPort))

            // ports
            var ports = ports()
            assertTrue(ports.contains(dns))
            assertEquals(2, ports.size)

            // remove
            remove(dns)
            ports = ports()
            assertFalse(ports.contains(dns))
            assertEquals(1, ports.size)
        }
    }
}
