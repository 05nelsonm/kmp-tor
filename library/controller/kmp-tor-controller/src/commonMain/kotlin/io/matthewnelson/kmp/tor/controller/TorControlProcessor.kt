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
@file:Suppress("SpellCheckingInspection")

package io.matthewnelson.kmp.tor.controller

import io.matthewnelson.component.encoding.base16.encodeBase16
import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.config.ClientAuthEntry
import io.matthewnelson.kmp.tor.controller.common.config.ConfigEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet.KeyWord
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import io.matthewnelson.kmp.tor.common.util.TorStrings.CLRF
import io.matthewnelson.kmp.tor.common.util.TorStrings.MULTI_LINE_END
import io.matthewnelson.kmp.tor.common.util.TorStrings.SP
import io.matthewnelson.kmp.tor.controller.common.config.HiddenServiceEntry
import io.matthewnelson.kmp.tor.controller.common.internal.ControllerUtils
import io.matthewnelson.kmp.tor.controller.internal.controller.ControlPortInteractor
import io.matthewnelson.kmp.tor.controller.internal.controller.ReplyLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmSynthetic

/**
 * Primary interface that handles all TorControl* use cases
 *
 * @see [RealTorControlProcessor]
 * @see [RealTorController.processorDelegate]
 * */
interface TorControlProcessor:
    TorControlAuthenticate,
    TorControlChallengeAuth,
    TorControlCircuit,
    TorControlConfig,
    TorControlDescriptorPost,
    TorControlDropGuards,
    TorControlHs,
    TorControlInfoGet,
    TorControlInfoProtocol,
    TorControlMapAddress,
    TorControlOnion,
    TorControlOnionClientAuth,
    TorControlOwnershipDrop,
    TorControlOwnershipTake,
    TorControlResolve,
    TorControlSetEvents,
    TorControlSignal,
    TorControlStream,
    TorControlUseFeature
{
    companion object {
        @JvmSynthetic
        internal fun newInstance(interactor: ControlPortInteractor): TorControlProcessor =
            RealTorControlProcessor(interactor)
    }
}

