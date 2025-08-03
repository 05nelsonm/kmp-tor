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
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.destroyedException
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress
import kotlin.jvm.JvmSynthetic

/**
 * Type definition of [Key.Public] and [Key.Private] specific to
 * client authentication.
 * */
public class AuthKey private constructor() {

    /**
     * Holder for a public key associated with a Hidden Service's client
     * authentication configuration.
     *
     * This would be the key a Hidden Service operator adds, to only allow
     * connections from tor clients who have the [AuthKey.Private] associated
     * with this [AuthKey.Public].
     *
     * @see [X25519.PublicKey]
     * */
    public sealed class Public(private val key: ByteArray): Key.Public() {

        public final override fun encoded(): ByteArray = key.copyOf()

        public final override fun base16(): String = key.encodeToString(BASE_16)
        public final override fun base32(): String = key.encodeToString(BASE_32)
        public final override fun base64(): String = key.encodeToString(BASE_64)

        /**
         * Produces the base 32 descriptor string for this [AuthKey.Public]
         * in the form of `descriptor:{algorithm}:{base-32}`.
         * */
        public fun descriptorBase32(): String = toDescriptor(address = null, base32())

        /**
         * Produces the base 64 descriptor string for this [AuthKey.Public]
         * in the form of `descriptor:{algorithm}:{base-64}`.
         * */
        public fun descriptorBase64(): String = toDescriptor(address = null, base64())
    }

    /**
     * Holder for a private key associated with a Hidden Service's client
     * authentication configuration.
     *
     * This would be the key added to a tor client by a user who wishes to
     * connect to a Hidden Service that has been configured using the
     * [AuthKey.Public] associated with this [AuthKey.Private].
     *
     * @see [X25519.PrivateKey]
     * */
    public sealed class Private(key: ByteArray): Key.Private(key) {

        /**
         * Produces the base 32 descriptor string for this [AuthKey.Private]
         * in the form of `{onion-address}:descriptor:{algorithm}:{base-32}`.
         *
         * @see [descriptorBase32OrNull]
         * @throws [IllegalArgumentException] if the [address] is not a
         *   compatible [OnionAddress] for this [algorithm].
         * @throws [IllegalStateException] if [isDestroyed] is `true`.
         * */
        public fun descriptorBase32(
            address: OnionAddress,
        ): String = descriptorBase32(address.asPublicKey())

        /**
         * Produces the base 32 descriptor string for this [AuthKey.Private]
         * in the form of `{onion-address}:descriptor:{algorithm}:{base-32}`.
         *
         * @see [descriptorBase32OrNull]
         * @throws [IllegalArgumentException] if the [publicKey] is not a
         *   compatible [AddressKey.Public] for this [algorithm].
         * @throws [IllegalStateException] if [isDestroyed] is `true`.
         * */
        public fun descriptorBase32(
            publicKey: AddressKey.Public,
        ): String {
            val result = descriptorBase32OrNull(publicKey)
            if (result != null) return result

            if (isCompatibleWith(publicKey)) {
                throw destroyedException()
            }

            throw IllegalArgumentException("${publicKey.algorithm()}.PublicKey is not compatible with ${algorithm()}.PrivateKey")
        }

        /**
         * Produces the base 32 descriptor string for this [AuthKey.Private]
         * in the form of `{address-w/o-.onion}:descriptor:{algorithm}:{base-32}`,
         * or `null` if [isDestroyed] is `true`.
         *
         * @see [descriptorBase32]
         * */
        public fun descriptorBase32OrNull(
            address: OnionAddress,
        ): String? = descriptorBase32OrNull(address.asPublicKey())

        /**
         * Produces the base 32 descriptor string for this [AuthKey.Private]
         * in the form of `{address-w/o-.onion}:descriptor:{algorithm}:{base-32}`,
         * or `null` if [isDestroyed] is `true`.
         *
         * @see [descriptorBase32]
         * */
        public fun descriptorBase32OrNull(
            publicKey: AddressKey.Public,
        ): String? {
            if (!isCompatibleWith(publicKey)) return null

            val encoded = base32OrNull() ?: return null
            return toDescriptor(address = publicKey.address(), encoded)
        }

        /**
         * Produces the base 64 descriptor string for this [AuthKey.Private]
         * in the form of `{address-w/o-.onion}:{algorithm}:{base-64}`.
         *
         * @see [descriptorBase64OrNull]
         * @throws [IllegalArgumentException] if the [address] is not a
         *   compatible [OnionAddress] for this [algorithm].
         * @throws [IllegalStateException] if [isDestroyed] is `true`.
         * */
        public fun descriptorBase64(
            address: OnionAddress,
        ): String = descriptorBase64(address.asPublicKey())

        /**
         * Produces the base 64 descriptor string for this [AuthKey.Private]
         * in the form of `{address-w/o-.onion}:descriptor:{algorithm}:{base-64}`.
         *
         * @see [descriptorBase64OrNull]
         * @throws [IllegalArgumentException] if the [publicKey] is not a
         *   compatible [AddressKey.Public] for this [algorithm].
         * @throws [IllegalStateException] if [isDestroyed] is `true`.
         * */
        public fun descriptorBase64(
            publicKey: AddressKey.Public,
        ): String {
            val result = descriptorBase64OrNull(publicKey)
            if (result != null) return result

            if (isCompatibleWith(publicKey)) {
                throw destroyedException()
            }

            throw IllegalArgumentException("${publicKey.algorithm()}.PublicKey is not compatible with ${algorithm()}.PrivateKey")
        }

        /**
         * Produces the base 64 descriptor string for this [AuthKey.Private]
         * in the form of `{address-w/o-.onion}:descriptor:{algorithm}:{base-64}`,
         * or `null` if [isDestroyed] is `true`.
         *
         * @see [descriptorBase64]
         * */
        public fun descriptorBase64OrNull(
            address: OnionAddress,
        ): String? = descriptorBase64OrNull(address.asPublicKey())

        /**
         * Produces the base 64 descriptor string for this [AuthKey.Private]
         * in the form of `{address-w/o-.onion}:descriptor:{algorithm}:{base-64}`,
         * or `null` if [isDestroyed] is `true`.
         *
         * @see [descriptorBase64]
         * */
        public fun descriptorBase64OrNull(
            publicKey: AddressKey.Public,
        ): String? {
            if (!isCompatibleWith(publicKey)) return null

            val encoded = base64OrNull() ?: return null
            return toDescriptor(address = publicKey.address(), encoded)
        }

        @JvmSynthetic
        internal abstract fun isCompatibleWith(addressKey: AddressKey.Public): Boolean
    }

    init {
        throw IllegalStateException("AuthKey cannot be instantiated")
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Key.toDescriptor(
    address: OnionAddress?,
    encoded: String,
): String {
    val sb = StringBuilder().apply {
        if (address != null) {
            append(address.value)
            append(':')
        }

        append("descriptor:")
        append(algorithm())
        append(':')
        append(encoded)
    }

    val result = sb.toString()

    if (this is Key.Private) {
        // blank it (sb.clear only resets the internal index)
        val size = sb.length
        sb.clear()
        repeat(size) { sb.append(' ') }
    }

    return result
}
