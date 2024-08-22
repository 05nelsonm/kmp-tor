/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("KotlinRedundantDiagnosticSuppress", "UnusedReceiverParameter")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress.Companion.toOnionAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.ctrl.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply.Error.Companion.toError
import io.matthewnelson.kmp.tor.runtime.core.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.core.key.AuthKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.key.X25519
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PrivateKey.Companion.toX25519PrivateKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKeyOrNull

@Throws(IllegalArgumentException::class, NoSuchElementException::class)
internal fun TorCmdJob<*>.respond(replies: ArrayList<Reply>) {
    // waiters were destroyed while awaiting server response (i.e. EOS)
    if (replies.isEmpty()) {
        error(InterruptedException("CtrlConnection Stream Ended"))
        return
    }

    val success = run {
        var areAllSuccess = true
        for (reply in replies) {
            if (reply is Reply.Success) continue
            areAllSuccess = false
            break
        }

        if (areAllSuccess) {
            @Suppress("UNCHECKED_CAST")
            replies as ArrayList<Reply.Success>
        } else {
            null
        }
    }

    if (success == null) {
        error(replies.toError(name))
        return
    }

    when (cmd) {
        is TorCmd.Authenticate -> completeOK()
        is TorCmd.Config.Get -> cmd.complete(this, success)
        is TorCmd.Config.Load -> completeOK()
        is TorCmd.Config.Reset -> completeOK()
        is TorCmd.Config.Save -> completeOK()
        is TorCmd.Config.Set -> completeOK()
        is TorCmd.DropGuards -> completeOK()
        is TorCmd.Hs.Fetch -> completeOK()
        is TorCmd.Info.Get -> cmd.complete(this, success)
        is TorCmd.MapAddress -> cmd.complete(this, success)
        is TorCmd.Onion.Add -> cmd.complete(this, success)
        is TorCmd.Onion.Delete -> completeSuccess(success)
        is TorCmd.OnionClientAuth.Add -> completeSuccess(success)
        is TorCmd.OnionClientAuth.Remove -> completeSuccess(success)
        is TorCmd.OnionClientAuth.View -> cmd.complete(this, success)
        is TorCmd.Ownership.Drop -> completeOK()
        is TorCmd.Ownership.Take -> completeOK()
        is TorCmd.Resolve -> completeOK()
        is TorCmd.SetEvents -> completeOK()
        is TorCmd.Signal.Dump -> completeOK()
        is TorCmd.Signal.Debug -> completeOK()
        is TorCmd.Signal.NewNym -> completeOK()
        is TorCmd.Signal.ClearDnsCache -> completeOK()
        is TorCmd.Signal.Heartbeat -> completeOK()
        is TorCmd.Signal.Active -> completeOK()
        is TorCmd.Signal.Dormant -> completeOK()
        is TorCmd.Signal.Reload -> completeOK()
        is TorCmd.Signal.Shutdown -> completeOK()
        is TorCmd.Signal.Halt -> completeOK()
    }
}

@Throws(NoSuchElementException::class)
private fun TorCmd.Config.Get.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    val entries = ArrayList<ConfigEntry>(replies.size)
    replies.forEachKvp { key, setting ->
        val option = options.first { it.name.equals(key, ignoreCase = true) }
        entries.add(ConfigEntry(option, setting))
    }
    job.unsafeCast<List<ConfigEntry>>().completion(entries.toImmutableList())
}

private fun TorCmd.Info.Get.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    val map = LinkedHashMap<String, String>(keywords.size, 1.0f)
    replies.forEachKvp { key, value -> map[key] = value }
    job.unsafeCast<Map<String, String>>().completion(map.toImmutableMap())
}

private fun TorCmd.MapAddress.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    val set = LinkedHashSet<AddressMapping.Result>(mappings.size, 1.0f)
    replies.forEachKvp { key, value -> set.add(AddressMapping.Result(key, value)) }
    job.unsafeCast<Set<AddressMapping.Result>>().completion(set.toImmutableSet())
}

