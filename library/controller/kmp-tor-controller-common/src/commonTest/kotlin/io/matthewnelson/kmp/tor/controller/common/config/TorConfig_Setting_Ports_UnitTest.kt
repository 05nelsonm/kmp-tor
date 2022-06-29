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
import kotlin.test.*

@Suppress("ClassName")
@OptIn(InternalTorApi::class)
class TorConfig_Setting_Ports_UnitTest {

    @Test
    fun givenDifferentPortTypes_whenSamePortValues_areEqual() {
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
    fun givenPortsControl_whenTrySetDisable_remainsUnchanged() {
        val ctrl = Ports.Control().set(AorDorPort.Disable)
        assertTrue(ctrl.default is AorDorPort.Auto)
        assertTrue(ctrl.isDefault)
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
}
