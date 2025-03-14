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

import io.matthewnelson.encoding.base32.Base32Default
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.encoding.core.use
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.OnionString.Companion.toOnionString
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.V3.Companion.toOnionAddressV3OrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort.Companion.findHostnameAndPortFromURL
import io.matthewnelson.kmp.tor.runtime.core.internal.containsNon0Byte
import io.matthewnelson.kmp.tor.runtime.core.internal.stripBaseEncoding
import io.matthewnelson.kmp.tor.runtime.core.key.AddressKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.Companion.toOnionAddress
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress.V3.Companion.toOnionAddressV3
import org.kotlincrypto.error.InvalidKeyException
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
     * Wraps the [OnionAddress] in its [AddressKey.Public] format.
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
         * @throws [IllegalArgumentException] if no `.onion` address is found
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
     * Holder for a 56 character base 32 encoded v3 onion address, without the scheme
     * or `.onion` appended.
     *
     * A v3 onion address consists of its:
     *  - 32 byte ed25519 public key
     *  - 2 byte checksum
     *  - 1 byte version number
     *
     * @see [toOnionAddressV3]
     * @see [toOnionAddressV3OrNull]
     * */
    public class V3 private constructor(value: String): OnionAddress(value) {

        public override fun decode(): ByteArray = value.decodeToByteArray(BASE_32)

        /**
         * Wraps the [OnionAddress.V3] in its [ED25519_V3.PublicKey] format for extended functionality.
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
             * @throws [IllegalArgumentException] when:
             *   - A 56 character base 32 encoded string is not found
             *   - The version byte is invalid
             *   - The checksum is invalid
             *   - The 32 byte ed25519 public key is all 0 bytes (blank)
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
             *   - Array size is not 35 bytes
             *   - The version byte is invalid
             *   - The checksum is invalid
             *   - The 32 byte ed25519 public key is all 0 bytes (blank)
             * */
            @JvmStatic
            @JvmName("get")
            public fun ByteArray.toOnionAddressV3(): V3 {
                require(size == BYTE_SIZE) { "Invalid array size. expected[$BYTE_SIZE] vs actual[$size]" }
                require(last() == VERSION_BYTE) { "Invalid version byte. expected[$VERSION_BYTE] vs actual[${last()}]" }
                require(containsNon0Byte(ED25519_V3.PublicKey.BYTE_SIZE)) { "ed25519 public key is blank (all 0 bytes)" }
                val checksum = computeChecksum()
                val ca0 = this[BYTE_SIZE - 3]
                val ca1 = this[BYTE_SIZE - 2]
                val ce0 = checksum[0]
                val ce1 = checksum[1]
                require(ca0 == ce0 && ca1 == ce1) { "Invalid checksum byte(s). expected[$ce0, $ce1] vs actual[$ca0, $ca1]" }
                return V3(encodeToString(BASE_32))
            }

            /**
             * Parses a String for a v3 `.onion` address.
             *
             * String can be either a URL containing the v3 `.onion` address, or the
             * v3 `.onion` address itself.
             *
             * @return [OnionAddress.V3] or null if [toOnionAddressV3] throws exception
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
             * @return [OnionAddress.V3] or null if [toOnionAddressV3] throws exception
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
                    value.decodeToByteArray(BASE_32)
                } catch (e: EncodingException) {
                    throw IllegalArgumentException("v3 Onion addresses are base32 encoded", e)
                }
                return bytes.toOnionAddressV3()
            }

            @JvmSynthetic
            @Throws(InvalidKeyException::class)
            internal fun fromED25519(publicKey: ByteArray): V3 {
                if (publicKey.size != ED25519_V3.PublicKey.BYTE_SIZE) {
                    throw InvalidKeyException("Invalid array size[${publicKey.size}]")
                }
                if (!publicKey.containsNon0Byte(ED25519_V3.PublicKey.BYTE_SIZE)) {
                    throw InvalidKeyException("Key is blank (all 0 bytes)")
                }
                val checksum = publicKey.computeChecksum()
                val s = StringBuilder(ENCODED_LEN)
                BASE_32.newEncoderFeed(out = { char -> s.append(char) }).use { feed ->
                    publicKey.forEach { b -> feed.consume(b) }
                    feed.consume(checksum[0])
                    feed.consume(checksum[1])
                    feed.consume(VERSION_BYTE)
                }
                return V3(s.toString())
            }

            internal const val BYTE_SIZE: Int = 35
            private const val ENCODED_LEN: Int = 56
            private const val VERSION_BYTE: Byte = 3

            @Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")
            @Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
            private inline fun ByteArray.computeChecksum(): ByteArray = SHA3_256().apply {
                update(CHECKSUM_PREFIX)
                update(this@computeChecksum, 0, ED25519_V3.PublicKey.BYTE_SIZE)
                update(VERSION_BYTE)
            }.digest()

            private val BASE_32 = Base32Default { encodeToLowercase = true; padEncoded = false }
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
