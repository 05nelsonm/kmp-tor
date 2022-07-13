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
package io.matthewnelson.kmp.tor.controller.internal.controller

import io.matthewnelson.component.encoding.base16.encodeBase16
import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth
import io.matthewnelson.kmp.tor.common.internal.TorStrings
import io.matthewnelson.kmp.tor.common.server.Server
import io.matthewnelson.kmp.tor.controller.TorControlProcessor
import io.matthewnelson.kmp.tor.controller.common.config.ClientAuthEntry
import io.matthewnelson.kmp.tor.controller.common.config.ConfigEntry
import io.matthewnelson.kmp.tor.controller.common.config.HiddenServiceEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlOnionAdd
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.controller.common.internal.appendTo
import io.matthewnelson.kmp.tor.controller.common.internal.filterSupportedOnly
import io.matthewnelson.kmp.tor.controller.common.internal.writeEscapedIfTrue
import kotlinx.coroutines.delay

@OptIn(InternalTorApi::class)
internal class RealTorControlProcessor(
    interactor: ControlPortInteractor,
    private val processorLock: TorControlProcessorLock,
): TorControlProcessor, ControlPortInteractor by interactor {

    override suspend fun authenticate(bytes: ByteArray): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("AUTHENTICATE").apply {
                append(TorStrings.SP)
                append(bytes.encodeBase16())
                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

//    override suspend fun challengeAuth(clientNonce: String): Result<Map<String, String>> {
//        return processorLock.withContextAndLock {
//            val command = StringBuilder("AUTHCHALLENGE")
//                .append(SP)
//                .append("SAFECOOKIE")
//                .append(SP)
//                .append(clientNonce)
//                .append(CLRF)
//                .toString()
//
//            Result.success(processCommand(command).toMap())
//        }
//    }

//    override suspend fun circuitClose(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

//    override suspend fun circuitExtend(): Result<String> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

//    override suspend fun circuitSetPurpose(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

    override suspend fun configGet(keyword: TorConfig.KeyWord): Result<List<ConfigEntry>> {
        return configGet(setOf(keyword))
    }

    override suspend fun configGet(keywords: Set<TorConfig.KeyWord>): Result<List<ConfigEntry>> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("GETCONF").apply {
                for (keyword in keywords) {
                    append(TorStrings.SP)
                    append(keyword)
                }
                append(TorStrings.CLRF)
            }.toString()

            val configEntry = processCommand(command).map { reply ->
                val kvp = reply.message
                val index = kvp.indexOf('=')
                if (index >= 0) {
                    ConfigEntry(kvp.substring(0, index), kvp.substring(index + 1))
                } else {
                    ConfigEntry(kvp)
                }
            }

            Result.success(configEntry)
        }
    }

    override suspend fun configLoad(config: TorConfig): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("+LOADCONF").apply {
                append(TorStrings.CLRF)
                append(config.text)
                append(TorStrings.CLRF)
                append(TorStrings.MULTI_LINE_END)
                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

    override suspend fun configReset(keyword: TorConfig.KeyWord): Result<Any?> {
        return configReset(setOf(keyword))
    }

    override suspend fun configReset(keywords: Set<TorConfig.KeyWord>): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("RESETCONF").apply {
                for (keyword in keywords) {
                    when (keyword) {
                        // ControlPort can only be set upon startup
                        is TorConfig.KeyWord.ControlPort,

                            // Cookie authentication can only be set upon startup
                        is TorConfig.KeyWord.CookieAuthFile,
                        is TorConfig.KeyWord.CookieAuthentication,

                            // ControlPortWriteToFile can only be set upon startup
                        is TorConfig.KeyWord.ControlPortWriteToFile -> {
                            throw TorControllerException("$keyword cannot be modified via RESETCONF")
                        }
                        else -> { /* no-op */ }
                    }

                    append(TorStrings.SP)
                    append(keyword)
                }

                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

    override suspend fun configSave(force: Boolean): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("SAVECONF").apply {
                if (force) {
                    append(TorStrings.SP)
                    append("FORCE")
                }
                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

    override suspend fun configSet(setting: TorConfig.Setting<*>): Result<Any?> {
        return configSet(setOf(setting))
    }

    override suspend fun configSet(settings: Set<TorConfig.Setting<*>>): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("SETCONF").apply {

                for (setting in settings) {
                    when (setting.keyword) {
                        // ControlPort can only be set upon startup
                        is TorConfig.KeyWord.ControlPort,

                            // Cookie authentication can only be set upon startup
                        is TorConfig.KeyWord.CookieAuthFile,
                        is TorConfig.KeyWord.CookieAuthentication,

                            // ControlPortWriteToFile can only be set upon startup
                        is TorConfig.KeyWord.ControlPortWriteToFile -> {
                            throw TorControllerException("${setting.keyword} cannot be modified via SETCONF")
                        }
                        else -> { /* no-op */ }
                    }

                    append(TorStrings.SP)
                    if (!setting.appendTo(this, isWriteTorConfig = false)) {
                        throw TorControllerException("Failed to add ${setting.keyword} to SETCONF command")
                    }
                }

                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

//    override suspend fun descriptorPost(): Result<String> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

    override suspend fun dropGuards(): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = "DROPGUARDS${TorStrings.CLRF}"

            Result.success(processCommand(command))
        }
    }

    override suspend fun hsFetch(address: OnionAddress): Result<Any?> {
        return hsFetch(null, address)
    }

    override suspend fun hsFetch(address: OnionAddress, server: Server.Fingerprint): Result<Any?> {
        return hsFetch(setOf(server), address)
    }

    override suspend fun hsFetch(
        address: OnionAddress,
        servers: Set<Server.Fingerprint>
    ): Result<Any?> {
        return hsFetch(servers, address)
    }

    private suspend fun hsFetch(
        servers: Set<Server.Fingerprint>?,
        address: OnionAddress
    ): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("HSFETCH").apply {
                append(TorStrings.SP)
                append(address.value)

                if (servers != null && servers.isNotEmpty()) {
                    for (server in servers) {
                        append(TorStrings.SP)
                        append("SERVER=")
                        append(server.value)
                    }
                }

                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

//    override suspend fun hsPost(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

    override suspend fun infoGet(keyword: TorControlInfoGet.KeyWord): Result<String> {
        return infoGet(setOf(keyword)).mapCatching { it[keyword.value]!! }
    }

    override suspend fun infoGet(keywords: Set<TorControlInfoGet.KeyWord>): Result<Map<String, String>> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("GETINFO").apply {
                if (keywords.isNotEmpty()) {
                    keywords.joinTo(this, separator = TorStrings.SP, prefix = TorStrings.SP) { it.value }
                }
                append(TorStrings.CLRF)
            }.toString()

            val map = processCommand(command).toMap()

            Result.success(map)
        }
    }

//    override suspend fun infoProtocol(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

//    override suspend fun mapAddress(): Result<Map<String, String>> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

    override suspend fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.VirtualPort>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return processorLock.withContextAndLock {
            val filtered = hsPorts.filterSupportedOnly() ?: throw TorControllerException(
                "Invalid HiddenService.VirtualPort(s) provided: $hsPorts"
            )

            val sb = StringBuilder("ADD_ONION").apply {
                append(TorStrings.SP)
                append(privateKey.keyType)
                append(':')
                append(privateKey.value)
            }

            onionAdd(sb, filtered, flags, maxStreams, privateKey)
        }
    }

    override suspend fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.VirtualPort>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return processorLock.withContextAndLock {
            val filtered = hsPorts.filterSupportedOnly() ?: throw TorControllerException(
                "Invalid HiddenService.VirtualPort(s) provided: $hsPorts"
            )

            val sb = StringBuilder("ADD_ONION").apply {
                append(TorStrings.SP)
                append("NEW")
                append(':')
                append(type)
            }

            onionAdd(sb, filtered, flags, maxStreams, null)
        }
    }

    private suspend fun onionAdd(
        sb: StringBuilder,
        hsPorts: Set<TorConfig.Setting.HiddenService.VirtualPort>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
        privateKey: OnionAddress.PrivateKey?,
    ): Result<HiddenServiceEntry> {
        val command = sb.apply {
            if (flags != null && flags.isNotEmpty()) {
                append(TorStrings.SP)
                append("Flags=")
                flags.joinTo(this, separator = ",")
            }

            if (maxStreams != null) {
                append(TorStrings.SP)
                append("MaxStreams=")
                append(maxStreams.value)
            }

            val localHostIp = PlatformUtil.localhostAddress()

            for (hsPort in hsPorts) {
                append(TorStrings.SP)
                append("Port=")
                when (hsPort) {
                    is TorConfig.Setting.HiddenService.Ports -> {
                        append(hsPort.virtualPort.value)
                        append(',')
                        append(localHostIp)
                        append(':')
                        append(hsPort.targetPort.value)
                    }
                    is TorConfig.Setting.HiddenService.UnixSocket -> {
                        append(hsPort.virtualPort.value)
                        append(',')
                        append("unix:")

                        // NOTE: If path has a space in it, controller fails
                        //  as it cannot parse. There is no ability to quote
                        //  the unix:"/pa th/to/hs.sock" like usual...
                        //  .
                        //  https://github.com/05nelsonm/kmp-tor/issues/207#issuecomment-1166722564
                        //  https://gitlab.torproject.org/tpo/core/tor/-/issues/40633
                        val path = hsPort
                            .targetUnixSocket
                            .writeEscapedIfTrue(true)
                            .value

                        append(path)
                    }
                }
            }

            append(TorStrings.CLRF)
        }.toString()

        val isFlagDiscardPkPresent = flags?.contains(TorControlOnionAdd.Flag.DiscardPK) == true

        // SingleLine(status=250, message=ServiceID=bxtow33uhscfu2xscwmha4quznly7ybfocm6i5uh35uyltddbj4yesyd)
        // SingleLine(status=250, message=PrivateKey=ED25519-V3:cKjLrpfAV0rNDmfn6hMpcWUFsN82MqhVxwre9c3KjnlfkZxkJlyixy756WMKtNlVyMrhSxgaZfECky7rE1O1dA==)
        // SingleLine(status=250, message=OK)
        val hsEntry = processCommand(command).let { lines ->

            var serviceId: OnionAddress? = null
            var privKey: OnionAddress.PrivateKey? = if (isFlagDiscardPkPresent) {
                null
            } else {
                privateKey
            }

            for (line in lines) {
                val splits = line.message.split('=')
                when (splits.elementAtOrNull(0)?.lowercase()) {
                    "serviceid" -> {
                        serviceId = splits
                            .elementAtOrNull(1)
                            ?.let { onionAddress ->
                                OnionAddress.fromStringOrNull(onionAddress)
                            }
                    }
                    "privatekey" -> {
                        privKey = splits
                            .elementAtOrNull(1)
                            ?.split(':')
                            ?.elementAtOrNull(1)
                            ?.let { key ->
                                OnionAddress.PrivateKey.fromStringOrNull(key)
                            }
                    }
                    else -> continue
                }
            }

            if (serviceId == null) {
                throw TorControllerException("Failed to parse reply for onion address")
            }

            if (!isFlagDiscardPkPresent && privKey == null) {
                throw TorControllerException("Failed to parse reply for onion address private key")
            }

            HiddenServiceEntry(
                address = serviceId,
                privateKey = privKey,
                ports = hsPorts
            )
        }

        return Result.success(hsEntry)
    }

    override suspend fun onionDel(address: OnionAddress): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("DEL_ONION").apply {
                append(TorStrings.SP)
                append(address.value)
                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

    override suspend fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: OnionClientAuth.PrivateKey,
        clientName: ClientName?,
        flags: Set<TorControlOnionClientAuth.Flag>?
    ): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("ONION_CLIENT_AUTH_ADD").apply {
                append(TorStrings.SP)
                append(address.value)
                append(TorStrings.SP)
                append(key.keyType)
                append(':')
                append(key.base64(padded = false))
                if (clientName != null) {
                    append(TorStrings.SP)
                    append("ClientName=")
                    append(clientName.value)
                }
                if (flags != null && flags.isNotEmpty()) {
                    append(TorStrings.SP)
                    append("Flags=")
                    flags.joinTo(this, separator = ",")
                }
                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

    override suspend fun onionClientAuthRemove(address: OnionAddressV3): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("ONION_CLIENT_AUTH_REMOVE").apply {
                append(TorStrings.SP)
                append(address.value)
                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

    override suspend fun onionClientAuthView(): Result<List<ClientAuthEntry>> {
        return processorLock.withContextAndLock {
            Result.success(onionClientAuthView(null))
        }
    }

    override suspend fun onionClientAuthView(address: OnionAddressV3): Result<ClientAuthEntry> {
        return processorLock.withContextAndLock {
            val entries = onionClientAuthView(address.value)

            var entry: ClientAuthEntry? = null
            for (item in entries) {
                if (item.address == address.value) {
                    entry = item
                    break
                }
            }

            if (entry != null) {
                Result.success(entry)
            } else {
                throw TorControllerException("Failed to retrieve a ClientAuthEntry for $address")
            }
        }
    }

    private suspend fun onionClientAuthView(address: String?): List<ClientAuthEntry> {
        val command = StringBuilder("ONION_CLIENT_AUTH_VIEW").apply {
            if (address != null) {
                append(TorStrings.SP)
                append(address)
            }
            append(TorStrings.CLRF)
        }.toString()

        return processCommand(command).mapNotNull { reply ->
            // 250-CLIENT 6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd x25519:uDF+ZqDVnC+1k8qN28OOoM9EjQ+FTPTtb33Cdv2rD30= Flags=Permanent ClientName=Test^.HS
            if (reply.message.startsWith("CLIENT ")) {
                val splits = reply.message.split(' ')

                // positioning of Flags & ClientName can be either or, so we have to check for both
                var clientName: String? = null
                var flags: List<String>? = null

                for (i in 3..4) {
                    splits.elementAtOrNull(i)?.let { split ->
                        when {
                            split.startsWith("ClientName=") -> {
                                clientName = split.substringAfter('=')
                            }
                            split.startsWith("Flags=") -> {
                                flags = split.substringAfter('=').split(',')
                            }
                        }
                    }
                }

                ClientAuthEntry(
                    address = splits[1],
                    keyType = splits[2].substringBefore(':'),
                    privateKey = splits[2].substringAfter(':'),
                    clientName = clientName,
                    flags = flags,
                )
            } else {
                null
            }
        }
    }

    override suspend fun ownershipDrop(): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = "DROPOWNERSHIP${TorStrings.CLRF}"

            Result.success(processCommand(command))
        }
    }

    override suspend fun ownershipTake(): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = "TAKEOWNERSHIP${TorStrings.CLRF}"

            Result.success(processCommand(command))
        }
    }

