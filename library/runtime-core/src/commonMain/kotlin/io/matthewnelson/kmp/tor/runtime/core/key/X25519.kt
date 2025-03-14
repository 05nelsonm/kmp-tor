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
package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.kmp.tor.runtime.core.internal.containsNon0Byte
import io.matthewnelson.kmp.tor.runtime.core.internal.generateX25519KeyPair
import io.matthewnelson.kmp.tor.runtime.core.internal.tryDecodeOrNull
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKeyOrNull
import org.kotlincrypto.error.InvalidKeyException
import org.kotlincrypto.random.CryptoRand
import org.kotlincrypto.random.RandomnessProcurementException
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * An [X25519] [KeyType.Auth], utilized by tor for Version 3 Client Authentication.
 * */
public object X25519: KeyType.Auth<X25519.PublicKey, X25519.PrivateKey>() {

    /**
     * `x25519`
     * */
    public override fun algorithm(): String = "x25519"

    /**
     * Generates a new x25519 key pair, suitable for use with v3 client authentication.
     *
     * @throws [RandomnessProcurementException]
     * */
    @JvmStatic
    public fun generateKeyPair(): Pair<PublicKey, PrivateKey> = CryptoRand.Default.generateX25519KeyPair()

    /**
     * A 32 byte x25519 public key.
     *
     * This would be the key a Hidden Service operator adds, to only allow
     * connections from tor clients who have the [X25519.PrivateKey] associated
     * with this [X25519.PublicKey].
     *
     * @see [toX25519PublicKey]
     * @see [toX25519PublicKeyOrNull]
     * */
    public class PublicKey private constructor(key: ByteArray): AuthKey.Public(key) {

        /**
         * `x25519`
         * */
        public override fun algorithm(): String = X25519.algorithm()

        public companion object {

            /**
             * Parses a String for a x25519 public key.
             *
             * String can be a Base 16/32/64 encoded raw value.
             *
             * @return [X25519.PublicKey]
             * @throws [InvalidKeyException] if no key is found
             * */
            @JvmStatic
            @JvmName("get")
            public fun String.toX25519PublicKey(): PublicKey {
                val decoded = tryDecodeOrNull(
                    expectedSize = BYTE_SIZE,
                    decoders = listOf(BASE_64, BASE_32, BASE_16),
                ) ?: throw InvalidKeyException("Tried base 16/32/64 decoding, but failed to find a $BYTE_SIZE byte key")
                if (!decoded.containsNon0Byte(BYTE_SIZE)) throw InvalidKeyException("Key is blank (all 0 bytes)")
                return PublicKey(decoded)
            }

            /**
             * Transforms provided bytes into a x25519 public key.
             *
             * @return [X25519.PublicKey]
             * @throws [InvalidKeyException] if byte array size is inappropriate
             * */
            @JvmStatic
            @JvmName("get")
            public fun ByteArray.toX25519PublicKey(): PublicKey {
                if (size != BYTE_SIZE) throw InvalidKeyException("Invalid array size[$size]")
                if (!containsNon0Byte(BYTE_SIZE)) throw InvalidKeyException("Key is blank (all 0 bytes)")
                return PublicKey(copyOf())
            }

            /**
             * Parses a String for a x25519 public key.
             *
             * String can be a Base 16/32/64 encoded raw value.
             *
             * @return [X25519.PublicKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toX25519PublicKeyOrNull(): PublicKey? = try {
                toX25519PublicKey()
            } catch (_: InvalidKeyException) {
                null
            }

            /**
             * Transforms provided bytes into a x25519 public key.
             *
             * @return [X25519.PublicKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toX25519PublicKeyOrNull(): PublicKey? = try {
                toX25519PublicKey()
            } catch (_: InvalidKeyException) {
                null
            }

            internal const val BYTE_SIZE = 32
        }
    }

    /**
     * A 32 byte x25519 private key.
     *
     * This would be the key added to a tor client by a user who wishes to
     * connect to a Hidden Service that has been configured using the
     * [X25519.PublicKey] associated with this [X25519.PrivateKey].
     *
     * @see [toX25519PublicKey]
     * @see [toX25519PublicKeyOrNull]
     * */
    public class PrivateKey private constructor(key: ByteArray): AuthKey.Private(key) {

        /**
         * `x25519`
         * */
        public override fun algorithm(): String = X25519.algorithm()

        public companion object {

            /**
             * Parses a String for a x25519 private key.
             *
             * String can be a Base 16/32/64 encoded raw value.
             *
             * @return [X25519.PrivateKey]
             * @throws [InvalidKeyException] if no key is found
             * */
            @JvmStatic
            @JvmName("get")
            public fun String.toX25519PrivateKey(): PrivateKey {
                val decoded = tryDecodeOrNull(
                    expectedSize = BYTE_SIZE,
                    decoders = listOf(BASE_64, BASE_32, BASE_16),
                ) ?: throw InvalidKeyException("Tried base 16/32/64 decoding, but failed to find a $BYTE_SIZE byte key")
                if (!decoded.containsNon0Byte(BYTE_SIZE)) throw InvalidKeyException("Key is blank (all 0 bytes)")
                return PrivateKey(decoded)
            }

            /**
             * Transforms provided bytes into a x25519 private key.
             *
             * @return [X25519.PrivateKey]
             * @throws [InvalidKeyException] if byte array size is inappropriate
             * */
            @JvmStatic
            @JvmName("get")
            public fun ByteArray.toX25519PrivateKey(): PrivateKey {
                if (size != BYTE_SIZE) throw InvalidKeyException("Invalid array size[$size]")
                if (!containsNon0Byte(BYTE_SIZE)) throw InvalidKeyException("Key is blank (all 0 bytes)")
                return PrivateKey(copyOf())
            }

            /**
             * Parses a String for a x25519 private key.
             *
             * String can be a Base 16/32/64 encoded raw value.
             *
             * @return [X25519.PrivateKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toX25519PrivateKeyOrNull(): PrivateKey? = try {
                toX25519PrivateKey()
            } catch (_: InvalidKeyException) {
                null
            }

            /**
             * Transforms provided bytes into a x25519 private key.
             *
             * @return [X25519.PrivateKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toX25519PrivateKeyOrNull(): PrivateKey? = try {
                toX25519PrivateKey()
            } catch (_: InvalidKeyException) {
                null
            }

            internal const val BYTE_SIZE = 32
        }

        @JvmSynthetic
        internal override fun isCompatibleWith(
            addressKey: AddressKey.Public
        ): Boolean = when (addressKey) {
            is ED25519_V3.PublicKey -> true
        }
    }
}
