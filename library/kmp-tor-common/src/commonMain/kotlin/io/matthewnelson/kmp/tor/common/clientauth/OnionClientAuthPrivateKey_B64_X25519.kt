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
package io.matthewnelson.kmp.tor.common.clientauth

import io.matthewnelson.component.base64.Base64
import io.matthewnelson.component.base64.decodeBase64ToArray
import io.matthewnelson.component.encoding.base32.Base32
import io.matthewnelson.component.encoding.base32.encodeBase32
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.util.TorStrings.REDACTED
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid base64 encoded (without padding '=') x25519 onion client auth private key
 *
 * @see [REGEX] for private key character requirements
 * @throws [IllegalArgumentException] if [value] is not a 43 character base64
 *  encoded (without padding '=') String
 * */
@JvmInline
@OptIn(InternalTorApi::class)
@Suppress("ClassName")
value class OnionClientAuthPrivateKey_B64_X25519(
    override val value: String
): OnionClientAuth.PrivateKey {

    init {
        require(value.matches(REGEX)) {
            "value=$REDACTED was not a 43 character base64 encoded (w/o padding '=') String"
        }
    }

    override fun base64(padded: Boolean): String {
        return if (padded) {
            "$value="
        } else {
            value
        }
    }

    override fun base32(padded: Boolean): String {
        val b32 = decode().encodeBase32(Base32.Default)
        return if (padded) {
            b32
        } else {
            b32.dropLast(4)
        }
    }

    override fun decode(): ByteArray {
        return value.decodeBase64ToArray()!!
    }

    override fun descriptor(address: OnionAddressV3): String {
        return descriptorString(address)
    }

    override val keyType: OnionClientAuth.Key.Type get() = OnionClientAuth.Key.Type.x25519

    override fun toString(): String {
        return "OnionClientAuthPrivateKey_B64_X25519(value=$REDACTED)"
    }

    companion object {
        @get:JvmStatic
        val REGEX: Regex get() = "[${Base64.Default.CHARS}]{43}".toRegex()
    }
}
