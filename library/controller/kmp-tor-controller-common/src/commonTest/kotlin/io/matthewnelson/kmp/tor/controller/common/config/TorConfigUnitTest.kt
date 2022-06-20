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
import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.ControllerUtils
import kotlin.test.*

@OptIn(InternalTorApi::class)
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
    fun givenTorConfigBuilder_whenSameSettingPut_replacesOldSetting() {
        val expected = TorF.True
        val disableNetwork = DisableNetwork().set(TorF.False)
        val config = TorConfig.Builder {
            put(disableNetwork)
            put(disableNetwork.set(expected))
        }.build()

        assertEquals(expected, config.settings.first().value)
    }

    @Test
    fun givenTorConfigBuilder_whenSameSettingPutIfAbsent_doesNotReplaceOldSetting() {
        val expected = TorF.True
        val disableNetwork = DisableNetwork().set(TorF.False)
        val config = TorConfig.Builder {
            put(disableNetwork)
            putIfAbsent(disableNetwork.set(expected))
        }.build()

        assertNotEquals(expected, config.settings.first().value)
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
        val port = AorDorPort.Value(PortProxy(9150))
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
        val portValue = AorDorPort.Value(PortProxy(9150))

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
        val portValue = AorDorPort.Value(PortProxy(9150))

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
        val control1 = Ports.Control().set(AorDorPort.Value(PortProxy(9051)))
        val control2 = control1.clone().set(AorDorPort.Auto)

        assertNotEquals(control1, control2)
        assertNotEquals(control1.value, control2.value)
    }

    @Test
    fun givenPort_whenCloned_originalPortSettingsNotAffectedByModification() {
        val socks1 = Ports.Socks()
        socks1.set(AorDorPort.Value(PortProxy(9150)))

        val socks2 = socks1.clone()
        socks2.set(AorDorPort.Auto)

        assertNotEquals(socks1.value, socks2.value)
    }

    @Test
    fun givenHiddenService_whenSameDir_settingsAreEqual() {
        val path = Path("/some_dir")

        val (hs1, hs2) = Pair(HiddenService(), HiddenService())

        hs1.setPorts(setOf(HiddenService.Ports(Port(11))))
        hs2.setPorts(setOf(HiddenService.Ports(Port(22))))

        hs1.setMaxStreams(HiddenService.MaxStreams(1))
        hs2.setMaxStreams(HiddenService.MaxStreams(2))

        hs1.setMaxStreamsCloseCircuit(TorF.True)

        hs1.set(FileSystemDir(path))
        hs2.set(FileSystemDir(path))

        assertNotEquals(hs1.maxStreams, hs2.maxStreams)
        assertNotEquals(hs1.maxStreamsCloseCircuit, hs2.maxStreamsCloseCircuit)
        assertNotEquals(hs1.ports, hs2.ports)

        assertEquals(hs1, hs2)
    }

    @Test
    fun givenHiddenService_whenCloned_matchesOriginal() {
        val expectedHs = HiddenService()
        val expectedDir = FileSystemDir(Path("/some_dir"))
        val expectedPorts = setOf(HiddenService.Ports(Port(11)))
        val expectedMaxStreams = HiddenService.MaxStreams(1)
        val expectedMaxStreamsCloseCircuit = TorF.True

        expectedHs.set(expectedDir)
        expectedHs.setPorts(expectedPorts)
        expectedHs.setMaxStreams(expectedMaxStreams)
        expectedHs.setMaxStreamsCloseCircuit(expectedMaxStreamsCloseCircuit)

        val actual = expectedHs.clone()
        assertEquals(expectedDir, actual.value)
        assertEquals(expectedPorts, actual.ports)
        assertEquals(expectedMaxStreams, actual.maxStreams)
        assertEquals(expectedMaxStreamsCloseCircuit, actual.maxStreamsCloseCircuit)
    }

    @Test
    fun givenHiddenServicePorts_whenMultiplePortsWithSameVirtPort_onlyOneIsUsed() {
        val set = mutableSetOf<HiddenService.Ports>()
        val expected = HiddenService.Ports(virtualPort = Port(80))
        set.add(expected)
        set.add(HiddenService.Ports(virtualPort = Port(80), targetPort = Port(12345)))

        assertEquals(1, set.size)
        assertEquals(expected, set.first())
    }

    @Test
    fun givenUnixSocket_whenPathsSame_equalsEachOther() {
        // Only run if support for domain sockets is had
        if (!ControllerUtils.hasUnixDomainSocketSupport) return

        val control1 = UnixSocket.Control()
        control1.set(FileSystemFile(Path("/some/path")))

        val control2 = control1.clone()
        control2.setFlags(setOf(
            UnixSocket.Control.Flag.GroupWritable
        ))

        assertEquals(control1, control2)

        val set = mutableSetOf<UnixSocket.Control>()
        set.add(control1)
        set.add(control2)

        assertEquals(1, set.size)
    }

    @Test
    fun givenUnixSocketControl_whenPathIntegerOrEmpty_valueNotSet() {
        // Only run if support for domain sockets is had
        if (!ControllerUtils.hasUnixDomainSocketSupport) return

        val control = UnixSocket.Control()
        control.set(FileSystemFile(Path("0")))
        assertNull(control.value)

        control.set(FileSystemFile(Path("9051")))
        assertNull(control.value)

        control.set(FileSystemFile(Path("")))
        assertNull(control.value)
    }

    @Test
    fun givenUnixSocketControl_whenCloned_matchesOriginal() {
        // Only run if support for domain sockets is had
        if (!ControllerUtils.hasUnixDomainSocketSupport) return

        val control = UnixSocket.Control()
        control.set(FileSystemFile(Path("/some/path")))
        control.setFlags(setOf(
            UnixSocket.Control.Flag.WorldWritable
        ))

        val clone = control.clone()

        assertNotNull(control.value)
        assertEquals(control.value, clone.value)
        assertEquals(control.flags, clone.flags)
    }

}
