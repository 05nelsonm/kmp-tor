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

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress.V3.Companion.toOnionAddressV3OrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.tryDecodeOrNull
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKeyOrNull
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
             * address itself, or Base 16/32/64 encoded raw value.
             *
             * @return [ED25519_V3.PublicKey]
             * @throws [IllegalArgumentException] if no key is found
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toED25519_V3PublicKey(): PublicKey {
                return toED25519_V3PublicKeyOrNull()
                    ?: throw IllegalArgumentException("$this is not an ${algorithm()} public key")
            }

            /**
             * Transforms provided bytes into a ED25519-V3 public key.
             *
             * @return [ED25519_V3.PublicKey]
             * @throws [IllegalArgumentException] if byte array size is inappropriate
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toED25519_V3PublicKey(): PublicKey {
                return toED25519_V3PublicKeyOrNull()
                    ?: throw IllegalArgumentException("bytes are not an ${algorithm()} public key")
            }

            /**
             * Parses a String for an ED25519-V3 public key.
             *
             * String can be a URL containing a v3 `.onion` address, the v3 `.onion`
             * address itself, or Base 16/32/64 encoded raw value.
             *
             * @return [ED25519_V3.PublicKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toED25519_V3PublicKeyOrNull(): PublicKey? {
                var address = toOnionAddressV3OrNull()

                // If it wasn't a URL or base32 encoded, check if it's
                // formatted as base16 or 64
                if (address == null) {
                    address = tryDecodeOrNull(
                        expectedSize = OnionAddress.V3.BYTE_SIZE,
                        decoders = listOf(Base16, Base64.Default)
                    )?.toOnionAddressV3OrNull()
                }

                if (address == null) return null
                return PublicKey(address)
            }

            /**
             * Transforms provided bytes into a ED25519-V3 public key.
             *
             * @return [ED25519_V3.PublicKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toED25519_V3PublicKeyOrNull(): PublicKey? {
                val address = toOnionAddressV3OrNull() ?: return null
                return PublicKey(address)
            }
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
             * @throws [IllegalArgumentException] if no key is found
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toED25519_V3PrivateKey(): PrivateKey {
                return toED25519_V3PrivateKeyOrNull()
                    ?: throw IllegalArgumentException("Tried base 16/32/64 decoding, but failed to find a $BYTE_SIZE byte key")
            }

            /**
             * Transforms provided bytes into a ED25519-V3 private key.
             *
             * @return [ED25519_V3.PrivateKey]
             * @throws [IllegalArgumentException] if byte array size is inappropriate
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toED25519_V3PrivateKey(): PrivateKey {
                return toED25519_V3PrivateKeyOrNull()
                    ?: throw IllegalArgumentException("Invalid key size. Must be $BYTE_SIZE bytes")
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
            public fun String.toED25519_V3PrivateKeyOrNull(): PrivateKey? {
                val decoded = tryDecodeOrNull(expectedSize = BYTE_SIZE) ?: return null
                return PrivateKey(decoded)
            }

            /**
             * Transforms provided bytes into a ED25519-V3 private key.
             *
             * @return [ED25519_V3.PrivateKey] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toED25519_V3PrivateKeyOrNull(): PrivateKey? {
                if (size != BYTE_SIZE) return null
                return PrivateKey(copyOf())
            }

            private const val BYTE_SIZE = 64
        }
    }
}
