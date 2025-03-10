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
package io.matthewnelson.kmp.tor.runtime.core.net

import io.matthewnelson.encoding.base32.Base32
import io.matthewnelson.encoding.base32.Base32Default
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.OnionString.Companion.toOnionString
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.V3.Companion.toOnionAddressV3OrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort.Companion.findHostnameAndPortFromURL
import io.matthewnelson.kmp.tor.runtime.core.internal.stripBaseEncoding
import io.matthewnelson.kmp.tor.runtime.core.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.Companion.toOnionAddress
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.V3.Companion.toOnionAddressV3
import org.kotlincrypto.hash.sha3.SHA3_256
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Base abstraction for denoting a String value as a `.onion` address
 *
 * e.g.
 *
 *     val string = "2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid"
 *
 *     string.toOnionAddress()
 *     "http://${string}.onion:8080/some/path".toOnionAddress()
 *     "http://subdomain.${string}.onion:8080/some/path".toOnionAddress()
 *
 * @see [toOnionAddress]
 * @see [toOnionAddressOrNull]
 * */
public sealed class OnionAddress private constructor(value: String): Address(value) {

    public abstract fun decode(): ByteArray

    /**
     * Wraps the [OnionAddress] in its [AddressKey.Public] format for extended
     * functionality.
     * */
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
        public fun String.toOnionAddress(): OnionAddress {
            val onion = findHostnameAndPortFromURL().toOnionString()
            return onion.toOnionAddressV3()
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
        public fun ByteArray.toOnionAddress(): OnionAddress {
            return toOnionAddressV3()
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
        public fun String.toOnionAddressOrNull(): OnionAddress? = try {
            toOnionAddress()
        } catch (_: IllegalArgumentException) {
            null
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
        public fun ByteArray.toOnionAddressOrNull(): OnionAddress? = try {
            toOnionAddress()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Holder for a 56 character v3 onion address without the scheme or `.onion` appended.
     *
     * @see [toOnionAddressV3]
     * @see [toOnionAddressV3OrNull]
     * */
    public class V3 private constructor(value: String): OnionAddress(value) {

        public override fun decode(): ByteArray = value.decodeToByteArray(Base32.Default)

        /**
         * Wraps the [OnionAddress.V3] in its [ED25519_V3.PublicKey] format for extended
         * functionality.
         * */
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
            public fun String.toOnionAddressV3(): V3 {
                return findHostnameAndPortFromURL()
                    .toOnionString()
                    .toOnionAddressV3()
            }

            /**
             * Transforms provided bytes into a v3 `.onion` address.
             *
             * @return [OnionAddress.V3]
             * @throws [IllegalArgumentException] when:
             *   - array size is inappropriate
             *   - invalid version byte
             *   - invalid checksum
             * */
            @JvmStatic
            @JvmName("get")
            public fun ByteArray.toOnionAddressV3(): V3 {
                require(size == BYTE_SIZE) { "Invalid array size. expected[$BYTE_SIZE] vs actual[$size]" }
                require(last() == VERSION_BYTE) { "Invalid version byte. expected[$VERSION_BYTE] vs actual[${last()}]" }
                val checksum = computeChecksum(this)
                val a0 = this[BYTE_SIZE - 3]
                val a1 = this[BYTE_SIZE - 2]
                val e0 = checksum[0]
                val e1 = checksum[1]
                require(a0 == e0 && a1 == e1) { "Invalid checksum byte(s). expected[$e0, $e1] vs actual[$a0, $a1]" }
                return V3(encodeToString(BASE32))
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
            public fun String.toOnionAddressV3OrNull(): V3? = try {
                toOnionAddressV3()
            } catch (_: IllegalArgumentException) {
                null
            }

            /**
             * Transforms provided bytes into a v3 `.onion` address.
             *
             * @return [OnionAddress.V3] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toOnionAddressV3OrNull(): V3? = try {
                toOnionAddressV3()
            } catch (_: IllegalArgumentException) {
                null
            }

            @JvmSynthetic
            @Throws(IllegalArgumentException::class)
            internal fun OnionString.toOnionAddressV3(): V3 {
                require(value.length == ENCODED_LEN) { "v3 Onion addresses are $ENCODED_LEN characters" }
                val bytes = try {
                    value.decodeToByteArray(BASE32)
                } catch (e: EncodingException) {
                    throw IllegalArgumentException("v3 Onion addresses are base32 encoded", e)
                }
                return bytes.toOnionAddressV3()
            }

            @JvmSynthetic
            @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
            internal fun computeChecksum(
                ed25519PublicKey: ByteArray,
            ): ByteArray = SHA3_256().apply {
                update(CHECKSUM_PREFIX)
                update(ed25519PublicKey, 0, 32) // TODO: Move 32 to ED25519_V3.Public.Companion
                update(VERSION_BYTE)
            }.digest()

            internal const val BYTE_SIZE: Int = 35
            internal const val ENCODED_LEN: Int = 56
            internal const val VERSION_BYTE: Byte = 3

            private val BASE32 = Base32Default { encodeToLowercase = true; padEncoded = false }
            private val CHECKSUM_PREFIX = ".onion checksum".encodeToByteArray()
        }
    }

    @JvmInline
    internal value class OnionString private constructor(internal val value: String) {

        internal companion object {

            @JvmSynthetic
            internal fun HostAndPort.toOnionString(): OnionString {
                val stripped = value
                    .substringBefore(':') // port
                    .substringBeforeLast(".onion")
                    .substringAfterLast('.') // subdomains()
                    .lowercase()
                    .stripBaseEncoding()

                return OnionString(stripped)
            }
        }
    }
}
