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

import io.matthewnelson.component.parcelize.Parcelable
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.internal.TorStrings.REDACTED
import io.matthewnelson.kmp.tor.common.internal.findOnionAddressFromUrl
import io.matthewnelson.kmp.tor.common.internal.stripBaseEncoding
import kotlin.jvm.JvmStatic

/**
 * Base interface for denoting a String value is an [OnionAddress]
 *
 * @see [Address]
 * @see [OnionAddressV3]
 * */
sealed interface OnionAddress: Address {

    /**
     * Returns the raw bytes for the given [value]
     * */
    fun decode(): ByteArray

    companion object {

        /**
         * Attempts to find the [OnionAddress] for a given
         * string. This could be a URL, a newly base32 encoded
         * string, or the properly formatted address itself.
         *
         * @see [findOnionAddressFromUrl]
         * @see [fromStringOrNull]
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): OnionAddress {
            val stripped = address
                // Treat it as a URL at first and attempt to
                // strip out everything but the onion address.
                .findOnionAddressFromUrl()
                // If it's a freshly encoded value, it could be
                // still formatted improperly as all uppercase.
                .lowercase()

            try {
                return OnionAddressV3(stripped)
            } catch (_: IllegalArgumentException) {}

            throw IllegalArgumentException("Failed to find a valid OnionAddress from $address")
        }

        @JvmStatic
        fun fromStringOrNull(address: String): OnionAddress? {
            return try {
                fromString(address)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    sealed interface PrivateKey: Parcelable {

        val value: String

        /**
         * Returns the raw bytes for the given [value]
         * */
        fun decode(): ByteArray

        val keyType: Type

        companion object {
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            fun fromString(key: String): PrivateKey {
                val stripped = key.stripBaseEncoding()

                try {
                    return OnionAddressV3PrivateKey_ED25519(stripped)
                } catch (_: IllegalArgumentException) {}

                @OptIn(InternalTorApi::class)
                throw IllegalArgumentException("Failed to find a valid OnionAddress.Private key from string: $REDACTED")
            }

            @JvmStatic
            fun fromStringOrNull(key: String): PrivateKey? {
                return try {
                    fromString(key)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }

        @Suppress("ClassName")
        sealed class Type {

            override fun toString(): String {
                return when(this) {
                    is ED25519_V3 -> ED25519_V3_ACTUAL
                }
            }

            companion object {
                private const val ED25519_V3_ACTUAL = "ED25519-V3"
                private const val ED25519_V3_CLASS = "ED25519_V3"

                @JvmStatic
                @Throws(IllegalArgumentException::class)
                fun valueOf(value: String): Type {
                    return when (value) {
                        ED25519_V3_ACTUAL,
                        ED25519_V3_CLASS -> ED25519_V3
                        else -> {
                            throw IllegalArgumentException(
                                "Failed to determine OnionAddress.PrivateKey.Type from $value"
                            )
                        }
                    }
                }
            }

            object ED25519_V3: Type()
        }
    }

    @Deprecated(
        message = "Replaced by canonicalHostname method",
        replaceWith = ReplaceWith("canonicalHostname()"),
        level = DeprecationLevel.WARNING,
    )
    val valueDotOnion: String
}
