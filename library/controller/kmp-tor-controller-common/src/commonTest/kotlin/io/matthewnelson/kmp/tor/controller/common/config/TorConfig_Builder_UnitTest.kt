/*
 * Copyright (c) 2022 Matthew Nelson
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

import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.appendTo
import kotlin.test.*

@Suppress("ClassName")
@OptIn(InternalTorApi::class)
class TorConfig_Builder_UnitTest {

    @Test
    fun givenBuilder_whenBuild_containsExpectedArguments() {
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
    fun givenBuilder_whenRemove_removesArgument() {
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
    fun givenBuilder_whenRemoveInstanceOf_removesAllInstances() {
        val expectedRemove = Ports.Control()
        val expectedContains = buildSet {
            add(Ports.Dns())
            add(Ports.HttpTunnel())
        }

        val config = TorConfig.Builder {
            put(expectedRemove.set(AorDorPort.Value(PortProxy(9051))))
            put(expectedRemove.set(AorDorPort.Value(PortProxy(9052))))
            put(expectedRemove.set(AorDorPort.Value(PortProxy(9053))))
            put(expectedRemove.set(AorDorPort.Value(PortProxy(9054))))
            put(expectedContains)
        }.build()

        assertEquals(4, config.settings.filterIsInstance<Ports.Control>().size)
        assertEquals(6, config.settings.size)

        val newConfig = config.newBuilder {
            removeInstanceOf(expectedRemove::class)
        }.build()

        assertEquals(0, newConfig.settings.filterIsInstance<Ports.Control>().size)
        assertEquals(2, newConfig.settings.size)

        for (expected in expectedContains) {
            assertTrue(newConfig.settings.contains(expected))
        }
    }

    @Test
    fun givenBuilder_whenSameSettingPut_replacesOldSetting() {
        val expected = TorF.True
        val disableNetwork = DisableNetwork().set(TorF.False)
        val config = TorConfig.Builder {
            put(disableNetwork)
            put(disableNetwork.set(expected))
        }.build()

        assertEquals(1, config.settings.size)
        assertEquals(expected, config.settings.first().value)
    }

    @Test
    fun givenBuilder_whenSameSettingPutIfAbsent_doesNotReplaceOldSetting() {
        val expected = TorF.True
        val disableNetwork = DisableNetwork()
        val config = TorConfig.Builder {
            put(disableNetwork)
            putIfAbsent(disableNetwork.set(expected))
        }.build()

        assertEquals(1, config.settings.size)
        assertNotEquals(expected, config.settings.first().value)
    }

    @Test
    fun givenBuilder_whenContainsDisabledPorts_buildDoesNotInclude() {
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
    fun givenSetting_whenAppendingToStringBuilderFalse_doesNotAddSettingToTorConfig() {
        // By not calling .setPorts, our appendTo method should _not_ append anything
        // to our string builder
        val hsSetting = HiddenService().set(FileSystemDir(Path("/some/path")))
        assertFalse(hsSetting.appendTo(StringBuilder(), isWriteTorConfig = true))
        assertFalse(hsSetting.appendTo(StringBuilder(), isWriteTorConfig = false))

        val config = TorConfig.Builder {
            put(hsSetting)
        }.build()

        assertTrue(config.settings.isEmpty())
        assertTrue(config.text.isEmpty())
    }
}
