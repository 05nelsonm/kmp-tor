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
import io.matthewnelson.component.base64.encodeBase64
import io.matthewnelson.component.encoding.base32.Base32
import io.matthewnelson.component.encoding.base32.decodeBase32ToArray
import io.matthewnelson.kmp.tor.common.util.descriptorString
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid base32 encoded (without padding '=') x25519 onion client auth public key
 *
 * @see [OnionClientAuthPrivateKey_B32_X25519.REGEX] for public key character requirements
 * @see [RealOnionClientAuthPublicKey_B32_X25519]
 * @throws [IllegalArgumentException] if [value] is not a 52 character base32
 *  encoded (without padding '=') String
 * */
@Suppress("ClassName")
sealed interface OnionClientAuthPublicKey_B32_X25519: OnionClientAuth.PublicKey {

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(key: String): OnionClientAuthPublicKey_B32_X25519 {
            return RealOnionClientAuthPublicKey_B32_X25519(key)
        }
    }
}

@JvmInline
@Suppress("ClassName")
private value class RealOnionClientAuthPublicKey_B32_X25519(
    override val value: String
): OnionClientAuthPublicKey_B32_X25519 {

    init {
        require(value.matches(OnionClientAuthPrivateKey_B32_X25519.REGEX)) {
            "$value is not a 52 character base32 encoded (w/o padding '=') String"
        }
    }

    override fun base64(padded: Boolean): String {
        val b64 = decode().encodeBase64(Base64.Default)
        return if (padded) {
            b64
        } else {
            b64.dropLast(1)
        }
    }

    override fun base32(padded: Boolean): String {
        return if (padded) {
            "$value===="
        } else {
            value
        }
    }

    override fun decode(): ByteArray {
        return value.decodeBase32ToArray(Base32.Default)!!
    }

    override fun descriptor(): String {
        return descriptorString()
    }

    override val keyType: OnionClientAuth.Key.Type get() = OnionClientAuth.Key.Type.x25519

    override fun toString(): String {
        return "OnionClientAuthPublicKey_B32_X25519(value=$value)"
    }
}
