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

import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth.Key.Type
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuthPrivateKey_B64_X25519
import io.matthewnelson.kmp.tor.common.util.TorStrings.REDACTED
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlOnionClientAuthView
import kotlin.jvm.JvmField

/**
 * Holder class for raw data returned from [TorControlOnionClientAuthView]
 *
 * [address] is an [OnionAddressV3]
 * [keyType] is [Type.x25519]
 * [privateKey] is [OnionClientAuthPrivateKey_B64_X25519]
 * [clientName] is [ClientName] or null
 * [flags] are a list of [TorControlOnionClientAuth.Flag] or null
 * */
class ClientAuthEntry(
    @JvmField
    val address: String,
    @JvmField
    val keyType: String,
    @JvmField
    val privateKey: String,
    @JvmField
    val clientName: String?,
    @JvmField
    val flags: List<String>?,
) {

    override fun equals(other: Any?): Boolean {
        return  other != null                       &&
                other is ClientAuthEntry            &&
                other.address == address            &&
                other.keyType == keyType            &&
                other.privateKey == privateKey      &&
                other.clientName == clientName      &&
                other.flags?.size == flags?.size    &&
                other.flags.let { a ->
                    flags.let { b ->
                        (a.isNullOrEmpty() && b.isNullOrEmpty())                ||
                        (a?.containsAll( elements = b ?: emptyList()) == true   &&
                         b?.containsAll(elements = a) == true)
                    }
                }
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 31 + address.hashCode()
        result = result * 31 + keyType.hashCode()
        result = result * 31 + privateKey.hashCode()
        result = result * 31 + clientName.hashCode()
        flags?.forEach { result = result * 31 + it.hashCode() }
        return result
    }

    @OptIn(InternalTorApi::class)
    override fun toString(): String {
        return "ClientAuthEntry(address=$REDACTED,keyType=$keyType," +
                "privateKey=$REDACTED,clientName=$clientName," +
                "flags=${flags?.joinToString()})"
    }
}
