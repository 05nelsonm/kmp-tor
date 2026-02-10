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

import io.matthewnelson.encoding.core.Encoder.Companion.encodeToCharArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.core.use
import io.matthewnelson.encoding.core.util.wipe
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.encoding.utf8.UTF8.CharPreProcessor.Companion.sizeUTF8
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.destroyedException
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
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

        public final override fun base16Chars(): CharArray = key.encodeToCharArray(BASE_16)
        public final override fun base32Chars(): CharArray = key.encodeToCharArray(BASE_32)
        public final override fun base64Chars(): CharArray = key.encodeToCharArray(BASE_64)

        /**
         * Produces the descriptor `String` using the Base32 encoding of the
         * key in the form of `descriptor:{algorithm}:{base32 encoded key}`.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth`
         * file in the Hidden Service's `authorized_clients` directory.
         *
         * @see [descriptorBase32Utf8]
         * */
        public fun descriptorBase32(): String = toDescriptorString(address = null, base32Chars())

        /**
         * Produces the descriptor using the Base32 encoding of the key in the
         * form of `descriptor:{algorithm}:{base32 encoded key}` as UTF-8 bytes.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth`
         * file in the Hidden Service's `authorized_clients` directory.
         *
         * @see [descriptorBase32]
         * */
        public fun descriptorBase32Utf8(): ByteArray = descriptorBase32().encodeToByteArray()

        /**
         * Produces the descriptor `String` using the Base64 encoding of the
         * key in the form of `descriptor:{algorithm}:{base64 encoded key}`.
         *
         * @see [descriptorBase64Utf8]
         * */
        public fun descriptorBase64(): String = toDescriptorString(address = null, base64Chars())

        /**
         * Produces the descriptor using the Base64 encoding of the key in the
         * form of `descriptor:{algorithm}:{base64 encoded key}` as UTF-8 bytes.
         *
         * @see [descriptorBase64]
         * */
        public fun descriptorBase64Utf8(): ByteArray = descriptorBase64().encodeToByteArray()
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
         * Produces the descriptor `String` using the Base32 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase32Utf8] instead.
         *
         * @see [descriptorBase32Utf8]
         *
         * @throws [IllegalArgumentException] If [address] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun descriptorBase32(
            address: OnionAddress,
        ): String = descriptorBase32(address.asPublicKey())

        /**
         * Produces the descriptor `String` using the Base32 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase32Utf8] instead.
         *
         * @see [descriptorBase32Utf8]
         *
         * @throws [IllegalArgumentException] If [publicKey] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
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
         * Produces the descriptor `String` using the Base32 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`,
         * or `null` if either the [address] is not compatible with this [algorithm], or
         * [isDestroyed] is `true`.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase32Utf8OrNull] instead.
         *
         * @see [descriptorBase32Utf8OrNull]
         * */
        public fun descriptorBase32OrNull(
            address: OnionAddress,
        ): String? = descriptorBase32OrNull(address.asPublicKey())

        /**
         * Produces the descriptor `String` using the Base32 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`,
         * or `null` if either the [publicKey] is not compatible with this [algorithm], or
         * [isDestroyed] is `true`.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase32Utf8OrNull] instead.
         *
         * @see [descriptorBase32Utf8OrNull]
         * */
        public fun descriptorBase32OrNull(
            publicKey: AddressKey.Public,
        ): String? {
            if (!isCompatibleWith(publicKey)) return null
            val encoded = base32CharsOrNull() ?: return null
            return toDescriptorString(publicKey.address(), encoded)
        }

        /**
         * Produces the descriptor using the Base32 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`
         * as UTF-8 bytes.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * @see [descriptorBase32]
         *
         * @throws [IllegalArgumentException] If [address] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun descriptorBase32Utf8(
            address: OnionAddress,
        ): ByteArray = descriptorBase32Utf8(address.asPublicKey())

        /**
         * Produces the descriptor using the Base32 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`
         * as UTF-8 bytes.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * @see [descriptorBase32]
         *
         * @throws [IllegalArgumentException] If [publicKey] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun descriptorBase32Utf8(
            publicKey: AddressKey.Public,
        ): ByteArray {
            val result = descriptorBase32Utf8OrNull(publicKey)
            if (result != null) return result
            if (isCompatibleWith(publicKey)) {
                throw destroyedException()
            }
            throw IllegalArgumentException("${publicKey.algorithm()}.PublicKey is not compatible with ${algorithm()}.PrivateKey")
        }

        /**
         * Produces the descriptor using the Base32 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`
         * as UTF-8 bytes, or `null` if either the [address] is not compatible with this
         * [algorithm], or [isDestroyed] is `true`.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * @see [descriptorBase32OrNull]
         * */
        public fun descriptorBase32Utf8OrNull(
            address: OnionAddress,
        ): ByteArray? = descriptorBase32Utf8OrNull(address.asPublicKey())

        /**
         * Produces the descriptor using the Base32 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base32 encoded key}`
         * as UTF-8 bytes, or `null` if either the [publicKey] is not compatible with this
         * [algorithm], or [isDestroyed] is `true`.
         *
         * **NOTE:** This is the output for which would be written to a `{name}.auth_private`
         * file in the directory specified by a config's [TorOption.ClientOnionAuthDir].
         *
         * @see [descriptorBase32OrNull]
         * */
        public fun descriptorBase32Utf8OrNull(
            publicKey: AddressKey.Public,
        ): ByteArray? {
            if (!isCompatibleWith(publicKey)) return null
            val encoded = base32CharsOrNull() ?: return null
            return toDescriptorUtf8(publicKey.address(), encoded)
        }

        /**
         * Produces the descriptor `String` using the Base64 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`.
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase64Utf8] instead.
         *
         * @see [descriptorBase64Utf8]
         *
         * @throws [IllegalArgumentException] If [address] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun descriptorBase64(
            address: OnionAddress,
        ): String = descriptorBase64(address.asPublicKey())

        /**
         * Produces the descriptor `String` using the Base64 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`.
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase64Utf8] instead.
         *
         * @see [descriptorBase64Utf8]
         *
         * @throws [IllegalArgumentException] If [publicKey] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
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
         * Produces the descriptor `String` using the Base64 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`,
         * or `null` if either the [address] is not compatible with this [algorithm], or
         * [isDestroyed] is `true`.
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase64Utf8OrNull] instead.
         *
         * @see [descriptorBase32Utf8OrNull]
         * */
        public fun descriptorBase64OrNull(
            address: OnionAddress,
        ): String? = descriptorBase64OrNull(address.asPublicKey())

        /**
         * Produces the descriptor `String` using the Base64 encoding of the key in the form
         * of `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`,
         * or `null` if either the [publicKey] is not compatible with this [algorithm], or
         * [isDestroyed] is `true`.
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after use. One should
         * prefer using [descriptorBase64Utf8OrNull] instead.
         *
         * @see [descriptorBase32Utf8OrNull]
         * */
        public fun descriptorBase64OrNull(
            publicKey: AddressKey.Public,
        ): String? {
            if (!isCompatibleWith(publicKey)) return null
            val encoded = base64CharsOrNull() ?: return null
            return toDescriptorString(publicKey.address(), encoded)
        }

        /**
         * Produces the descriptor using the Base64 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`
         * as UTF-8 bytes.
         *
         * @see [descriptorBase64]
         *
         * @throws [IllegalArgumentException] If [address] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun descriptorBase64Utf8(
            address: OnionAddress,
        ): ByteArray = descriptorBase64Utf8(address.asPublicKey())

        /**
         * Produces the descriptor using the Base64 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`
         * as UTF-8 bytes.
         *
         * @see [descriptorBase64]
         *
         * @throws [IllegalArgumentException] If [publicKey] is not compatible with this [algorithm].
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun descriptorBase64Utf8(
            publicKey: AddressKey.Public,
        ): ByteArray {
            val result = descriptorBase64Utf8OrNull(publicKey)
            if (result != null) return result
            if (isCompatibleWith(publicKey)) {
                throw destroyedException()
            }
            throw IllegalArgumentException("${publicKey.algorithm()}.PublicKey is not compatible with ${algorithm()}.PrivateKey")
        }

        /**
         * Produces the descriptor using the Base64 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`
         * as UTF-8 bytes, or `null` if either the [address] is not compatible with this
         * [algorithm], or [isDestroyed] is `true`.
         *
         * @see [descriptorBase64OrNull]
         * */
        public fun descriptorBase64Utf8OrNull(
            address: OnionAddress,
        ): ByteArray? = descriptorBase64Utf8OrNull(address.asPublicKey())

        /**
         * Produces the descriptor using the Base64 encoding of the key in the form of
         * `{onion address without .onion}:descriptor:{algorithm}:{base64 encoded key}`
         * as UTF-8 bytes, or `null` if either the [publicKey] is not compatible with this
         * [algorithm], or [isDestroyed] is `true`.
         *
         * @see [descriptorBase64OrNull]
         * */
        public fun descriptorBase64Utf8OrNull(
            publicKey: AddressKey.Public,
        ): ByteArray? {
            if (!isCompatibleWith(publicKey)) return null
            val encoded = base64CharsOrNull() ?: return null
            return toDescriptorUtf8(publicKey.address(), encoded)
        }

        @JvmSynthetic
        internal abstract fun isCompatibleWith(addressKey: AddressKey.Public): Boolean
    }

    init {
        throw IllegalStateException("AuthKey cannot be instantiated")
    }
}

