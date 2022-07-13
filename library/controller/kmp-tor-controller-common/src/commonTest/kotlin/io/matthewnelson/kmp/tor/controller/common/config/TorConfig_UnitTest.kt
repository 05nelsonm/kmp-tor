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

import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import kotlin.test.*

@Suppress("ClassName")
@OptIn(InternalTorApi::class)
class TorConfig_UnitTest {

    @Test
    fun given_whenNewBuilder_containsExpectedArguments() {
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
    fun given_whenTryModifySetting_settingRemainsUnchanged() {
        val tunnelPort = Ports.HttpTunnel()
        val expected1 = AorDorPort.Auto
        val expected2 = AorDorPort.Value(PortProxy(9150))
        val notExpected = AorDorPort.Disable

        val config = TorConfig.Builder {
            put(tunnelPort.set(expected1))
            put(tunnelPort.set(expected2))
        }.build()

        val setting1 = config.settings.elementAt(0) as Ports.HttpTunnel
        assertEquals(setting1.value, expected1)
        assertTrue(setting1.isolationFlags.isNullOrEmpty())
        assertFalse(setting1.isMutable)

        setting1
            .setIsolationFlags(setOf(
                Ports.IsolationFlag.IsolateClientAddr
            ))
            .set(notExpected)
        assertEquals(setting1.value, expected1)
        assertTrue(setting1.isolationFlags.isNullOrEmpty())

        val setting2 = config.settings.elementAt(1) as Ports.HttpTunnel
        assertEquals(setting2.value, expected2)
        assertTrue(setting2.isolationFlags.isNullOrEmpty())
        assertFalse(setting2.isMutable)

        setting2
            .setIsolationFlags(setOf(
                Ports.IsolationFlag.IsolateClientAddr
            ))
            .set(notExpected)
        assertEquals(setting2.value, expected2)
        assertTrue(setting2.isolationFlags.isNullOrEmpty())
    }
}
