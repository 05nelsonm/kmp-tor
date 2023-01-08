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

import io.matthewnelson.kmp.tor.common.address.*
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuthPrivateKey_B32_X25519
import io.matthewnelson.kmp.tor.controller.common.config.ConfigEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet.KeyWord
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlMapAddress.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlOnionAdd
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.controller.common.internal.appendTo
import io.matthewnelson.kmp.tor.helpers.model.KeyWordModel
import io.matthewnelson.kmp.tor.helpers.TorTestHelper
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.util.PortUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlin.test.*

@OptIn(InternalTorApi::class)
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
            TorConfig.KeyWord.CookieAuthentication,
            TorConfig.KeyWord.DisableNetwork,
            TorConfig.KeyWord.ControlPort,
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
            val entry1 = manager.configGet(disableNetwork.keyword).getOrThrow().first()
            assertEquals(_false.value, entry1.value)

            // Now disable it
            val _true = TorConfig.Option.TorF.True
            disableNetwork.set(_true) // disable it
            manager.configSet(disableNetwork).getOrThrow()

            // verify disabled
            val entry2 = manager.configGet(disableNetwork.keyword).getOrThrow().first()
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

            manager.configSet(setOf(dormancy, padding))
            manager.setEvents(setOf())
            manager.removeListener(listener)

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }

    @Test
    fun givenNewHiddenService_whenTCPPortOnly_returnsSuccess() = runBlocking {
        val entry = manager.onionAddNew(
            type = OnionAddress.PrivateKey.Type.ED25519_V3,
            hsPorts = setOf(
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8761), targetPort = Port(8760)),
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8762), targetPort = Port(8760)),
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8763), targetPort = Port(8760)),
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
    fun givenNewHiddenService_whenUnixSocketPortOnly_returnsSuccess() = runBlocking {
        // Only run if support for domain sockets is had by Tor
        if (!(PlatformUtil.isDarwin || PlatformUtil.isLinux)) return@runBlocking

        val workDir = configProvider.workDir

        val entryResult = manager.onionAddNew(
            type = OnionAddress.PrivateKey.Type.ED25519_V3,
            hsPorts = setOf(

                // Will fail if targetUnixSocket contains space in the path
                TorConfig.Setting.HiddenService.UnixSocket(
                    virtualPort = Port(8764),
                    targetUnixSocket = workDir.builder {
                        addSegment(TorConfig.Setting.DataDirectory.DEFAULT_NAME)
                        addSegment("test_hs.sock")
                    }
                ),
            ),
            flags = setOf(
                TorControlOnionAdd.Flag.MaxStreamsCloseCircuit,
            ),
            maxStreams = TorConfig.Setting.HiddenService.MaxStreams(4)
        )

        if (
            entryResult.toString().contains("Bad arguments to ADD_ONION: Cannot parse keyword argument(s)") &&
            workDir.value.contains(' ')
        ) {
            // https://gitlab.torproject.org/tpo/core/tor/-/issues/40633
            // Pass the test
        } else {
            manager.onionDel(entryResult.getOrThrow().address).getOrThrow()
        }

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
            maxStreams = null,
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
            flags = null,
            maxStreams = null,
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
            hsPorts = emptySet(),
            flags = null,
            maxStreams = null,
        )

        assertTrue(resultNew.isFailure)

        val resultExisting = manager.onionAdd(
            privateKey = OnionAddressV3PrivateKey_ED25519("gGNRsNtSKe38fPr/J1UW2nOCNetZNl3qNySlPs9M5Fait1VVruWEBOoNU7fuPRkC4yrS1G7f/VjohBdzKTIQ6Q"),
            hsPorts = emptySet(),
            flags = null,
            maxStreams = null,
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
            flags = null,
            maxStreams = null,
        ).getOrThrow()

        val result = manager.onionAdd(
            privateKey = entry.privateKey!!,
            hsPorts = setOf(
                TorConfig.Setting.HiddenService.Ports(virtualPort = Port(8772), targetPort = Port(8770)),
            ),
            flags = null,
            maxStreams = null,
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
        manager.setEvents(setOf(TorEvent.HSDescriptor)).getOrThrow()

        awaitBootstrap()

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
            while (!received && count < 180 && isActive) {
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
        val hsConfigSettings = awaitLastValidatedTorConfig()
            .torConfig
            .settings
            .filterIsInstance<TorConfig.Setting.HiddenService>()

        assertTrue(hsConfigSettings.size > 1)

        val entries = manager.configGet(hsConfigSettings.first().keyword)
            .getOrThrow()
            .filter { it.key == hsConfigSettings.first().keyword.toString() }

        assertEquals(hsConfigSettings.size, entries.size)

        Unit
    }

    @Test
    fun givenTorController_whenConfigResetHS_returnsSuccess() = runBlocking {
        val lastConfig = awaitLastValidatedTorConfig()

        val hsConfigSettings = lastConfig
            .torConfig
            .settings
            .filterIsInstance<TorConfig.Setting.HiddenService>()

        assertTrue(hsConfigSettings.size > 1)

        val entries = manager.configGet(hsConfigSettings.first().keyword)
            .getOrThrow()
            .filter { it.key == hsConfigSettings.first().keyword.toString() }

        assertEquals(hsConfigSettings.size, entries.size)

        var throwable: Throwable? = null
        try {
            manager.configSet(hsConfigSettings.first()).getOrThrow()

            val resetEntries = manager.configGet(hsConfigSettings.first().keyword)
                .getOrThrow()
                .filter { it.key == hsConfigSettings.first().keyword.toString() }

            assertEquals(1, resetEntries.size)
        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.configLoad(lastConfig.torConfig)

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }

    @Test
    fun givenTorController_whenConfigSetHS_returnsSuccess() = runBlocking {
        val lastConfig = awaitLastValidatedTorConfig()

        val hsConfigSettings = lastConfig
            .torConfig
            .settings
            .filterIsInstance<TorConfig.Setting.HiddenService>()

        assertTrue(hsConfigSettings.size > 1)

        val entries = manager.configGet(hsConfigSettings.first().keyword)
            .getOrThrow()
            .filter { it.key == hsConfigSettings.first().keyword.toString() }

        assertEquals(hsConfigSettings.size, entries.size)

        var throwable: Throwable? = null
        try {
            manager.configSet(hsConfigSettings.first()).getOrThrow()

            val resetEntries = manager.configGet(hsConfigSettings.first().keyword)
                .getOrThrow()
                .filter { it.key == hsConfigSettings.first().keyword.toString() }

            assertEquals(1, resetEntries.size)
        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.configLoad(lastConfig.torConfig)

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }

    @Test
    fun givenTorController_whenConfigResetPortWithFlags_returnsSuccess() = runBlocking {
        val port = PortUtil.findNextAvailableTcpPort(port = Port(9155), limit = 1_000)

        val socks = TorConfig.Setting.Ports.Socks()
            .setFlags(setOf(
                TorConfig.Setting.Ports.Socks.Flag.CacheDNS,
                TorConfig.Setting.Ports.Socks.Flag.OnionTrafficOnly,
            ))
            .setIsolationFlags(setOf(
                TorConfig.Setting.Ports.IsolationFlag.IsolateSOCKSAuth,
            ))
            .set(TorConfig.Option.AorDorPort.Value(PortProxy(port.value)))

        var throwable: Throwable? = null
        try {

            manager.configSet(socks).getOrThrow()

            val entry = manager.configGet(socks.keyword).getOrThrow().first()

            val sb = StringBuilder()
            socks.appendTo(sb, isWriteTorConfig = true)

            // Remove Keyword (SocksPort)
            val expected = sb.toString().substringAfter(' ')

            assertEquals(expected, entry.value)

        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.configLoad(awaitLastValidatedTorConfig().torConfig)

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }

    @Test
    fun givenTorController_whenConfigResetSocksUnixSocketWithFlags_returnsSuccess() = runBlocking {
        // Only run if support for domain sockets is had by Tor
        if (!(PlatformUtil.isDarwin || PlatformUtil.isLinux)) return@runBlocking

        val socks = TorConfig.Setting.UnixSockets.Socks()
            .setFlags(setOf(
                TorConfig.Setting.Ports.Socks.Flag.CacheDNS,
                TorConfig.Setting.Ports.Socks.Flag.OnionTrafficOnly,
            ))
            .setIsolationFlags(setOf(
                TorConfig.Setting.Ports.IsolationFlag.IsolateSOCKSAuth,
            ))
            .set(TorConfig.Option.FileSystemFile(configProvider.workDir.builder {
                addSegment(TorConfig.Setting.DataDirectory.DEFAULT_NAME)
                addSegment("socks_test_set.sock")
            }))

        var throwable: Throwable? = null
        try {

            manager.configSet(socks).getOrThrow()

            val entry = manager.configGet(socks.keyword).getOrThrow().first()

            val sb = StringBuilder()
            socks.appendTo(sb, isWriteTorConfig = true)

            // Remove Keyword (SocksPort)
            val expected = sb.toString().substringAfter(' ')

            assertEquals(expected, entry.value)

        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.configLoad(awaitLastValidatedTorConfig().torConfig)

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }

    /**
     * Validate all [TorConfig.KeyWord]s as recognized by Tor.
     * */
    @Test
    fun givenKeyWord_whenInfoGet_returnsAsRecognized() = runBlocking {
        val keywords = KeyWordModel().getAll()

        val successes = mutableListOf<Result<List<ConfigEntry>>>()
        val failures = mutableListOf<Result<List<ConfigEntry>>>()

        for (keyword in keywords) {
            val response = manager.configGet(keyword)
            if (response.isSuccess) {
                successes.add(response)
            } else {
                failures.add(response)
            }
        }

        for (response in failures) {
            println(response)
        }
        assertTrue(failures.isEmpty())

        Unit
    }

    @Test
    fun givenTorController_whenMapAddress_returnsSuccess() = runBlocking {
        manager.setEvents(setOf(TorEvent.AddressMap)).getOrThrow()

        var throwable: Throwable? = null
        try {

            val mapping1 = manager
                .mapAddress(Mapping.anyIPv4(to = "2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion"))
                .getOrThrow()

            val mapping1Info = manager
                .infoGet(KeyWord.AddressMappings.All())
                .getOrThrow()

            assertTrue(mapping1Info.parseInfoMapping().contains(mapping1))

            val mapping2 = manager
                .mapAddress(Mapping.anyHost(to = "torproject.org"))
                .getOrThrow()

            val mapping2Info = manager
                .infoGet(KeyWord.AddressMappings.All())
                .getOrThrow()

            assertTrue(mapping2Info.parseInfoMapping().contains(mapping2))

            val unMapping1 = manager
                .mapAddress(Mapping.unmap(mapping1.from))
                .getOrThrow()

            val unMapping1Info = manager
                .infoGet(KeyWord.AddressMappings.All())
                .getOrThrow()

            assertTrue(!unMapping1Info.parseInfoMapping().contains(unMapping1))
            assertTrue(unMapping1.isUnmapping)
        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.setEvents(emptySet())
            removeAllMappings()
            throwable?.let { throw it }
        }

        Unit
    }

    @Test
    fun givenTorController_whenResolve_returnsSuccess() = runBlocking {
        manager.setEvents(setOf(TorEvent.AddressMap)).getOrThrow()

        val mappedAddresses = mutableSetOf<Mapped>()

        val listener = object : TorManagerEvent.Listener() {
            override fun eventAddressMap(output: String) {
                mappedAddresses.addAll(output.parseInfoMapping())
            }
        }

        var throwable: Throwable? = null
        try {
            awaitBootstrap()
            manager.addListener(listener)
            manager.resolve(hostname = "torproject.org", reverse = false).getOrThrow()

            var timeout = 60_000L
            while (timeout > 0 && mappedAddresses.isEmpty()) {
                delay(100L)
                timeout -= 100L
            }

            val mapped = mappedAddresses.find { it.from == "torproject.org" }
            assertNotNull(mapped)
        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.setEvents(emptySet())
            manager.removeListener(listener)
            removeAllMappings()
            throwable?.let { throw it }
        }

        Unit
    }

    // Helper function to return all Mapped addresses from
    // infoGet response.
    //
    // i7myviemoo4vnprq.virtual torproject.org NEVER
    private fun String.parseInfoMapping(): Set<Mapped> {
        val lines = lines()
        val set = LinkedHashSet<Mapped>(lines.size)

        for (line in lines) {
            try {
                val splits = line.split(' ')
                val from = splits[0]
                val to = splits[1]

                if (to == "<error>") continue

                set.add(Mapped(from, to))
            } catch (_: IndexOutOfBoundsException) {
                continue
            }
        }

        return set
    }

    private suspend fun removeAllMappings() {
        // Unmap any remaining test data
        manager
            .infoGet(KeyWord.AddressMappings.All())
            .onSuccess { result ->
                val toUnMap = result.parseInfoMapping().map { mapped ->
                    Mapping.unmap(mapped.from)
                }.toSet()

                if (toUnMap.isNotEmpty()) {
                    manager.mapAddress(toUnMap)
                }
            }
    }
}
