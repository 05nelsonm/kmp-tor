/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("ClassName", "FunctionName")

package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.containsNon0Byte
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.V3.Companion.toOnionAddressV3OrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.tryDecodeOrNull
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.V3.Companion.toOnionAddressV3
import org.kotlincrypto.error.InvalidKeyException
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * An [ED25519_V3] [KeyType]. Also known as "Version 3 Hidden Services".
 * */
public object ED25519_V3: KeyType.Address<ED25519_V3.PublicKey, ED25519_V3.PrivateKey>() {

    /**
     * `ED25519-V3`
     * */
    public override fun algorithm(): String = "ED25519-V3"

    /**
     * The public key of a Hidden Service (i.e. a [OnionAddress.V3] wrapped in
     * [AddressKey.Public] functionality).
     *
     * @see [OnionAddress.V3]
     * @see [toED25519_V3PublicKey]
     * @see [toED25519_V3PublicKeyOrNull]
     * */
    public class PublicKey(address: OnionAddress.V3): AddressKey.Public(address) {

        /**
         * `ED25519-V3`
         * */
        public override fun algorithm(): String = ED25519_V3.algorithm()

        public override fun address(): OnionAddress.V3 = super.address() as OnionAddress.V3

        public companion object {

            /**
             * Parses a String for an ED25519-V3 public key.
             *
             * String can be a URL containing a v3 `.onion` address, the v3 `.onion`
             * address itself, or Base 16/32/64 encoded raw key value.
             *
             * @return [ED25519_V3.PublicKey]
             * @throws [InvalidKeyException] if no key is found
             * */
            @JvmStatic
            @JvmName("get")
            public fun String.toED25519_V3PublicKey(): PublicKey {
                // Sanity check for smallest possible input length (a Base64 encoded 32 byte key)
                BASE_64.config.encodeOutSize(BYTE_SIZE.toLong()).let { minLength ->
                    if (length >= minLength) return@let
                    throw InvalidKeyException("Invalid length. actual[$length] vs min[$minLength]")
                }

                // Peek for a colon, indicative the presence of a URL scheme (ws:// to https://)
                run {
                    var i = 2
                    var hasColon = false
                    while (!hasColon && i < 6) {
                        hasColon = this[i++] == ':'
                    }
                    if (!hasColon) return@run

                    // Colon present. Base decoding will all fail. Must be a v3 onion address or fail.
                    try {
                        return PublicKey(toOnionAddressV3())
                    } catch (e: IllegalArgumentException) {
                        throw InvalidKeyException("[$this] is not an ${algorithm()} public key", e)
                    }
                }

                // Try Base64 first (most probable encoding)
                var b = decodeToByteArrayOrNull(BASE_64)
                b = when (b?.size) {
                    null -> null
                    BYTE_SIZE -> b
                    OnionAddress.V3.BYTE_SIZE -> {
                        val a = b.toOnionAddressV3OrNull()
                        if (a != null) return PublicKey(a)
                        null
                    }
                    else -> null
                }
                if (b == null) {
                    // Try Base32 if was not Base64 encoded
                    b = decodeToByteArrayOrNull(BASE_32)
                    b = when (b?.size) {
                        null -> null
                        BYTE_SIZE -> b
                        OnionAddress.V3.BYTE_SIZE -> {
                            val a = b.toOnionAddressV3OrNull()
                            if (a != null) return PublicKey(a)
                            null
                        }
                        else -> null
                    }
                }
                if (b == null) {
                    // Lastly, try Base16
                    b = decodeToByteArrayOrNull(BASE_16)
                    b = when (b?.size) {
                        null -> null
                        BYTE_SIZE -> b
                        OnionAddress.V3.BYTE_SIZE -> {
                            val a = b.toOnionAddressV3OrNull()
                            if (a != null) return PublicKey(a)
                            null
                        }
                        else -> null
                    }
                }

                if (b == null) {
                    // Last resort. Check if it's something like <onion-address>.onion
                    try {
                        return PublicKey(toOnionAddressV3())
                    } catch (e: IllegalArgumentException) {
                        throw InvalidKeyException("[$this] is not an ${algorithm()} public key", e)
                    }
                }

                return b.toED25519_V3PublicKey()
            }

            /**
             * Transforms provided bytes into a ED25519-V3 public key.
             *
             * @return [ED25519_V3.PublicKey]
             * @throws [InvalidKeyException] if byte array size is inappropriate
             * */
            @JvmStatic
            @JvmName("get")
            public fun ByteArray.toED25519_V3PublicKey(): PublicKey {
                val address = when (size) {
                    BYTE_SIZE -> {
                        try {
                            OnionAddress.V3.fromED25519(this)
                        } catch (e: IllegalArgumentException) {
                            throw InvalidKeyException(e)
                        }
                    }
                    OnionAddress.V3.BYTE_SIZE -> {
                        try {
                            toOnionAddressV3()
                        } catch (e: IllegalArgumentException) {
                            throw InvalidKeyException(e)
                        }
                    }
                    else -> throw InvalidKeyException("Invalid array size[$size]")
                }

                return PublicKey(address)
            }

            /**
             * Parses a String for an ED25519-V3 public key.
             *
             * String can be a URL containing a v3 `.onion` address, the v3 `.onion`
             * address itself, or Base 16/32/64 encoded raw key value.
             *
             * @return [ED25519_V3.PublicKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toED25519_V3PublicKeyOrNull(): PublicKey? = try {
                toED25519_V3PublicKey()
            } catch (e: InvalidKeyException) {
                null
            }

            /**
             * Transforms provided bytes into a ED25519-V3 public key.
             *
             * @return [ED25519_V3.PublicKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toED25519_V3PublicKeyOrNull(): PublicKey? = try {
                toED25519_V3PublicKey()
            } catch (e: InvalidKeyException) {
                null
            }

            internal const val BYTE_SIZE: Int = 32
        }
    }

    /**
     * The private key of a Hidden Service.
     *
     * @see [toED25519_V3PrivateKey]
     * @see [toED25519_V3PrivateKeyOrNull]
     * */
    public class PrivateKey private constructor(
        key: ByteArray,
    ): AddressKey.Private(key) {

        /**
         * `ED25519-V3`
         * */
        public override fun algorithm(): String = ED25519_V3.algorithm()

        public companion object {

            /**
             * Parses a String for an ED25519-V3 private key.
             *
             * String can be a Base 16/32/64 encoded raw value.
             *
             * @return [ED25519_V3.PrivateKey]
             * @throws [InvalidKeyException] if no key is found
             * */
            @JvmStatic
            @JvmName("get")
            public fun String.toED25519_V3PrivateKey(): PrivateKey {
                val decoded = tryDecodeOrNull(
                    expectedSize = BYTE_SIZE,
                    decoders = listOf(BASE_64, BASE_32, BASE_16),
                ) ?: throw InvalidKeyException("Tried base 16/32/64 decoding, but failed to find a $BYTE_SIZE byte key")
                if (!decoded.containsNon0Byte(BYTE_SIZE)) throw InvalidKeyException("Key is blank (all 0 bytes)")
                return PrivateKey(decoded)
            }

            /**
             * Transforms provided bytes into a ED25519-V3 private key.
             *
             * @return [ED25519_V3.PrivateKey]
             * @throws [InvalidKeyException] if byte array size is inappropriate
             * */
            @JvmStatic
            @JvmName("get")
            public fun ByteArray.toED25519_V3PrivateKey(): PrivateKey {
                if (size != BYTE_SIZE) throw InvalidKeyException("Invalid array size[$size]")
                if (!containsNon0Byte(BYTE_SIZE)) throw InvalidKeyException("Key is blank (all 0 bytes)")
                return PrivateKey(copyOf())
            }

            /**
             * Parses a String for an ED25519-V3 private key.
             *
             * String can be a Base 16/32/64 encoded raw value.
             *
             * @return [ED25519_V3.PrivateKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toED25519_V3PrivateKeyOrNull(): PrivateKey? = try {
                toED25519_V3PrivateKey()
            } catch (_: InvalidKeyException) {
                null
            }

            /**
             * Transforms provided bytes into a ED25519-V3 private key.
             *
             * @return [ED25519_V3.PrivateKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toED25519_V3PrivateKeyOrNull(): PrivateKey? = try {
                toED25519_V3PrivateKey()
            } catch (_: InvalidKeyException) {
                null
            }

            private const val BYTE_SIZE = 64
        }
    }
}
