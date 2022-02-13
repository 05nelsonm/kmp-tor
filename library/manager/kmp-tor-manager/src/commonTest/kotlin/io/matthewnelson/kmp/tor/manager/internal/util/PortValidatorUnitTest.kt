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
package io.matthewnelson.kmp.tor.manager.internal.util

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.Ports
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.AorDorPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PortValidatorUnitTest {

    private val validator = PortValidator()

    @Test
    fun givenDifferentConfigPortType_whenHasSamePorts_setsToAuto() {
        val port = Port(9150)
        val socks = Ports.Socks()
        socks.set(AorDorPort.Value(port))
        val http = Ports.HttpTunnel()
        http.set(AorDorPort.Value(port))

        TorConfig.Builder {
            put(socks)
            put(http)
        }

        validator.add(socks)
        validator.add(http)

        val validated = validator.validate { true }

        assertEquals(port, (validated.first().value as AorDorPort.Value).port)
        assertEquals(AorDorPort.Auto, validated.elementAt(1).value)
    }

    @Test
    fun givenNoSocksPort_whenValidationCalled_socksPortIsAddedAndValidated() {
        validator.add(Ports.HttpTunnel())
        val validated = validator.validate { true }
        val socks = validated.filterIsInstance<Ports.Socks>()
        assertEquals(1, socks.size)
        assertEquals(Ports.Socks(), socks.first())
    }

    @Test
    fun givenNoSocksPort_when9050TakenAndValidationCalled_socksPortAutoIsAdded() {
        val http = Ports.HttpTunnel()
        val socks = Ports.Socks()
        http.set(socks.default)

        validator.add(http)
        val validated = validator.validate { true }
        val socksInstance = validated.filterIsInstance<Ports.Socks>()
        assertEquals(AorDorPort.Auto, socksInstance.first().value)
        assertNotEquals(socks, socksInstance.first())
    }

    @Test
    fun givenNoControlPort_whenValidationCalled_controlPortIsAdded() {
        val validated = validator.validate { true }
        val controlPort = validated.filterIsInstance<Ports.Control>()
        assertEquals(1, controlPort.size)
        assertEquals(Ports.Control(), controlPort.first())
    }
}
