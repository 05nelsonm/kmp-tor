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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base32.Base32
import io.matthewnelson.encoding.base32.Base32Default
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.runtime.core.Destroyable
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.destroyedException
import kotlin.concurrent.Volatile

/**
 * Base abstraction for Public/Private keys used in tor.
 * */
public actual sealed class Key private actual constructor(): java.security.Key {

    public actual abstract fun algorithm(): String

    public actual abstract fun encodedOrNull(): ByteArray?
    public actual abstract fun base16OrNull(): String?
    public actual abstract fun base32OrNull(): String?
    public actual abstract fun base64OrNull(): String?

    public final override fun getAlgorithm(): String = algorithm()
    public final override fun getEncoded(): ByteArray? = encodedOrNull()

    // For now keys are always raw but could be bumped up
    // to commonMain if need be
    public final override fun getFormat(): String = "RAW"

    public actual sealed class Public actual constructor(): Key(), java.security.PublicKey {
        public actual abstract fun encoded(): ByteArray
        public actual abstract fun base16(): String
        public actual abstract fun base32(): String
        public actual abstract fun base64(): String

        public actual final override fun encodedOrNull(): ByteArray = encoded()
        public actual final override fun base16OrNull(): String = base16()
        public actual final override fun base32OrNull(): String = base32()
        public actual final override fun base64OrNull(): String = base64()

        private val _toString by lazy { "${algorithm()}.PublicKey[${base32()}]" }
        /** @suppress */
        public actual final override fun equals(other: Any?): Boolean = other is Public && other.toString() == toString()
        /** @suppress */
        public actual final override fun hashCode(): Int = 17 * 31 + toString().hashCode()
        /** @suppress */
        public actual final override fun toString(): String = _toString
    }

    public actual sealed class Private actual constructor(
        private val key: ByteArray,
    ) : Key(), Destroyable, java.security.PrivateKey {

        @Volatile
        private var _destroyed = false
        @OptIn(InternalKmpTorApi::class)
        private val lock = SynchronizedObject()

        public actual final override fun destroy() {
            if (_destroyed) return

            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) {
                if (_destroyed) return@synchronized
                key.fill(0)
                _destroyed = true
            }
        }

        public actual final override fun isDestroyed(): Boolean = _destroyed

        @Throws(IllegalStateException::class)
        public actual fun encoded(): ByteArray = encodedOrNull() ?: throw destroyedException(algorithm())
        @Throws(IllegalStateException::class)
        public actual fun base16(): String = base16OrNull() ?: throw destroyedException(algorithm())
        @Throws(IllegalStateException::class)
        public actual fun base32(): String = base32OrNull() ?: throw destroyedException(algorithm())
        @Throws(IllegalStateException::class)
        public actual fun base64(): String = base64OrNull() ?: throw destroyedException(algorithm())

        public actual final override fun encodedOrNull(): ByteArray? = withKeyOrNull { it.copyOf() }
        public actual final override fun base16OrNull(): String? = withKeyOrNull { it.encodeToString(BASE_16) }
        public actual final override fun base32OrNull(): String? = withKeyOrNull { it.encodeToString(BASE_32) }
        public actual final override fun base64OrNull(): String? = withKeyOrNull { it.encodeToString(BASE_64) }

        @OptIn(InternalKmpTorApi::class)
        protected actual fun <T : Any> withKeyOrNull(
            block: (key: ByteArray) -> T
        ): T? {
            if (_destroyed) return null

            return synchronized(lock) {
                if (_destroyed) return@synchronized null
                block(key)
            }
        }

        /** @suppress */
        public actual final override fun equals(other: Any?): Boolean = other is Private && other.hashCode() == hashCode()
        /** @suppress */
        public actual final override fun hashCode(): Int = 17 * 42 + key.hashCode()
        /** @suppress */
        public actual final override fun toString(): String = "${algorithm()}.PrivateKey[isDestroyed=$_destroyed]@${hashCode()}"
    }

    /** @suppress */
    protected actual companion object {
        internal actual val BASE_16: Base16 = Base16()
        internal actual val BASE_32: Base32.Default = Base32Default { padEncoded = false }
        internal actual val BASE_64: Base64 = Base64 { padEncoded = false }
    }
}
