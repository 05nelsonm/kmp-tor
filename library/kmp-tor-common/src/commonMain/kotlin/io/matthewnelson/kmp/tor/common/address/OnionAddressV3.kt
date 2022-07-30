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
import io.matthewnelson.kmp.tor.common.internal.findOnionAddressFromUrl
import io.matthewnelson.kmp.tor.common.internal.stripBaseEncoding
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

        /**
         * Attempts to find the [OnionAddressV3] for a given
         * string. This could be a URL, a newly base32 encoded
         * string, or the properly formatted address itself.
         *
         * @see [findOnionAddressFromUrl]
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): OnionAddressV3 {
            val stripped = address
                // Treat it as a URL at first and attempt to
                // strip out everything but the onion address.
                .findOnionAddressFromUrl()
                // If it's a freshly encoded value, it could be
                // still formatted improperly as all uppercase.
                .lowercase()

            return RealOnionAddressV3(stripped)
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
        message = "Replaced by canonicalHostname method",
        replaceWith = ReplaceWith("canonicalHostname()"),
        level = DeprecationLevel.WARNING
    )
    override val valueDotOnion: String get() = "$value.onion"

    override fun canonicalHostname(): String = "$value.onion"

    override fun decode(): ByteArray = value.uppercase().decodeBase32ToArray(Base32.Default)!!

    override fun toString(): String = "OnionAddressV3(value=$value)"
}
