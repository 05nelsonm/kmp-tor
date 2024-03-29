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

import io.matthewnelson.component.parcelize.Parcelable
import io.matthewnelson.component.parcelize.Parcelize
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.internal.stripBaseEncoding
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Onion Client Authentication public/private keys.
 *
 * Tor utilizes base32 and base64 encoded key blobs for different things. For example,
 * interacting with Tor over its control port requires a base64 encoded private key,
 * while persisting the key to the file system requires it to be base32 encoded for Tor
 * to read on startup (otherwise Tor will throw errors and not start).
 *
 * @see [Key]
 * @see [PublicKey]
 * @see [PrivateKey]
 * @see [KeyPair]
 * */
class OnionClientAuth private constructor() {

    /**
     * Base interface to [PublicKey] & [PrivateKey]
     * */
    sealed interface Key: Parcelable {

        val value: String

        /**
         * Returns the raw base64 encoded (with optional padding) [value]
         * */
        fun base64(padded: Boolean = false): String

        /**
         * Returns the raw base32 encoded (with optional padding) [value]
         * */
        fun base32(padded: Boolean = false): String

        /**
         * Returns the raw bytes for the given [value]
         * */
        fun decode(): ByteArray

        val keyType: Type

        /**
         * Currently, the only client auth key type is x25519
         * */
        @Suppress("EnumEntryName")
        enum class Type {
            x25519;
        }
    }

    /**
     * Denotes an onion client auth public key.
     *
     * @see [OnionClientAuthPublicKey_B32_X25519]
     * @see [OnionClientAuthPublicKey_B64_X25519]
     * */
    sealed interface PublicKey: Key {

        /**
         * Produces the needed descriptor for the given [PublicKey] in
         * the format expected by Tor.
         *
         * descriptor:<key type>:<public key>
         *
         * @see [io.matthewnelson.kmp.tor.common.internal.descriptorString]
         * */
        fun descriptor(): String

        companion object {
            const val DESCRIPTOR = "descriptor"

            @JvmStatic
            @Throws(IllegalArgumentException::class)
            fun fromString(key: String): PublicKey {
                val stripped = key.stripBaseEncoding()

                try {
                    return OnionClientAuthPublicKey_B64_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                try {
                    return OnionClientAuthPublicKey_B32_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                throw IllegalArgumentException("String was not a OnionClientAuth.PublicKey")
            }

            @JvmStatic
            fun fromStringOrNull(key: String): PublicKey? {
                return try {
                    fromString(key)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    /**
     * Denotes an onion client auth private key.
     *
     * @see [OnionClientAuthPrivateKey_B32_X25519]
     * @see [OnionClientAuthPrivateKey_B64_X25519]
     * */
    sealed interface PrivateKey: Key {

        /**
         * Produces the needed descriptor for the given [PrivateKey] in
         * the format expected by Tor,
         *
         *  <address>:<key type>:<private key>
         *
         * @see [io.matthewnelson.kmp.tor.common.internal.descriptorString]
         * */
        fun descriptor(address: OnionAddressV3): String

        companion object {
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            fun fromString(key: String): PrivateKey {
                val stripped = key.stripBaseEncoding()

                try {
                    return OnionClientAuthPrivateKey_B64_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                try {
                    return OnionClientAuthPrivateKey_B32_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                throw IllegalArgumentException("String was not a OnionClientAuth.PrivateKey")
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
    }

    @Parcelize
    class KeyPair(
        @JvmField
        val publicKey: PublicKey,
        @JvmField
        val privateKey: PrivateKey
    ): Parcelable {

        override fun equals(other: Any?): Boolean {
            return  other != null                   &&
                    other is KeyPair                &&
                    other.publicKey == publicKey    &&
                    other.privateKey == privateKey
        }

        override fun hashCode(): Int {
            var result = 17
            result = result * 31 + publicKey.hashCode()
            result = result * 31 + privateKey.hashCode()
            return result
        }

        override fun toString(): String = "KeyPair(publicKey=$publicKey, privateKey=$privateKey)"
    }

//    sealed interface KeyFactory: OnionClientAuth {
//        fun generateKeyPair(keyType: Key.Type): KeyPair
//    }
//
//    companion object {
//        @JvmStatic
//        fun keyFactory(): KeyFactory {
//
//        }
//    }
}
