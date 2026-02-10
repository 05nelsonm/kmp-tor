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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "RedundantVisibilityModifier", "UNUSED")

package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base32.Base32
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.EncoderDecoder
import io.matthewnelson.kmp.tor.runtime.core.Destroyable

/**
 * Base abstraction for Public/Private keys used by tor.
 * */
public expect sealed class Key private constructor() {

    /**
     * Returns the algorithm name for this key. For example, `ED25519-V3` or `x25519`
     * */
    public abstract fun algorithm(): String

    /**
     * Returns the key in its primary encoding, or `null` if the key does not support
     * encoding or [Key.Private.isDestroyed] is `true`.
     * */
    public abstract fun encodedOrNull(): ByteArray?

    /**
     * Returns a Base16 (uppercase) encoded `String` of the raw key value, or `null` if
     * the key does not support encoding or [Key.Private.isDestroyed] is `true`.
     *
     * **NOTE:** `String` is immutable and thus cannot be back-filled after
     * use. For [Key.Private], one should prefer using [base16CharsOrNull] instead.
     *
     * @see [base16CharsOrNull]
     * */
    public abstract fun base16OrNull(): String?

    /**
     * Returns a Base32 (uppercase & no padding) encoded `String` of the raw key value, or
     * `null` if the key does not support encoding or [Key.Private.isDestroyed] is `true`.
     *
     * **NOTE:** `String` is immutable and thus cannot be back-filled after
     * use. For [Key.Private], one should prefer using [base32CharsOrNull] instead.
     *
     * @see [base32CharsOrNull]
     * */
    public abstract fun base32OrNull(): String?

    /**
     * Returns a Base64 (no padding) encoded `String` of the raw key value, or `null` if
     * the key does not support encoding or [Key.Private.isDestroyed] is `true`.
     *
     * **NOTE:** `String` is immutable and thus cannot be back-filled after
     * use. For [Key.Private], one should prefer using [base64CharsOrNull] instead.
     *
     * @see [base64CharsOrNull]
     * */
    public abstract fun base64OrNull(): String?

    /**
     * Returns a Base16 (uppercase) encoded `CharArray` of the raw key value, or `null` if
     * the key does not support encoding or [Key.Private.isDestroyed] is `true`.
     *
     * @see [base16OrNull]
     * */
    public abstract fun base16CharsOrNull(): CharArray?

    /**
     * Returns a Base32 (uppercase & no padding) encoded `CharArray` of the raw key value, or
     * `null` if the key does not support encoding or [Key.Private.isDestroyed] is `true`.
     *
     * @see [base32OrNull]
     * */
    public abstract fun base32CharsOrNull(): CharArray?

    /**
     * Returns a Base64 (no padding) encoded `CharArray` of the raw key value, or `null` if
     * the key does not support encoding or [Key.Private.isDestroyed] is `true`.
     *
     * @see [base64OrNull]
     * */
    public abstract fun base64CharsOrNull(): CharArray?

    public sealed class Public(): Key {

        /**
         * Returns the key in its primary encoding.
         * */
        public abstract fun encoded(): ByteArray

        /**
         * Returns a Base16 (uppercase) encoded `String` of the raw key value.
         *
         * @see [base16Chars]
         * */
        public abstract fun base16(): String

        /**
         * Returns a Base32 (uppercase & no padding) encoded `String` of the raw key value.
         *
         * @see [base32Chars]
         * */
        public abstract fun base32(): String

        /**
         * Returns a Base64 (no padding) encoded `String` of the raw key value.
         *
         * @see [base64Chars]
         * */
        public abstract fun base64(): String

        /**
         * Returns a Base16 (uppercase) encoded `CharArray` of the raw key value.
         *
         * @see [base16]
         * */
        public abstract fun base16Chars(): CharArray

        /**
         * Returns a Base32 (uppercase & no padding) encoded `CharArray` of the raw key value.
         *
         * @see [base32]
         * */
        public abstract fun base32Chars(): CharArray

        /**
         * Returns a Base64 (no padding) encoded `CharArray` of the raw key value.
         *
         * @see [base64]
         * */
        public abstract fun base64Chars(): CharArray

        public final override fun encodedOrNull(): ByteArray
        public final override fun base16OrNull(): String
        public final override fun base32OrNull(): String
        public final override fun base64OrNull(): String
        public final override fun base16CharsOrNull(): CharArray
        public final override fun base32CharsOrNull(): CharArray
        public final override fun base64CharsOrNull(): CharArray

        /** @suppress */
        public final override fun equals(other: Any?): Boolean
        /** @suppress */
        public final override fun hashCode(): Int
        /** @suppress */
        public final override fun toString(): String
    }

    public sealed class Private(key: ByteArray): Key, Destroyable {

        public final override fun isDestroyed(): Boolean

        /**
         * Destroys the [Key.Private], back-filling the underlying array with `0` bytes.
         * */
        public final override fun destroy()

        /**
         * Returns the key in its primary encoding.
         *
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun encoded(): ByteArray

        /**
         * Returns a Base16 (uppercase) encoded `String` of the raw key value.
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after
         * use. One should prefer using [base16Chars] instead.
         *
         * @see [base16Chars]
         *
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun base16(): String

        /**
         * Returns a Base32 (uppercase & no padding) encoded `String` of the raw key value.
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after
         * use. One should prefer using [base32Chars] instead.
         *
         * @see [base32Chars]
         *
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun base32(): String

        /**
         * Returns a Base64 (no padding) encoded `String` of the raw key value.
         *
         * **NOTE:** `String` is immutable and thus cannot be back-filled after
         * use. One should prefer using [base32Chars] instead.
         *
         * @see [base64Chars]
         *
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun base64(): String

        /**
         * Returns a Base16 (uppercase) encoded `CharArray` of the raw key value.
         *
         * @see [base16]
         *
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun base16Chars(): CharArray

        /**
         * Returns a Base32 (uppercase & no padding) encoded `CharArray` of the raw key value.
         *
         * @see [base32]
         *
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun base32Chars(): CharArray

        /**
         * Returns a Base64 (no padding) encoded `CharArray` of the raw key value.
         *
         * @see [base64]
         *
         * @throws [IllegalStateException] If [isDestroyed] is `true`.
         * */
        public fun base64Chars(): CharArray

        public final override fun encodedOrNull(): ByteArray?
        public final override fun base16OrNull(): String?
        public final override fun base32OrNull(): String?
        public final override fun base64OrNull(): String?
        public final override fun base16CharsOrNull(): CharArray?
        public final override fun base32CharsOrNull(): CharArray?
        public final override fun base64CharsOrNull(): CharArray?

        /** @suppress */
        public final override fun equals(other: Any?): Boolean
        /** @suppress */
        public final override fun hashCode(): Int
        /** @suppress */
        public final override fun toString(): String
    }

    /** @suppress */
    protected companion object {
        internal val BASE_16: Base16
        internal val BASE_32: Base32.Default
        internal val BASE_64: Base64

        internal val DECODERS: List<EncoderDecoder<*>>
    }
}
