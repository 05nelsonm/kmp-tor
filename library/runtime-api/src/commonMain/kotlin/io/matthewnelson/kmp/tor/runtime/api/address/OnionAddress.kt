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
package io.matthewnelson.kmp.tor.runtime.api.address

import io.matthewnelson.encoding.base32.Base32
import io.matthewnelson.encoding.base32.Base32Default
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.runtime.api.address.OnionAddress.V3.Companion.toOnionAddressV3OrNull
import io.matthewnelson.kmp.tor.runtime.api.internal.findHostnameAndPortFromURL
import io.matthewnelson.kmp.tor.runtime.api.internal.stripBaseEncoding
import io.matthewnelson.kmp.tor.runtime.api.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.api.key.ED25519_V3
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Base abstraction for denoting a String value as a `.onion` address
 * */
public sealed class OnionAddress private constructor(value: String): Address(value) {

    public final override fun canonicalHostname(): String = "$value.onion"
    public abstract fun decode(): ByteArray
    public abstract fun asPublicKey(): AddressKey.Public

    public companion object {

        /**
         * Parses a String for any `.onion` address.
         *
         * String can be either a URL containing the `.onion` address, or the
         * `.onion` address itself.
         *
         * Currently, only v3 `.onion` addresses are supported.
         *
         * @return [OnionAddress]
         * @throws [IllegalArgumentException] if no `.onion` address is found
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun String.toOnionAddress(): OnionAddress {
            return toOnionAddressOrNull()
                ?: throw IllegalArgumentException("$this does not contain an onion address")
        }

        /**
         * Transforms provided bytes into a `.onion` address.
         *
         * Currently, only v3 `.onion` addresses are supported.
         *
         * @return [OnionAddress]
         * @throws [IllegalArgumentException] if byte array size is inappropriate
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun ByteArray.toOnionAddress(): OnionAddress {
            return toOnionAddressOrNull()
                ?: throw IllegalArgumentException("bytes are not an onion address")
        }

        /**
         * Parses a String for any `.onion` address.
         *
         * String can be either a URL containing the `.onion` address, or the
         * `.onion` address itself.
         *
         * Currently, only v3 `.onion` addresses are supported.
         *
         * @return [OnionAddress] or null
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun String.toOnionAddressOrNull(): OnionAddress? {
            return toOnionAddressV3OrNull()
        }

        /**
         * Transforms provided bytes into a `.onion` address.
         *
         * Currently, only v3 `.onion` addresses are supported.
         *
         * @return [OnionAddress] or null
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun ByteArray.toOnionAddressOrNull(): OnionAddress? {
            return toOnionAddressV3OrNull()
        }
    }

    /**
     * Holder for a 56 character v3 onion address without the scheme or `.onion`
     * appended.
     *
     * This is only a preliminary check for character and length correctness.
     * Public key validity is not checked.
     * */
    public class V3 private constructor(value: String): OnionAddress(value) {

        public override fun decode(): ByteArray = value.decodeToByteArray(Base32.Default)
        public override fun asPublicKey(): ED25519_V3.PublicKey = ED25519_V3.PublicKey(this)

        public companion object {

            /**
             * Parses a String for a v3 `.onion` address.
             *
             * String can be either a URL containing the v3 `.onion` address, or the
             * v3 `.onion` address itself.
             *
             * @return [OnionAddress.V3]
             * @throws [IllegalArgumentException] if no v3 `.onion` address is found
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toOnionAddressV3(): V3 {
                return toOnionAddressV3OrNull()
                    ?: throw IllegalArgumentException("$this does not contain a v3 onion address")
            }

            /**
             * Transforms provided bytes into a v3 `.onion` address.
             *
             * @return [OnionAddress.V3]
             * @throws [IllegalArgumentException] if byte array size is inappropriate
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toOnionAddressV3(): V3 {
                return toOnionAddressV3OrNull()
                    ?: throw IllegalArgumentException("bytes are not a v3 onion address")
            }

            /**
             * Parses a String for a v3 `.onion` address.
             *
             * String can be either a URL containing the v3 `.onion` address, or the
             * v3 `.onion` address itself.
             *
             * @return [OnionAddress.V3] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toOnionAddressV3OrNull(): V3? {
                val stripped = findOnionAddressFromURL()
                    .lowercase()
                    .stripBaseEncoding()

                if (!stripped.matches(REGEX)) return null
                return V3(stripped)
            }

            /**
             * Transforms provided bytes into a v3 `.onion` address.
             *
             * @return [OnionAddress.V3] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toOnionAddressV3OrNull(): V3? {
                if (size != BYTE_SIZE) return null

                val encoded = encodeToString(Base32Default {
                    encodeToLowercase = true
                    padEncoded = false
                })

                return V3(encoded)
            }

            internal const val BYTE_SIZE: Int = 35
            private val REGEX: Regex = "[${Base32.Default.CHARS_LOWER}]{56}".toRegex()
        }
    }
}

@Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
private inline fun String.findOnionAddressFromURL(): String {
    return findHostnameAndPortFromURL()
        .substringBefore(':') // port
        .substringBeforeLast(".onion")
        .substringAfterLast('.') // subdomains
}
