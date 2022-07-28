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
package io.matthewnelson.kmp.tor.common.address

import io.matthewnelson.component.encoding.base32.Base32
import io.matthewnelson.component.encoding.base32.decodeBase32ToArray
import io.matthewnelson.component.parcelize.Parcelize
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import io.matthewnelson.kmp.tor.common.internal.stripAddress
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a 56 character v3 onion address without the scheme or `.onion`
 * appended. This is only a preliminary check for character and length
 * correctness, and does not check validity of bytes.
 *
 * @see [REGEX] for onion address character requirements
 * @see [RealOnionAddressV3]
 * @throws [IllegalArgumentException] if [value] is not a v3 onion address
 * */
@SealedValueClass
@OptIn(ExperimentalTorApi::class)
sealed interface OnionAddressV3: OnionAddress {

    companion object {
        @JvmStatic
        val REGEX: Regex = "[a-z2-7]{56}".toRegex()

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(address: String): OnionAddressV3 {
            return RealOnionAddressV3(address)
        }

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): OnionAddressV3 {
            return RealOnionAddressV3(address.stripAddress())
        }

        @JvmStatic
        fun fromStringOrNull(address: String): OnionAddressV3? {
            return try {
                fromString(address)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

@JvmInline
@Parcelize
private value class RealOnionAddressV3(override val value: String): OnionAddressV3 {

    init {
        require(value.matches(OnionAddressV3.REGEX)) {
            "$value is not a valid onion address"
        }
    }

    @Deprecated(
        message = "Use hostname",
        replaceWith = ReplaceWith("hostname"),
        level = DeprecationLevel.WARNING
    )
    override val valueDotOnion: String get() = "$value.onion"

    override val hostname: String get() = "$value.onion"

    override fun decode(): ByteArray {
        return value.uppercase().decodeBase32ToArray(Base32.Default)!!
    }

    override fun toString(): String {
        return "OnionAddressV3(value=$value)"
    }
}
