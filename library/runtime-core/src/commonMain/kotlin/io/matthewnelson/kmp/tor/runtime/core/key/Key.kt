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
import io.matthewnelson.kmp.tor.runtime.core.Destroyable

/**
 * Base abstraction for Public/Private keys used by tor.
 * */
public expect sealed class Key private constructor() {

    /**
     * TODO
     * */
    public abstract fun algorithm(): String

    /**
     * TODO
     * */
    public abstract fun encodedOrNull(): ByteArray?

    /**
     * TODO
     * */
    public abstract fun base16OrNull(): String?

    /**
     * TODO
     * */
    public abstract fun base32OrNull(): String?

    /**
     * TODO
     * */
    public abstract fun base64OrNull(): String?

    /**
     * TODO
     * */
    public abstract fun base16CharsOrNull(): CharArray?

    /**
     * TODO
     * */
    public abstract fun base32CharsOrNull(): CharArray?

    /**
     * TODO
     * */
    public abstract fun base64CharsOrNull(): CharArray?

    public sealed class Public(): Key {

        /**
         * TODO
         * */
        public abstract fun encoded(): ByteArray

        /**
         * TODO
         * */
        public abstract fun base16(): String

        /**
         * TODO
         * */
        public abstract fun base32(): String

        /**
         * TODO
         * */
        public abstract fun base64(): String

        /**
         * TODO
         * */
        public abstract fun base16Chars(): CharArray

        /**
         * TODO
         * */
        public abstract fun base32Chars(): CharArray

        /**
         * TODO
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

        /**
         * TODO
         * */
        public final override fun isDestroyed(): Boolean

        /**
         * TODO
         * */
        public final override fun destroy()

        /**
         * TODO
         * */
        public fun encoded(): ByteArray

        /**
         * TODO
         * */
        public fun base16(): String

        /**
         * TODO
         * */
        public fun base32(): String

        /**
         * TODO
         * */
        public fun base64(): String

        /**
         * TODO
         * */
        public fun base16Chars(): CharArray

        /**
         * TODO
         * */
        public fun base32Chars(): CharArray

        /**
         * TODO
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
    }
}
