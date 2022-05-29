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

import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3PrivateKey_ED25519
import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuthPrivateKey_B32_X25519
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet.KeyWord
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlOnionAdd
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.helper.TorTestHelper
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlin.test.*

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

        manager.infoGet(getInfoSocks).getOrThrow()

        Unit
    }

    @Test
    fun givenCommandConfigGet_whenTorQueried_returnsSuccess() = runBlocking {
        val getConfig = setOf(
            TorConfig.Setting.CookieAuthentication(),
            TorConfig.Setting.DisableNetwork(),
            TorConfig.Setting.Ports.Control(),
        )

        manager.configGet(getConfig).getOrThrow()

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
        ).getOrThrow()

        val entryResult = manager.onionClientAuthView(address)
        manager.onionClientAuthRemove(address)

        val entry = entryResult.getOrThrow()
        assertEquals(address.value, entry.address)
        assertEquals(privateKey.keyType.toString(), entry.keyType)
        assertEquals(privateKey.base64(padded = true), entry.privateKey)
        assertEquals(clientName.value, entry.clientName)
        assertEquals(flagPerm.toString(), entry.flags?.first())

        Unit
    }

    @Test
    fun givenCommandConfigSet_whenTorQueried_returnsSuccess() = runBlocking {

        // get DisableNetwork to an enabled state (DisableNetwork false, ie. enabled)
        val _false = TorConfig.Option.TorF.False
        val disableNetwork = TorConfig.Setting.DisableNetwork().set(_false) // enable it
        var throwable: Throwable? = null
        try {
            manager.configSet(disableNetwork).getOrThrow()

            // Check it is enabled
            val entry1 = manager.configGet(disableNetwork).getOrThrow().first()
            assertEquals(_false.value, entry1.value)

            // Now disable it
            val _true = TorConfig.Option.TorF.True
            disableNetwork.set(_true) // disable it
            manager.configSet(disableNetwork).getOrThrow()

            // verify disabled
            val entry2 = manager.configGet(disableNetwork).getOrThrow().first()
            assertEquals(_true.value, entry2.value)
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
        manager.setEvents(setOf(TorEvent.ConfChanged)).getOrThrow()

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
            manager.configSet(setOf(dormancy, padding)).getOrThrow()

            assertEquals(0, changes.size)

            dormancy.set(TorConfig.Option.TorF.False)
            padding.set(TorConfig.Option.AorTorF.False)

            manager.configSet(setOf(dormancy, padding)).getOrThrow()

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

    @Test
    fun givenHiddenService_whenNewlyGenerated_returnsSuccess() = runBlocking {
        val entry = manager.onionAddNew(
            type = OnionAddress.PrivateKey.Type.ED25519_V3,
            hsPorts = setOf(
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8761), targetPort = Port(8760)),
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8762), targetPort = Port(8760)),
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8763), targetPort = Port(8760)),
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8764), targetPort = Port(8760)),
            ),
            flags = setOf(
                TorControlOnionAdd.Flag.MaxStreamsCloseCircuit,
            ),
            maxStreams = TorConfig.Setting.HiddenService.MaxStreams(4)
        ).getOrThrow()

        manager.onionDel(entry.address).getOrThrow()

        Unit
    }

    @Test
    fun givenHiddenService_whenFlagDiscardPkSet_returnsNullPrivateKey() = runBlocking {
        val expectedAddress = OnionAddressV3("2v4lfb2eo4cuygh6ssuwmkwsqqcdoua5nyjb7vkkxg76wllhbz3ltpad")

        val entry = manager.onionAdd(
            privateKey = OnionAddressV3PrivateKey_ED25519("gGNRsNtSKe38fPr/J1UW2nOCNetZNl3qNySlPs9M5Fait1VVruWEBOoNU7fuPRkC4yrS1G7f/VjohBdzKTIQ6Q"),
            hsPorts = setOf(
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8770), targetPort = Port(8770)),
            ),
            flags = setOf(
                TorControlOnionAdd.Flag.DiscardPK,
            ),
        ).getOrThrow()

        manager.onionDel(expectedAddress)

        assertEquals(expectedAddress, entry.address)
        assertNull(entry.privateKey)

        Unit
    }

    @Test
    fun givenHiddenService_whenAdded_returnsPrivateKey() = runBlocking {
        val expectedAddress = OnionAddressV3("2v4lfb2eo4cuygh6ssuwmkwsqqcdoua5nyjb7vkkxg76wllhbz3ltpad")
        val expectedPrivateKey = OnionAddressV3PrivateKey_ED25519("gGNRsNtSKe38fPr/J1UW2nOCNetZNl3qNySlPs9M5Fait1VVruWEBOoNU7fuPRkC4yrS1G7f/VjohBdzKTIQ6Q")

        val entry = manager.onionAdd(
            privateKey = expectedPrivateKey,
            hsPorts = setOf(
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8770), targetPort = Port(8770)),
            ),
        ).getOrThrow()

        manager.onionDel(expectedAddress)

        assertEquals(expectedAddress, entry.address)
        assertEquals(expectedPrivateKey, entry.privateKey)

        Unit
    }

    @Test
    fun givenHiddenService_whenNoPortsProvided_returnsFailure() = runBlocking {
        val resultNew = manager.onionAddNew(
            type = OnionAddress.PrivateKey.Type.ED25519_V3,
            hsPorts = emptySet()
        )

        assertTrue(resultNew.isFailure)

        val resultExisting = manager.onionAdd(
            privateKey = OnionAddressV3PrivateKey_ED25519("gGNRsNtSKe38fPr/J1UW2nOCNetZNl3qNySlPs9M5Fait1VVruWEBOoNU7fuPRkC4yrS1G7f/VjohBdzKTIQ6Q"),
            hsPorts = emptySet()
        )

        assertTrue(resultExisting.isFailure)

        Unit
    }

    @Test
    fun givenHiddenService_whenAlreadyAdded_returnsFailure() = runBlocking {
        val entry = manager.onionAddNew(
            type = OnionAddress.PrivateKey.Type.ED25519_V3,
            hsPorts = setOf(
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8771), targetPort = Port(8770)),
            ),
        ).getOrThrow()

        val result = manager.onionAdd(
            privateKey = entry.privateKey!!,
            hsPorts = setOf(
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8772), targetPort = Port(8770)),
            ),
        )

        manager.onionDel(entry.address)

        assertTrue(result.isFailure)

        Unit
    }

    @Test
    fun givenController_whenReplyIsCodedAs25x_returnsSuccess() = runBlocking {
        val address = OnionAddressV3("6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd")
        val privateKey = OnionClientAuthPrivateKey_B32_X25519("XAYX4ZVA2WOC7NMTZKG5XQ4OUDHUJDIPQVGPJ3LPPXBHN7NLB56Q")
        val privateKey2 = OnionClientAuthPrivateKey_B32_X25519("5BM6QSFGK6HLVDJKMYXHHU7L5IRMDY2W7MXEXN46IYH5URKBTRYA")
        val clientName = ClientName("Test^.HS")
        val flagPerm = TorControlOnionClientAuth.Flag.Permanent

        manager.onionClientAuthAdd(
            address = address,
            key = privateKey,
            clientName = clientName,
            flags = setOf(flagPerm),
        ).getOrThrow()

        // This will result in Tor responding with a non 250 coded reply.
        // status=251, message=Client for onion existed and replaced
        val result = manager.onionClientAuthAdd(
            address = address,
            key = privateKey2,
            clientName = clientName,
            flags = setOf(flagPerm),
        )

        manager.onionClientAuthRemove(address)

        // Fail test if kotlin.Result.Failure
        result.getOrThrow()

        Unit
    }

    @Test
    fun givenController_whenHSFetched_descriptorsAreReceived() = runBlocking {
        awaitBootstrap(timeout = 60_000)

        manager.setEvents(setOf(TorEvent.HSDescriptor)).getOrThrow()
        val torProjectAddress = OnionAddressV3("2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid")

        var received = false
        val listener = object : TorManagerEvent.Listener() {
            override fun eventHSDescriptor(output: String) {

                // RECEIVED 2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid NO_AUTH $C71784391F1DA256250C03F7ACBD4801D0344BB3~Quetzalcoatl 5A8bUxARjHiRswE/D0UDbIEe3qiyNi4Rpy6ZXz4AVJ8
                val splits = output.split(' ')

                if (
                    splits.first().equals("RECEIVED", ignoreCase = true) &&
                    splits.elementAt(1) == torProjectAddress.value
                ) {
                    received = true
                }
            }
        }

        manager.addListener(listener)

        var throwable: Throwable? = null
        try {
//            // $DD2CE79C61F8395EA93A3194FE87394AC40453F1~hands CONNECTED $AA7630738F82C4DF26BB8EFF131564DACDEAC64C~Unnamed CONNECTED
//            val orConnInfo = manager.infoGet(KeyWord.Status.ORConn()).getOrThrow().split(' ')
//
//            val servers: MutableList<Server.LongName> = ArrayList(orConnInfo.size / 2)
//
//            for ((i, status) in orConnInfo.withIndex()) {
//                if (!status.startsWith('$')) {
//                    continue
//                }
//
//                val next = orConnInfo.elementAtOrNull(i + 1) ?: continue
//
//                if (next.equals("CONNECTED", ignoreCase = true)) {
//                    servers.add(Server.LongName.fromString(status))
//                }
//            }
//
//            assertTrue(servers.isNotEmpty())

            manager.hsFetch(torProjectAddress).getOrThrow()

            // await HSDescriptor event to be dispatched
            var count = 0
            while (!received && count < 10 && isActive) {
                delay(1_000)
                count++
            }

            assertTrue(received)
        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.removeListener(listener)
            manager.setEvents(setOf())

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }

    @Test
    fun givenTorController_whenConfigGetSingleKeyWord_returnsMultipleEntries() = runBlocking {
        delay(250L)

        val hsConfigSettings = configProvider
            .lastValidatedTorConfig!!
            .torConfig
            .settings
            .filterIsInstance<TorConfig.Setting.HiddenService>()
            .size

        assertTrue(hsConfigSettings > 1)

        val entries = manager.configGet(TorConfig.Setting.HiddenService()).getOrThrow()

        assertEquals(hsConfigSettings, entries.size)

        Unit
    }
}
