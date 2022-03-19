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

import io.matthewnelson.kmp.tor.common.util.separateSchemeFromAddress
import io.matthewnelson.kmp.tor.common.util.stripString
import kotlin.jvm.JvmStatic

/**
 * Base interface for denoting a String value is an [OnionAddress]
 *
 * @see [OnionAddressV3]
 * */
sealed interface OnionAddress {
    val value: String

    /**
     * Appends .onion to the given [value]
     * */
    val valueDotOnion: String

    companion object {
        @JvmStatic
        fun fromStringOrNull(string: String): OnionAddress? {
            val address = string
                .separateSchemeFromAddress()
                .second
                .substringBefore('.')

            try {
                return OnionAddressV3(address)
            } catch (e: IllegalArgumentException) {}

            return null
        }
    }

    sealed interface PrivateKey {

        val value: String

        /**
         * Returns the raw bytes for the given [value]
         * */
        fun decode(): ByteArray

        val keyType: Type

        companion object {
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            fun fromString(value: String): PrivateKey {
                val stripped = value.stripString()

                try {
                    return OnionAddressV3PrivateKey_ED25519(stripped)
                } catch (_: IllegalArgumentException) {}

                throw IllegalArgumentException("String was not an OnionAddress.PrivateKey")
            }

            @JvmStatic
            fun fromStringOrNull(value: String): PrivateKey? {
                return try {
                    fromString(value)
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
}