@Throws(IllegalArgumentException::class, NoSuchElementException::class)
private fun TorCmd.Onion.Add.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    @Suppress("LocalVariableName")
    var _publicKey: AddressKey.Public? = null
    var privateKey: AddressKey.Private? = null
    val auth = LinkedHashSet<AuthKey.Public>(clientAuth.size, 1.0F)

    // 250 ServiceID=sampleonion4t2pqglbny66wpovyvao3ylc23eileodtevc4b75ikpad
    // 250 PrivateKey=ED25519-V3:[Blob Redacted]
    // 250 ClientAuthV3=[Blob Redacted]
    replies.forEachKvp { key, value ->
        when (key.lowercase()) {
            "serviceid" -> {
                when (keyType) {
                    is ED25519_V3 -> value.toED25519_V3PublicKeyOrNull()
                }?.let { _publicKey = it }
            }
            "privatekey" -> {
                val i = value.indexOf(':')
                if (i == -1) return@forEachKvp

                val keyString = value.substring(i + 1)

                when (keyType) {
                    is ED25519_V3 -> keyString.toED25519_V3PrivateKeyOrNull()
                }?.let { privateKey = it }
            }
            "clientauthv3" -> {
                // Will be a headache when another key type is added.
                val authKey = value.toX25519PublicKeyOrNull() ?: return@forEachKvp
                auth.add(authKey)
            }
        }
    }

    val publicKey = _publicKey
        ?: throw NoSuchElementException("${keyType.algorithm()}.PublicKey was not found in replies")

    val entry = HiddenServiceEntry.of(publicKey, privateKey, auth)
    job.unsafeCast<HiddenServiceEntry>().completion(entry)
}

@Throws(IllegalArgumentException::class, NoSuchElementException::class)
private fun TorCmd.OnionClientAuth.View.complete(job: TorCmdJob<*>, replies: ArrayList<Reply.Success>) {
    // 250 CLIENT {onion address} {algorithm}:[Blob Redacted] Flags=Permanent ClientName=test0
    // 250 CLIENT {onion address} {algorithm}:[Blob Redacted] ClientName=test1
    // 250 CLIENT {onion address} {algorithm}:[Blob Redacted]
    val list = ArrayList<ClientAuthEntry>(replies.size)

    for (reply in replies) {
        if (reply.isOK) continue

        val splits = reply.message.substringAfter("CLIENT ", "").let { after ->
            if (after.isEmpty()) return@let null
            after.split(' ')
        }

        if (splits.isNullOrEmpty()) continue

        val address = splits.elementAtOrNull(0)?.toOnionAddressOrNull()
        val privateKey = splits.elementAtOrNull(1)?.let { keyString ->
            val i = keyString.indexOf(':')
            if (i == -1) return@let null

            when (keyString.substring(0, i)) {
                X25519.algorithm() -> keyString.substring(i + 1).toX25519PrivateKeyOrNull()
                else -> null
            }
        }

        if (address == null) {
            throw NoSuchElementException("address was not found in replies")
        }
        if (privateKey == null) {
            throw NoSuchElementException("private key was not found in replies")
        }

        var clientName: String? = null
        var flags: List<String>? = null

        if (splits.size > 2) {
            for (i in 2 until splits.size) {
                val split = splits[i]
                val iEquals = split.indexOf('=')
                if (iEquals == -1) continue

                when (split.substring(0, iEquals).lowercase()) {
                    "flags" -> {
                        flags = split.substring(iEquals + 1).split(',')
                    }
                    "clientname" -> {
                        clientName = split.substring(iEquals + 1)
                    }
                }
            }
        }

        val entry = ClientAuthEntry.of(
            address = address,
            privateKey = privateKey,
            clientName = clientName,
            flags = flags?.toImmutableSet() ?: emptySet()
        )

        list.add(entry)
    }

    job.unsafeCast<List<ClientAuthEntry>>().completion(list.toImmutableList())
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmdJob<*>.completeOK() {
    unsafeCast<Reply.Success.OK>().completion(Reply.Success.OK)
}

@Suppress("NOTHING_TO_INLINE")
private inline fun TorCmdJob<*>.completeSuccess(success: ArrayList<Reply.Success>) {
    unsafeCast<Reply.Success>().completion(success.first())
}

@Suppress("NOTHING_TO_INLINE", "UNCHECKED_CAST")
private inline fun <T: Any> TorCmdJob<*>.unsafeCast(): TorCmdJob<T> = this as TorCmdJob<T>

@Suppress("NOTHING_TO_INLINE")
private inline fun ArrayList<Reply.Success>.forEachKvp(
    block: (key: String, value: String) -> Unit,
) {
    for (reply in this) {
        if (reply.isOK) continue

        val kvp = reply.message
        val i = kvp.indexOf('=')

        if (i == -1) {
            block(kvp, "")
        } else {
            block(kvp.substring(0, i), kvp.substring(i + 1))
        }
    }
}
