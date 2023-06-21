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

import io.matthewnelson.component.parcelize.Parcelize
import io.matthewnelson.encoding.base32.Base32
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import io.matthewnelson.kmp.tor.common.internal.TorStrings.REDACTED
import io.matthewnelson.kmp.tor.common.internal.descriptorString
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid base32 encoded (without padding '=') x25519 onion client auth private key
 *
 * @see [REGEX] for private key character requirements
 * @see [RealOnionClientAuthPrivateKey_B32_X25519]
 * @throws [IllegalArgumentException] if [value] is not a 52 character base32
 *  encoded (without padding '=') String
 * */
@SealedValueClass
@Suppress("ClassName")
@OptIn(ExperimentalTorApi::class)
sealed interface OnionClientAuthPrivateKey_B32_X25519: OnionClientAuth.PrivateKey {

    companion object {
        @JvmStatic
        val REGEX: Regex = "[${Base32.Default.CHARS_UPPER}]{52}".toRegex()

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(key: String): OnionClientAuthPrivateKey_B32_X25519 {
            return RealOnionClientAuthPrivateKey_B32_X25519(key)
        }
    }
}

@JvmInline
@Parcelize
@OptIn(InternalTorApi::class)
@Suppress("ClassName")
private value class RealOnionClientAuthPrivateKey_B32_X25519(
    override val value: String
): OnionClientAuthPrivateKey_B32_X25519 {

    init {
        require(value.matches(OnionClientAuthPrivateKey_B32_X25519.REGEX)) {
            "value=$REDACTED was not a 52 character base32 encoded (w/o padding '=') String"
        }
    }

    override fun base64(padded: Boolean): String {
        val b64 = decode().encodeToString(Base64.Default)
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

    override fun decode(): ByteArray = value.decodeToByteArray(Base32.Default)

    override fun descriptor(address: OnionAddressV3): String = descriptorString(address)

    override val keyType: OnionClientAuth.Key.Type get() = OnionClientAuth.Key.Type.x25519

    override fun toString(): String = "OnionClientAuthPrivateKey_B32_X25519(value=$REDACTED)"
}