//    override suspend fun resolve(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

    override suspend fun setEvents(events: Set<TorEvent>): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("SETEVENTS").apply {
                if (events.isNotEmpty()) {
                    events.joinTo(this, separator = TorStrings.SP, prefix = TorStrings.SP)
                }
                append(TorStrings.CLRF)
            }.toString()

            Result.success(processCommand(command))
        }
    }

    override suspend fun signal(signal: TorControlSignal.Signal): Result<Any?> {
        return processorLock.withContextAndLock {
            val command = StringBuilder("SIGNAL").apply {
                append(TorStrings.SP)
                append(signal)
                append(TorStrings.CLRF)
            }.toString()

            val result = processCommand(command)

            when (signal) {
                TorControlSignal.Signal.Halt,
                TorControlSignal.Signal.Shutdown -> { delay(500L) }
                else -> { /* no-op */ }
            }

            Result.success(result)
        }
    }

//    override suspend fun streamAttach(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

//    override suspend fun streamClose(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

//    override suspend fun streamRedirect(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

//    override suspend fun useFeature(): Result<Any?> {
//        return processorLock.withContextAndLock {
//            TODO("Not yet implemented")
//        }
//    }

    private fun List<ReplyLine.SingleLine>.toMap(): Map<String, String> {
        if (isEmpty()) {
            return emptyMap()
        }

        val size = size - if (last().message == "OK") 1 else 0
        val map: MutableMap<String, String> = LinkedHashMap(size)
        for (i in 0 until size) {
            val kvp = this[i].message
            val index = kvp.indexOf('=')
            map[kvp.substring(0, index)] = kvp.substring(index + 1)
        }
        return map
    }
}
