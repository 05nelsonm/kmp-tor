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

import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress

/**
 * Type definition of [Key.Public] and [Key.Private] specific to
 * client authentication.
 *
 * @see [X25519]
 * */
public class AuthKey private constructor() {

    public sealed class Public(private val key: ByteArray): Key.Public() {

        // TODO: writeDescriptorToFile

        public final override fun encoded(): ByteArray = key.copyOf()

        public final override fun base16(): String = key.encodeToString(BASE_16)
        public final override fun base32(): String = key.encodeToString(BASE_32)
        public final override fun base64(): String = key.encodeToString(BASE_64)

        public fun descriptorBase32(): String = toDescriptor("descriptor", base32())
        public fun descriptorBase64(): String = toDescriptor("descriptor", base64())

        public final override fun equals(other: Any?): Boolean {
            if (other !is AuthKey.Public) return false
            if (other::class != this::class) return false
            if (other.algorithm() != algorithm()) return false
            if (other.key.size != key.size) return false

            var isEqual = true
            for (i in key.indices) {
                if (other.key[i] != key[i]) {
                    isEqual = false
                }
            }

            return isEqual
        }

        public final override fun hashCode(): Int = 17 * 31 + key.toList().hashCode()
    }

    public sealed class Private(key: ByteArray): Key.Private(key) {

        // TODO: writeDescriptorToFile

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        public fun descriptorBase32(
            address: OnionAddress,
        ): String = descriptorBase32(address.asPublicKey())

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        public fun descriptorBase32(
            publicKey: AddressKey.Public,
        ): String {
            val result = descriptorBase32OrNull(publicKey)
            if (result != null) return result

            if (publicKey.isCompatible()) {
                throw IllegalStateException("isDestroyed[${isDestroyed()}]")
            }

            throw IllegalArgumentException("${publicKey.algorithm()}.PublicKey is not compatible with ${algorithm()}.PrivateKey")
        }

        public fun descriptorBase32OrNull(
            address: OnionAddress,
        ): String? = descriptorBase32OrNull(address.asPublicKey())

        public fun descriptorBase32OrNull(
            publicKey: AddressKey.Public,
        ): String? {
            if (!publicKey.isCompatible()) return null

            val encoded = base32OrNull() ?: return null
            return toDescriptor(publicKey.address().value, encoded)
        }

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        public fun descriptorBase64(
            address: OnionAddress,
        ): String = descriptorBase64(address.asPublicKey())

        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        public fun descriptorBase64(
            publicKey: AddressKey.Public,
        ): String {
            val result = descriptorBase64OrNull(publicKey)
            if (result != null) return result

            if (publicKey.isCompatible()) {
                throw IllegalStateException("isDestroyed[${isDestroyed()}]")
            }

            throw IllegalArgumentException("${publicKey.algorithm()}.PublicKey is not compatible with ${algorithm()}.PrivateKey")
        }

        public fun descriptorBase64OrNull(
            address: OnionAddress,
        ): String? = descriptorBase64OrNull(address.asPublicKey())

        public fun descriptorBase64OrNull(
            publicKey: AddressKey.Public,
        ): String? {
            if (!publicKey.isCompatible()) return null

            val encoded = base64OrNull() ?: return null
            return toDescriptor(publicKey.address().value, encoded)
        }

        protected abstract fun AddressKey.Public.isCompatible(): Boolean
    }

    init {
        throw IllegalStateException("AuthKey cannot be instantiated")
    }
}

@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
private inline fun Key.toDescriptor(
    descriptor: String,
    encoded: String
): String {
    val sb = StringBuilder()
    sb.append(descriptor)
    sb.append(':')
    sb.append(algorithm())
    sb.append(':')
    sb.append(encoded)

    val result = sb.toString()

    if (this is Key.Private) {
        // blank it (sb.clear only resets the internal index)
        val size = sb.length
        sb.clear()
        repeat(size) { sb.append(' ') }
    }

    return result
}
