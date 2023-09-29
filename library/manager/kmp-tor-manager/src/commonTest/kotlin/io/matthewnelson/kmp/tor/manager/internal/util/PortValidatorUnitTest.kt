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

import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.Ports
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.UnixSockets
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.AorDorPort
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import kotlin.test.*

@OptIn(InternalTorApi::class)
class PortValidatorUnitTest {

    companion object {
        private const val DATA_DIR = "/tmp"
    }

    private val validator = PortValidator(Path(DATA_DIR))

    @Test
    fun givenPortWithValue_whenPortUnavailable_setsToAuto() {
        val port = PortProxy(9150)
        val socks = Ports.Socks()
        socks.set(AorDorPort.Value(port))

        validator.add(socks)

        val validated = validator.validate { false }

        assertEquals(AorDorPort.Auto, validated.first().value)
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
        val controlSocket = validated.filterIsInstance<UnixSockets.Control>()
        assertEquals(1, controlPort.size + controlSocket.size)

        if (PlatformUtil.hasControlUnixDomainSocketSupport) {
            val expected = UnixSockets.Control()
            expected.set(TorConfig.Option.FileSystemFile(Path(DATA_DIR).builder {
                addSegment(UnixSockets.Control.DEFAULT_NAME)
            }))

            assertNotNull(expected.value)
            assertNotNull(controlSocket.first().value)
            assertEquals(expected, controlSocket.first())
        } else {
            assertEquals(Ports.Control(), controlPort.first())
            assertEquals(AorDorPort.Auto, controlPort.first().value)
        }
    }

    @Test
    fun givenUnixSocketControl_whenValidationCalled_controlPortIsNotAdded() {
        validator.add(UnixSockets.Control())
        val validated = validator.validate { true }
        assertTrue(validated.filterIsInstance<Ports.Control>().isEmpty())
        assertFalse(validated.filterIsInstance<UnixSockets.Control>().isEmpty())
    }

    @Test
    fun givenUnixSocketSocks_whenValidationCalled_socksPortIsNotAdded() {
        validator.add(UnixSockets.Socks())
        val validated = validator.validate { true }
        assertTrue(validated.filterIsInstance<Ports.Socks>().isEmpty())
        assertFalse(validated.filterIsInstance<UnixSockets.Socks>().isEmpty())
    }

    @Test
    fun givenUnixSocket_whenInvalidLength_thenFails() {
        val sb = StringBuilder()
        sb.append('/') // so check for unix fs succeeds
        repeat(104) { sb.append('a') }

        val socks = UnixSockets.Socks()
        socks.set(TorConfig.Option.FileSystemFile(Path(sb.toString())))
        println(socks.value?.value?.length)
        validator.add(socks)
        sb.append('j')
        socks.set(TorConfig.Option.FileSystemFile(Path(sb.toString())))

        assertFailsWith<TorManagerException> {
            validator.add(socks)
        }
    }
}