@OptIn(InternalTorApi::class)
private class RealTorControlProcessor(
    interactor: ControlPortInteractor
): TorControlProcessor, ControlPortInteractor by interactor {
    private val lock = Mutex()

    override suspend fun authenticate(bytes: ByteArray): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("AUTHENTICATE")
                    .append(SP)
                    .append(bytes.encodeBase16())
                    .append(CLRF)
                    .toString()

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

//    override suspend fun challengeAuth(clientNonce: String): Result<Map<String, String>> {
//        return lock.withLock {
//            try {
//                val command = StringBuilder("AUTHCHALLENGE")
//                    .append(SP)
//                    .append("SAFECOOKIE")
//                    .append(SP)
//                    .append(clientNonce)
//                    .append(CLRF)
//                    .toString()
//
//                Result.success(processCommand(command).toMap())
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun circuitClose(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun circuitExtend(): Result<String> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun circuitSetPurpose(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

    override suspend fun configGet(setting: TorConfig.Setting<*>): Result<ConfigEntry> {
        return configGet(setOf(setting)).mapCatching { it.first() }
    }

    override suspend fun configGet(settings: Set<TorConfig.Setting<*>>): Result<List<ConfigEntry>> {
        return lock.withLock {
            try {
                val command = StringBuilder("GETCONF").let { sb ->
                    if (settings.isNotEmpty()) {
                        settings.joinTo(sb, SP, SP) { it.keyword }
                    }
                    sb.append(CLRF)
                    sb.toString()
                }

                val configEntry = processCommand(command) {
                    map { reply ->
                        val kvp = reply.message
                        val index = kvp.indexOf('=')
                        if (index >= 0) {
                            ConfigEntry(kvp.substring(0, index), kvp.substring(index + 1))
                        } else {
                            ConfigEntry(kvp)
                        }
                    }
                }

                Result.success(configEntry)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun configLoad(config: TorConfig): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("+LOADCONF").let { sb ->
                    sb.append(CLRF)

                    val kControlPort = TorConfig.Setting.Ports.Control::class
                    val kControlPortWriteToFile = TorConfig.Setting.ControlPortWriteToFile::class
                    var needsModification = false
                    for (setting in config.settings) {
                        val clazz = setting::class
                        if (clazz == kControlPort || clazz == kControlPortWriteToFile) {
                            needsModification = true
                            break
                        }
                    }

                    if (needsModification) {
                        val newConfig: TorConfig = config.newBuilder {
                            removeInstanceOf(kControlPort)
                            removeInstanceOf(kControlPortWriteToFile)
                        }.build()
                        sb.append(newConfig.text)
                    } else {
                        sb.append(config.text)
                    }

                    sb.append(CLRF)
                    sb.append(MULTI_LINE_END)
                    sb.append(CLRF)
                    sb.toString()
                }

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun configReset(
        setting: TorConfig.Setting<*>,
        setDefault: Boolean
    ): Result<Any?> {
        return configReset(setOf(setting), setDefault)
    }

    override suspend fun configReset(
        settings: Set<TorConfig.Setting<*>>,
        setDefault: Boolean
    ): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("RESETCONF").let { sb ->
                    for (setting in settings) {
                        when (setting) {
                            // ControlPort can only be set upon startup
                            is TorConfig.Setting.Ports.Control,
                            // ControlPortWriteToFile can only be set upon startup
                            is TorConfig.Setting.ControlPortWriteToFile -> continue
                            else -> { /* no-op */ }
                        }

                        sb.append(SP)
                        sb.append(setting.keyword)

                        if (!setDefault) {
                            setting.value?.value?.let { kwv ->
                                sb.append('=')
                                sb.append(kwv)
                            }
                        }

                    }
                    sb.append(CLRF)
                    sb.toString()
                }

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun configSave(force: Boolean): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("SAVECONF").let { sb ->
                    if (force) {
                        sb.append(SP)
                        sb.append("FORCE")
                    }
                    sb.append(CLRF)
                    sb.toString()
                }

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun configSet(setting: TorConfig.Setting<*>): Result<Any?> {
        return configSet(setOf(setting))
    }

    override suspend fun configSet(settings: Set<TorConfig.Setting<*>>): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("SETCONF").let { sb ->
                    for (setting in settings) {
                        when (setting) {
                            // ControlPort can only be set upon startup
                            is TorConfig.Setting.Ports.Control,
                            // ControlPortWriteToFile can only be set upon startup
                            is TorConfig.Setting.ControlPortWriteToFile -> continue
                            else -> { /* no-op */ }
                        }

                        sb.append(SP)
                        sb.append(setting.keyword)

                        setting.value?.value?.let { kwv ->
                            sb.append('=')
                            sb.append(kwv)
                        }
                    }
                    sb.append(CLRF)
                    sb.toString()
                }

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

//    override suspend fun descriptorPost(): Result<String> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

    override suspend fun dropGuards(): Result<Any?> {
        return lock.withLock {
            try {
                val command = "DROPGUARDS$CLRF"

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

//    override suspend fun hsFetch(address: OnionAddress, servers: Set<String>?): Result<Any?> {
//        return lock.withLock {
//            try {
//                val command = StringBuilder("HSFETCH").let { sb ->
//                    sb.append(SP)
//                    sb.append(address.value)
//                    if (servers != null && servers.isNotEmpty()) {
//                        for (server in servers) {
//                            if (server.isNotEmpty()) {
//                                sb.append(SP)
//                                sb.append("SERVER=")
//                                sb.append(server)
//                            }
//                        }
//                    }
//                    sb.append(CLRF)
//                    sb.toString()
//                }
//
//                Result.success(processCommand(command))
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun hsPost(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

    override suspend fun infoGet(keyword: KeyWord): Result<String> {
        return infoGet(setOf(keyword)).mapCatching { it[keyword.value]!! }
    }

    override suspend fun infoGet(keywords: Set<KeyWord>): Result<Map<String, String>> {
        return lock.withLock {
            try {
                val command = StringBuilder("GETINFO").let { sb ->
                    if (keywords.isNotEmpty()) {
                        keywords.joinTo(sb, separator = SP, prefix = SP) { it.value }
                    }
                    sb.append(CLRF)
                    sb.toString()
                }

                val map = processCommand(command) { toMap() }

                Result.success(map)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

//    override suspend fun infoProtocol(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun mapAddress(): Result<Map<String, String>> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

    override suspend fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return lock.withLock {
            val sb = StringBuilder("ADD_ONION").apply {
                append(SP)
                append(privateKey.keyType)
                append(':')
                append(privateKey.value)
            }

            onionAdd(sb, hsPorts, flags, maxStreams)
        }
    }

    override suspend fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return lock.withLock {
            val sb = StringBuilder("ADD_ONION").apply {
                append(SP)
                append("NEW")
                append(':')
                append(type)
            }

            onionAdd(sb, hsPorts, flags, maxStreams)
        }
    }

    private suspend fun onionAdd(
        sb: StringBuilder,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry> {
        return try {
            val command = sb.apply {
                if (flags != null && flags.isNotEmpty()) {
                    append(SP)
                    append("Flags=")
                    flags.joinTo(this, separator = ",")
                }

                if (maxStreams != null) {
                    append(SP)
                    append("MaxStreams=")
                    append(maxStreams.value)
                }

                if (hsPorts.isNotEmpty()) {
                    val localHostIp = try {
                        ControllerUtils.localhostAddress()
                    } catch (_: RuntimeException) {
                        withContext(Dispatchers.Default) {
                            ControllerUtils.localhostAddress()
                        }
                    }

                    for (hsPort in hsPorts) {
                        append(SP)
                        append("Port=")
                        append(hsPort.virtualPort.value)
                        append(',')
                        append(localHostIp)
                        append(':')
                        append(hsPort.targetPort.value)
                    }
                }

                append(CLRF)
            }.toString()

            // SingleLine(status=250, message=ServiceID=bxtow33uhscfu2xscwmha4quznly7ybfocm6i5uh35uyltddbj4yesyd)
            // SingleLine(status=250, message=PrivateKey=ED25519-V3:cKjLrpfAV0rNDmfn6hMpcWUFsN82MqhVxwre9c3KjnlfkZxkJlyixy756WMKtNlVyMrhSxgaZfECky7rE1O1dA==)
            // SingleLine(status=250, message=OK)
            val hsEntry = processCommand(command) {

                var serviceId: OnionAddress? = null
                var privateKey: OnionAddress.PrivateKey? = null
                for (line in this) {
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
                            privateKey = splits
                                .elementAtOrNull(1)
                                ?.split(':')
                                ?.elementAtOrNull(1)
                                ?.let { key ->
                                    OnionAddress.PrivateKey.fromString(key)
                                }
                        }
                        else -> continue
                    }
                }

                if (serviceId == null) {
                    throw TorControllerException("Failed to parse reply for onion address")
                }

                if (flags?.contains(TorControlOnionAdd.Flag.DiscardPK) != true && privateKey == null) {
                    throw TorControllerException("Failed to parse reply for onion address private key")
                }

                HiddenServiceEntry(
                    address = serviceId,
                    privateKey = privateKey,
                    ports = hsPorts
                )
            }

            Result.success(hsEntry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun onionDel(address: OnionAddress): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("DEL_ONION")
                    .append(SP)
                    .append(address.value)
                    .append(CLRF)
                    .toString()

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: OnionClientAuth.PrivateKey,
        clientName: ClientName?,
        flags: Set<TorControlOnionClientAuth.Flag>?
    ): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("ONION_CLIENT_AUTH_ADD").let { sb ->
                    sb.append(SP)
                    sb.append(address.value)
                    sb.append(SP)
                    sb.append(key.keyType)
                    sb.append(':')
                    sb.append(key.base64(padded = false))
                    if (clientName != null) {
                        sb.append(SP)
                        sb.append("ClientName=")
                        sb.append(clientName.value)
                    }
                    if (flags != null && flags.isNotEmpty()) {
                        sb.append(SP)
                        sb.append("Flags=")
                        flags.joinTo(sb, separator = ",")
                    }
                    sb.append(CLRF)
                    sb.toString()
                }

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun onionClientAuthRemove(address: OnionAddressV3): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("ONION_CLIENT_AUTH_REMOVE")
                    .append(SP)
                    .append(address.value)
                    .append(CLRF)
                    .toString()

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun onionClientAuthView(): Result<List<ClientAuthEntry>> {
        return try {
            Result.success(onionClientAuthView(null))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun onionClientAuthView(address: OnionAddressV3): Result<ClientAuthEntry> {
        return try {
            val list = onionClientAuthView(address.value)
            for (entry in list) {
                if (entry.address == address.value) {
                    return Result.success(entry)
                }
            }

            throw TorControllerException("Failed to retrieve a ClientAuthEntry for $address")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun onionClientAuthView(address: String?): List<ClientAuthEntry> {
        lock.withLock {
            val command = StringBuilder("ONION_CLIENT_AUTH_VIEW").let { sb ->
                if (address != null) {
                    sb.append(SP)
                    sb.append(address)
                }
                sb.append(CLRF)
                sb.toString()
            }

            return processCommand(command) {
                mapNotNull { reply ->
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
        }
    }

    override suspend fun ownershipDrop(): Result<Any?> {
        return lock.withLock {
            try {
                val command = "DROPOWNERSHIP$CLRF"

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun ownershipTake(): Result<Any?> {
        return lock.withLock {
            try {
                val command = "TAKEOWNERSHIP$CLRF"

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

//    override suspend fun resolve(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

    override suspend fun setEvents(events: Set<TorEvent>, extended: Boolean): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("SETEVENTS").let { sb ->
                    if (extended) {
                        sb.append(SP)
                        sb.append("EXTENDED")
                    }
                    if (events.isNotEmpty()) {
                        events.joinTo(sb, separator = SP, prefix = SP)
                    }
                    sb.append(CLRF)
                    sb.toString()
                }

                Result.success(processCommand(command))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun signal(signal: TorControlSignal.Signal): Result<Any?> {
        return lock.withLock {
            try {
                val command = StringBuilder("SIGNAL")
                    .append(SP)
                    .append(signal)
                    .append(CLRF)
                    .toString()

                val result = processCommand(command)

                when (signal) {
                    TorControlSignal.Signal.Halt,
                    TorControlSignal.Signal.Shutdown -> { delay(500L) }
                    else -> { /* no-op */ }
                }

                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

//    override suspend fun streamAttach(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun streamClose(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun streamRedirect(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
//        }
//    }

//    override suspend fun useFeature(): Result<Any?> {
//        return lock.withLock {
//            try {
//                TODO("Not yet implemented")
//            } catch (e: Exception) {
//                Result.failure(e)
//            }
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
