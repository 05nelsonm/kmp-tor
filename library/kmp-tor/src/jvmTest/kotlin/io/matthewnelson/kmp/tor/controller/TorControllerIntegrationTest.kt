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
package io.matthewnelson.kmp.tor.controller

import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuthPrivateKey_B32_X25519
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet.KeyWord
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.helper.TorTestHelper
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class TorControllerIntegrationTest: TorTestHelper() {

    @Test
    fun givenCommandGetInfo_whenTorQueried_returnsSuccess() = runBlocking {
        val getInfoSocks = setOf(
            KeyWord.Status.BootstrapPhase(),
            KeyWord.NetListeners.Control(),
            KeyWord.NetListeners.Dns(),
            KeyWord.NetListeners.HttpTunnel(),
            KeyWord.NetListeners.Socks(),
            KeyWord.NetListeners.Trans(),
        )

        manager.infoGet(getInfoSocks).onFailure { ex ->
            fail(ex.stackTraceToString())
        }

        Unit
    }

    @Test
    fun givenCommandConfigGet_whenTorQueried_returnsSuccess() = runBlocking {
        val getConfig = setOf(
            TorConfig.Setting.CookieAuthentication(),
            TorConfig.Setting.DisableNetwork(),
            TorConfig.Setting.Ports.Control(),
        )

        manager.configGet(getConfig).onFailure { ex ->
            fail(ex.stackTraceToString())
        }

        Unit
    }

    @Test
    fun givenCommandOnionClientAuthView_whenTorQueried_returnsSuccess() = runBlocking {
        // Add the private key
        val address = OnionAddressV3("6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd")
        val privateKey = OnionClientAuthPrivateKey_B32_X25519("XAYX4ZVA2WOC7NMTZKG5XQ4OUDHUJDIPQVGPJ3LPPXBHN7NLB56Q")
        val clientName = ClientName("Test^.HS")
        val flagPerm = TorControlOnionClientAuth.Flag.Permanent

        manager.onionClientAuthAdd(
            address = address,
            key = privateKey,
            clientName = clientName,
            flags = setOf(flagPerm),
        ).onFailure { ex ->
            fail(ex.stackTraceToString())
        }

        val result = manager.onionClientAuthView(address)
        result.onFailure { ex ->
            fail(ex.stackTraceToString())
        }
        result.onSuccess { entry ->
            assertEquals(address.value, entry.address)
            assertEquals(privateKey.keyType.toString(), entry.keyType)
            assertEquals(privateKey.base64(padded = true), entry.privateKey)
            assertEquals(clientName.value, entry.clientName)
            assertEquals(flagPerm.toString(), entry.flags?.first())
        }

        Unit
    }

    @Test
    fun givenCommandConfigSet_whenTorQueried_returnsSuccess() = runBlocking {

        // get DisableNetwork to an enabled state (DisableNetwork false, ie. enabled)
        val _false = TorConfig.Option.TorF.False
        val disableNetwork = TorConfig.Setting.DisableNetwork().set(_false) // enable it
        var throwable: Throwable? = null
        try {
            manager.configSet(disableNetwork).onFailure { ex ->
                fail(ex.stackTraceToString())
            }

            // Check it is enabled
            val result1 = manager.configGet(disableNetwork)
            result1.onFailure { ex ->
                fail(ex.stackTraceToString())
            }
            result1.onSuccess { entry ->
                assertEquals(_false.value, entry.value)
            }

            // Now disable it
            val _true = TorConfig.Option.TorF.True
            disableNetwork.set(_true) // disable it
            manager.configSet(disableNetwork).onFailure { ex ->
                fail(ex.stackTraceToString())
            }

            // verify disabled
            val result2 = manager.configGet(disableNetwork)
            result2.onFailure { ex ->
                fail(ex.stackTraceToString())
            }
            result2.onSuccess { entry ->
                assertEquals(_true.value, entry.value)
            }
        } catch (t: Throwable) {
            throwable = t
        } finally {
            // re-enable it
            manager.configSet(disableNetwork.set(_false))

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }

    @Test
    fun givenEventConfChangedSet_whenConfigSettingsModified_eventsAreParsedCorrectly() = runBlocking {
        manager.setEvents(setOf(TorEvent.ConfChanged)).onFailure { ex ->
            fail(ex.stackTraceToString())
        }

        val changes: MutableList<String> = mutableListOf()
        val listener = object : TorManagerEvent.Listener() {
            override fun eventConfChanged(output: String) {
                changes.add(output)
            }
        }

        manager.addListener(listener)
        val dormancy = TorConfig.Setting.DormantTimeoutDisabledByIdleStreams()
        val padding = TorConfig.Setting.ConnectionPadding()
        var throwable: Throwable? = null
        try {
            manager.configSet(setOf(dormancy, padding)).onFailure { ex ->
                fail(ex.stackTraceToString())
            }

            assertEquals(0, changes.size)

            dormancy.set(TorConfig.Option.TorF.False)
            padding.set(TorConfig.Option.AorTorF.False)

            manager.configSet(setOf(dormancy, padding)).onFailure { ex ->
                fail(ex.stackTraceToString())
            }

            assertEquals(2, changes.size)
        } catch (t: Throwable) {
            throwable = t
        } finally {

            manager.configReset(setOf(dormancy, padding))
            manager.setEvents(setOf())
            manager.removeListener(listener)

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }
}
