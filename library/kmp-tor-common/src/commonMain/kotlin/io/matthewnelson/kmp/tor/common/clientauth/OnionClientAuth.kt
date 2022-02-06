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

import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Onion Client Authentication public/private keys.
 *
 * Tor utilizes base32 and base64 encoded key blobs for different things. For example,
 * interacting with Tor over it's control port requires a base64 encoded private key,
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
    sealed interface Key {

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
         * @see [PublicKey.descriptorString]
         * */
        fun descriptor(): String

        companion object {
            const val DESCRIPTOR = "descriptor"

            @JvmStatic
            @Throws(IllegalArgumentException::class)
            fun fromString(string: String): PublicKey {
                val stripped = string.stripString()

                try {
                    return OnionClientAuthPublicKey_B64_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                try {
                    return OnionClientAuthPublicKey_B32_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                throw IllegalArgumentException("String was not a OnionClientAuth.PublicKey")
            }

            @JvmStatic
            fun fromStringOrNull(string: String): PublicKey? {
                return try {
                    fromString(string)
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
         * @see [PrivateKey.descriptorString]
         * */
        fun descriptor(address: OnionAddressV3): String

        companion object {
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            fun fromString(string: String): PrivateKey {
                val stripped = string.stripString()

                try {
                    return OnionClientAuthPrivateKey_B64_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                try {
                    return OnionClientAuthPrivateKey_B32_X25519(stripped)
                } catch (_: IllegalArgumentException) {}

                throw IllegalArgumentException("String was not a OnionClientAuth.PrivateKey")
            }

            @JvmStatic
            fun fromStringOrNull(string: String): PrivateKey? {
                return try {
                    fromString(string)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    class KeyPair(val publicKey: PublicKey, val privateKey: PrivateKey) {

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

        override fun toString(): String {
            return "KeyPair(publicKey=$publicKey, privateKey=$privateKey)"
        }
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

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun OnionClientAuth.PrivateKey.descriptorString(address: OnionAddressV3): String =
    StringBuilder(address.value)
        .append(':')
        .append(keyType)
        .append(':')
        .append(value)
        .toString()

@JvmSynthetic
@Suppress("nothing_to_inline")
internal inline fun OnionClientAuth.PublicKey.descriptorString(): String =
    StringBuilder(OnionClientAuth.PublicKey.DESCRIPTOR)
        .append(':')
        .append(keyType)
        .append(':')
        .append(value)
        .toString()

@Suppress("nothing_to_inline")
private inline fun String.stripString(): String {
    var limit = length

    // Disregard padding and/or whitespace from end of string
    while (limit > 0) {
        val c = this[limit - 1]
        if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
            break
        }
        limit--
    }

    return this.substring(0, limit).trimStart()
}