private fun Key.toDescriptorString(
    address: OnionAddress?,
    encoded: CharArray,
): String {
    val sb = toDescriptor(
        address,
        encoded,
        _create = ::StringBuilder,
        _appendChar = StringBuilder::append,
        _appendString = StringBuilder::append,
    )
    val result = sb.toString()
    if (this is Key.Private) sb.wipe()
    return result
}

private fun Key.Private.toDescriptorUtf8(
    address: OnionAddress?,
    encoded: CharArray,
): ByteArray {
    var i = 0
    val chars = toDescriptor(
        address,
        encoded,
        _create = ::CharArray,
        _appendChar = { c -> set(i++, c) },
        _appendString = { s -> s.forEach { c -> set(i++, c) } },
    )
    i = 0
    // OK to use sizeUTF8 here to mitigate unnecessary array
    // allocation and copying because the CharArray size is
    // negligible.
    val utf8 = ByteArray(chars.sizeUTF8(UTF8).toInt())
    UTF8.newDecoderFeed { b -> utf8[i++] = b }.use { feed ->
        chars.forEach(feed::consume)
    }
    chars.fill('\u0000')
    return utf8
}

@Suppress("LocalVariableName")
@OptIn(ExperimentalContracts::class)
private inline fun <T> Key.toDescriptor(
    address: OnionAddress?,
    encoded: CharArray,
    _create: (size: Int) -> T,
    _appendChar: T.(Char) -> Unit,
    _appendString: T.(String) -> Unit,
): T {
    contract {
        callsInPlace(_create, InvocationKind.EXACTLY_ONCE)
        callsInPlace(_appendChar, InvocationKind.AT_LEAST_ONCE)
        callsInPlace(_appendString, InvocationKind.AT_LEAST_ONCE)
    }
    val b = run {
        var capacity = 0
        if (address != null) {
            capacity += address.toString().length
            capacity++ // :
        }
        capacity += 11 // descriptor:
        capacity += algorithm().length
        capacity++ // :
        capacity += encoded.size
        _create(capacity)
    }

    if (address != null) {
        b._appendString(address.toString())
        b._appendChar(':')
    }

    b._appendString("descriptor:")
    b._appendString(algorithm())
    b._appendChar(':')
    encoded.forEach { c -> b._appendChar(c) }

    if (this is Key.Private) encoded.fill('\u0000')

    return b
}
