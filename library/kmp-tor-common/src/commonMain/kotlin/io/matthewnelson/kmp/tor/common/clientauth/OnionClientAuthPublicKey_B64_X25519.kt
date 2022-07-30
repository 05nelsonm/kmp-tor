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

import io.matthewnelson.component.base64.decodeBase64ToArray
import io.matthewnelson.component.encoding.base32.Base32
import io.matthewnelson.component.encoding.base32.encodeBase32
import io.matthewnelson.component.parcelize.Parcelize
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import io.matthewnelson.kmp.tor.common.internal.descriptorString
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid base64 encoded (without padding '=') x25519 onion client auth public key
 *
 * @see [OnionClientAuthPrivateKey_B64_X25519.REGEX] for public key character requirements
 * @see [RealOnionClientAuthPublicKey_B64_X25519]
 * @throws [IllegalArgumentException] if [value] is not a 43 character base64
 *  encoded (without padding '=') String
 * */
@SealedValueClass
@Suppress("ClassName")
@OptIn(ExperimentalTorApi::class)
sealed interface OnionClientAuthPublicKey_B64_X25519: OnionClientAuth.PublicKey {

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(key: String): OnionClientAuthPublicKey_B64_X25519 {
            return RealOnionClientAuthPublicKey_B64_X25519(key)
        }
    }
}

@JvmInline
@Parcelize
@Suppress("ClassName")
private value class RealOnionClientAuthPublicKey_B64_X25519(
    override val value: String
): OnionClientAuthPublicKey_B64_X25519 {

    init {
        require(value.matches(OnionClientAuthPrivateKey_B64_X25519.REGEX)) {
            "$value is not a 43 character base64 encoded (w/o padding '=') String"
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

    override fun decode(): ByteArray = value.decodeBase64ToArray()!!

    override fun descriptor(): String = descriptorString()

    override val keyType: OnionClientAuth.Key.Type get() = OnionClientAuth.Key.Type.x25519

    override fun toString(): String = "OnionClientAuthPublicKey_B64_X25519(value=$value)"
}
