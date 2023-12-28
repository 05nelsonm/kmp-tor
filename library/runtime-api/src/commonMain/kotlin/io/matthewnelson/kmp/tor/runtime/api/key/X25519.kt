/*
 * Copyright (c) 2023 Matthew Nelson
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
package io.matthewnelson.kmp.tor.runtime.api.key

import io.matthewnelson.kmp.tor.runtime.api.internal.tryDecodeOrNull
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

public object X25519: KeyType.Auth<X25519.PublicKey, X25519.PrivateKey>() {

    public override fun algorithm(): String = "x25519"

    public class PublicKey private constructor(
        key: ByteArray
    ): AuthKey.Public(key) {

        public override fun algorithm(): String = X25519.algorithm()

        public companion object {

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toX25519PublicKey(): PublicKey {
                return toX25519PublicKeyOrNull()
                    ?: throw IllegalArgumentException("Tried base 16/32/64 decoding, but failed to find a $BYTE_SIZE byte key")
            }

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toX25519PublicKey(): PublicKey {
                return toX25519PublicKeyOrNull()
                    ?: throw IllegalArgumentException("Invalid key size. Must be $BYTE_SIZE bytes")
            }

            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toX25519PublicKeyOrNull(): PublicKey? {
                val decoded = tryDecodeOrNull(expectedSize = BYTE_SIZE) ?: return null
                return PublicKey(decoded)
            }

            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toX25519PublicKeyOrNull(): PublicKey? {
                if (size != BYTE_SIZE) return null
                return PublicKey(copyOf())
            }
        }
    }

    public class PrivateKey private constructor(
        key: ByteArray
    ): AuthKey.Private(key) {

        public override fun algorithm(): String = X25519.algorithm()

        public companion object {

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toX25519PrivateKey(): PrivateKey {
                return toX25519PrivateKeyOrNull()
                    ?: throw IllegalArgumentException("Tried base 16/32/64 decoding, but failed to find a $BYTE_SIZE byte key")
            }

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toX25519PrivateKey(): PrivateKey {
                return toX25519PrivateKeyOrNull()
                    ?: throw IllegalArgumentException("Invalid key size. Must be $BYTE_SIZE bytes")
            }

            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toX25519PrivateKeyOrNull(): PrivateKey? {
                val decoded = tryDecodeOrNull(expectedSize = BYTE_SIZE) ?: return null
                return PrivateKey(decoded)
            }

            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toX25519PrivateKeyOrNull(): PrivateKey? {
                if (size != BYTE_SIZE) return null
                return PrivateKey(copyOf())
            }
        }

        protected override fun AddressKey.Public.isCompatible(): Boolean = when (this) {
            is ED25519_V3.PublicKey -> true
        }
    }

    private const val BYTE_SIZE = 32
}
