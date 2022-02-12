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
import io.matthewnelson.kmp.tor.controller.common.file.Path
import kotlin.test.*

class TorConfigUnitTest {

    @Test
    fun givenTorConfigBuilder_whenBuilt_containsExpectedArguments() {
        val expectedKey = TorConfig.Setting.DisableNetwork()
        val expectedValue = TorConfig.Option.TorF.True

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey.set(expectedValue))
        }.build()

        assertTrue(config.text.contains(expectedKey.keyword))
        assertTrue(config.text.contains(expectedValue.value))
        assertTrue(config.settings.containsKey(expectedKey))
        assertEquals(config.settings[expectedKey], expectedValue)
    }

    @Test
    fun givenTorConfig_whenNewBuilder_containsExpectedArguments() {
        val expectedKey = TorConfig.Setting.DisableNetwork()
        val expectedValue = TorConfig.Option.TorF.True

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey.set(expectedValue))
        }.build()

        val newConfig = config.newBuilder().build()

        assertEquals(config, newConfig)
        assertEquals(config.text, newConfig.text)
        assertEquals(config.settings.size, newConfig.settings.size)
        for (entry in config.settings.entries) {
            assertEquals(entry.value, newConfig.settings[entry.key])
        }
    }

    @Test
    fun givenTorConfig_whenNewBuilderAndUpdated_containsNewArguments() {
        val expectedKey = TorConfig.Setting.DisableNetwork()
        val initialValue = TorConfig.Option.TorF.True
        val expectedValue = TorConfig.Option.TorF.False

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey.set(initialValue))
        }.build()

        assertEquals(config.settings[expectedKey], initialValue)

        val updatedConfig = config.newBuilder {
            updateIfPresent(TorConfig.Setting.DisableNetwork::class) {
                set(expectedValue)
            }
        }.build()

        assertNotEquals(config, updatedConfig)
        assertNotEquals(config.text, updatedConfig.text)
        assertEquals(config.settings.size, updatedConfig.settings.size)
        assertNotEquals(config.settings[expectedKey], updatedConfig.settings[expectedKey])
    }

    @Test
    fun givenTorConfig_whenNewBuilderAndUpdatedNonExistentSetting_containsSameArguments() {
        val expectedKey = TorConfig.Setting.DisableNetwork()
        val initialValue = TorConfig.Option.TorF.True
        val expectedValue = TorConfig.Option.TorF.False

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey.set(initialValue))
        }.build()

        val newConfig = config.newBuilder {
            updateIfPresent(TorConfig.Setting.ConnectionPadding::class) {
                set(expectedValue)
            }
        }.build()

        assertEquals(config, newConfig)
        assertEquals(config.text, newConfig.text)
        assertEquals(config.settings.size, newConfig.settings.size)
        for (entry in config.settings.entries) {
            assertEquals(entry.value, newConfig.settings[entry.key])
        }
    }

    @Test
    fun givenTorConfig_whenNewBuilderAndRemoveIfPresent_containsNewArguments() {
        val expectedKey = TorConfig.Setting.DisableNetwork()
        val keyToRemove = TorConfig.Setting.ConnectionPadding()

        val config: TorConfig = TorConfig.Builder {
            put(expectedKey)
            put(keyToRemove)
        }.build()

        assertTrue(config.settings.containsKey(expectedKey))
        assertTrue(config.settings.containsKey(keyToRemove))

        val newConfig = config.newBuilder {
            remove(keyToRemove)
        }.build()

        assertNotEquals(config, newConfig)
        assertNotEquals(config.text, newConfig.text)
        assertNotEquals(config.settings.size, newConfig.settings.size)
        assertNotNull(config.settings[keyToRemove])
        assertNull(newConfig.settings[keyToRemove])
    }

    @Test
    fun givenTorConfigBuilder_whenRemoveInstanceOf_removesAllInstances() {
        val expectedRemove = TorConfig.Setting.DisableNetwork()
        val expectedContains = buildSet {
            add(TorConfig.Setting.Ports.Control())
            add(TorConfig.Setting.Ports.Dns())
            add(TorConfig.Setting.Ports.HttpTunnel())
        }

        val config = TorConfig.Builder {
            put(expectedRemove)
            put(expectedContains)
        }.build()

        assertTrue(config.settings.containsKey(expectedRemove))

        val newConfig = config.newBuilder {
            removeInstanceOf(expectedRemove::class)
        }.build()

        assertFalse(newConfig.settings.containsKey(expectedRemove))
        for (expected in expectedContains) {
            assertTrue(newConfig.settings.containsKey(expected))
        }
    }

    @Test
    fun givenKeyWordPortControl_whenTrySetDisable_remainsUnchanged() {
        val ctrl = TorConfig.Setting.Ports.Control().set(TorConfig.Option.AorDorPort.Disable)
        assertTrue(ctrl.default is TorConfig.Option.AorDorPort.Auto)
        assertTrue(ctrl.isDefault)
    }

    @Test
    fun givenCacheDirectory_whenEmptyPath_remainsNull() {
        val cacheDir = TorConfig.Setting.CacheDirectory().set(TorConfig.Option.FileSystemDir(Path("")))
        assertNull(cacheDir.value)
    }

    @Test
    fun givenCookieAuthFile_whenEmptyPath_remainsNull() {
        val cookieFile = TorConfig.Setting.CookieAuthFile().set(TorConfig.Option.FileSystemFile(Path("")))
        assertNull(cookieFile.value)
    }

    @Test
    fun givenDataDirectory_whenEmptyPath_remainsNull() {
        val dataDir = TorConfig.Setting.DataDirectory().set(TorConfig.Option.FileSystemDir(Path("")))
        assertNull(dataDir.value)
    }

    @Test
    fun givenDormantClientTimeout_whenLessThan10Minutes_defaultsTo10Minutes() {
        val dormant = TorConfig.Setting.DormantClientTimeout().set(TorConfig.Option.Time.Minutes(2))
        assertEquals(10, (dormant.value as TorConfig.Option.Time.Minutes).time)
    }

    @Test
    fun givenSameSettings_whenValuesDifferent_settingsStillReturnTrueWhenCompared() {
        val control1 = TorConfig.Setting.Ports.Control().set(TorConfig.Option.AorDorPort.Value(Port(9051)))
        val control2 = TorConfig.Setting.Ports.Control()

        // equals override compares only the keyword for that setting, so they should register as equal
        assertEquals(control1, control2)

        assertNotEquals(control1.value, control2.value)
    }

    @Test
    fun givenPort_whenCloned_originalPortSettingsNotAffectedByModification() {
        val socks1 = TorConfig.Setting.Ports.Socks()
        socks1.set(TorConfig.Option.AorDorPort.Value(Port(9150)))

        val socks2 = socks1.clone()
        socks2.set(TorConfig.Option.AorDorPort.Auto)

        assertNotEquals(socks1.value, socks2.value)
    }
}
