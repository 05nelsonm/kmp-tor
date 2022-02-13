/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.controller.common.config

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import kotlin.test.*

class TorConfigUnitTest {

    @Test
    fun givenTorConfigBuilder_whenBuilt_containsExpectedArguments() {
        val expectedKey = DisableNetwork()
        val expectedValue = TorF.True

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey.set(expectedValue))
        }.build()

        assertTrue(config.text.contains(expectedKey.keyword))
        assertTrue(config.text.contains(expectedValue.value))
        assertTrue(config.settings.contains(expectedKey))
        assertEquals(
            config.settings.filterIsInstance<DisableNetwork>().first().value,
            expectedValue
        )
    }

    @Test
    fun givenTorConfig_whenNewBuilder_containsExpectedArguments() {
        val expectedKey = DisableNetwork()
        val expectedValue = TorF.True

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey.set(expectedValue))
        }.build()

        val newConfig = config.newBuilder().build()

        assertEquals(config, newConfig)
        assertEquals(config.text, newConfig.text)
        assertEquals(config.settings.size, newConfig.settings.size)
        for ((i, entry) in config.settings.withIndex()) {
            assertEquals(entry, newConfig.settings.elementAt(i))
            assertEquals(entry.value, newConfig.settings.elementAt(i).value)
        }
    }

    @Test
    fun givenTorConfig_whenNewBuilderAndRemoveIfPresent_containsNewArguments() {
        val expectedKey = DisableNetwork()
        val keyToRemove = ConnectionPadding()

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey)
            put(keyToRemove)
        }.build()

        assertTrue(config.settings.contains(expectedKey))
        assertTrue(config.settings.contains(keyToRemove))

        val newConfig = config.newBuilder {
            remove(keyToRemove)
        }.build()

        assertNotEquals(config, newConfig)
        assertNotEquals(config.text, newConfig.text)
        assertNotEquals(config.settings.size, newConfig.settings.size)
        assertFalse(config.settings.filterIsInstance<ConnectionPadding>().isEmpty())
        assertTrue(newConfig.settings.filterIsInstance<ConnectionPadding>().isEmpty())
    }

    @Test
    fun givenTorConfigBuilder_whenRemoveInstanceOf_removesAllInstances() {
        val expectedRemove = DisableNetwork()
        val expectedContains = buildSet {
            add(Ports.Control())
            add(Ports.Dns())
            add(Ports.HttpTunnel())
        }

        val config = TorConfig.Builder {
            put(expectedRemove)
            put(expectedContains)
        }.build()

        assertTrue(config.settings.contains(expectedRemove))

        val newConfig = config.newBuilder {
            removeInstanceOf(expectedRemove::class)
        }.build()

        assertFalse(newConfig.settings.contains(expectedRemove))
        for (expected in expectedContains) {
            assertTrue(newConfig.settings.contains(expected))
        }
    }

    @Test
    fun givenDifferentPortTypes_whenSamePort_areEqual() {
        val http = Ports.HttpTunnel()
        val socks = Ports.Socks()
        val port = AorDorPort.Value(Port(9150))
        http.set(port)
        socks.set(port)

        assertTrue(http.equals(socks))

        val config = TorConfig.Builder {
            put(http)
            put(socks)
        }.build()

        assertEquals(1, config.settings.size)

        http.set(AorDorPort.Auto)
        assertEquals(AorDorPort.Auto, http.value)
        assertFalse(http.equals(socks))

        val newConfig = TorConfig.Builder {
            put(http)
            put(socks)
        }.build()

        assertEquals(2, newConfig.settings.size)
    }

    @Test
    fun givenMultiplePorts_whenContainsDisable_buildDoesNotInclude() {
        val tunnelPort = Ports.HttpTunnel()
        val auto = AorDorPort.Auto
        val disabled = AorDorPort.Disable
        val portValue = AorDorPort.Value(Port(9150))

        val config = TorConfig.Builder {
            put(tunnelPort.set(auto))
            put(tunnelPort.set(portValue))
        }.build()

        assertEquals(2, config.settings.size)

        assertEquals(
            "${tunnelPort.keyword} ${auto.value}\n${tunnelPort.keyword} ${portValue.value}\n",
            config.text
        )

        val newConfig = config.newBuilder {
            put(tunnelPort.set(disabled))
        }.build()

        assertEquals(1, newConfig.settings.size)
        assertEquals("${tunnelPort.keyword} ${disabled.value}\n", newConfig.text)
    }

    @Test
    fun givenTorConfig_whenTryModifySetting_settingRemainsUnchanged() {
        val tunnelPort = Ports.HttpTunnel()
        val auto = AorDorPort.Auto
        val disabled = AorDorPort.Disable
        val portValue = AorDorPort.Value(Port(9150))

        val config = TorConfig.Builder {
            put(tunnelPort.set(auto))
            put(tunnelPort.set(portValue))
        }.build()

        assertTrue(tunnelPort.isMutable)

        val setting1 = config.settings.first() as Ports.HttpTunnel
        assertEquals(setting1.value, auto)
        assertTrue(setting1.isolationFlags.isNullOrEmpty())
        assertFalse(setting1.isMutable)

        setting1
            .setIsolationFlags(setOf(
                Ports.IsolationFlag.IsolateClientAddr
            ))
            .set(disabled)
        assertEquals(setting1.value, auto)
        assertTrue(setting1.isolationFlags.isNullOrEmpty())
    }

    @Test
    fun givenSetting_whenImmutable_becomesMutableWhenCloned() {
        val tunnelPort = Ports.HttpTunnel()
        val auto = AorDorPort.Auto

        val tunnelPort2 = tunnelPort.set(auto).setImmutable().clone()
        assertFalse(tunnelPort.isMutable)
        assertTrue(tunnelPort2.isMutable)
    }

    @Test
    fun givenPortsControl_whenTrySetDisable_remainsUnchanged() {
        val ctrl = Ports.Control().set(AorDorPort.Disable)
        assertTrue(ctrl.default is AorDorPort.Auto)
        assertTrue(ctrl.isDefault)
    }

    @Test
    fun givenCacheDirectory_whenEmptyPath_remainsNull() {
        val cacheDir = CacheDirectory().set(FileSystemDir(Path("")))
        assertNull(cacheDir.value)
    }

    @Test
    fun givenCookieAuthFile_whenEmptyPath_remainsNull() {
        val cookieFile = CookieAuthFile().set(FileSystemFile(Path("")))
        assertNull(cookieFile.value)
    }

    @Test
    fun givenDataDirectory_whenEmptyPath_remainsNull() {
        val dataDir = DataDirectory().set(FileSystemDir(Path("")))
        assertNull(dataDir.value)
    }

    @Test
    fun givenDormantClientTimeout_whenLessThan10Minutes_defaultsTo10Minutes() {
        val dormant = DormantClientTimeout().set(Time.Minutes(2))
        assertEquals(10, (dormant.value as Time.Minutes).time)
    }

    @Test
    fun givenSameSettings_whenValuesDifferent_settingsStillReturnTrueWhenCompared() {
        val padding1 = ConnectionPadding().set(AorTorF.Auto)
        val padding2 = padding1.clone().set(AorTorF.False)

        // equals override compares only the keyword for that setting, so they should register as equal
        assertEquals(padding1, padding2)

        assertNotEquals(padding1.value, padding2.value)
    }

    @Test
    fun givenSamePortSettings_whenValuesDifferent_settingsAreNotEquals() {
        val control1 = Ports.Control().set(AorDorPort.Value(Port(9051)))
        val control2 = control1.clone().set(AorDorPort.Auto)

        assertNotEquals(control1, control2)
        assertNotEquals(control1.value, control2.value)
    }

    @Test
    fun givenPort_whenCloned_originalPortSettingsNotAffectedByModification() {
        val socks1 = Ports.Socks()
        socks1.set(AorDorPort.Value(Port(9150)))

        val socks2 = socks1.clone()
        socks2.set(AorDorPort.Auto)

        assertNotEquals(socks1.value, socks2.value)
    }
}
