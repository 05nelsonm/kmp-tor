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
package io.matthewnelson.kmp.tor.runtime.core.address

import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4OrNull
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V6.Companion.toIPAddressV6OrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.findHostnameAndPortFromURL
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Base abstraction for denoting a String value as an ip address
 *
 * @see [V4]
 * @see [V6]
 * */
public sealed class IPAddress private constructor(value: String): Address(value) {

    public companion object {

        /**
         * Parses a String for its IPv4 or IPv6 address.
         *
         * String can be either a URL containing the IP address, or the
         * IPv4/IPv6 address itself.
         *
         * @return [IPAddress]
         * @throws [IllegalArgumentException] if no IP address is found
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun String.toIPAddress(): IPAddress {
            return toIPAddressOrNull()
                ?: throw IllegalArgumentException("$this does not contain an IP address")
        }

        /**
         * Parses a String for its IPv4 or IPv6 address.
         *
         * String can be either a URL containing the IP address, or the
         * IPv4/IPv6 address itself.
         *
         * @return [IPAddress] or null
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun String.toIPAddressOrNull(): IPAddress? {
            return toIPAddressV4OrNull()
                ?: toIPAddressV6OrNull()
        }
    }

    /**
     * Holder for an IPv4 address
     * */
    public open class V4 private constructor(
        private val bytes: ByteArray,
        value: String?,
    ): IPAddress(value ?: bytes.joinToString(".", transform = { it.toUByte().toString() })) {

        /**
         * `0.0.0.0`
         * */
        public object AnyHost: V4(BYTES_ANY_HOST, null)

        public fun address(): ByteArray = bytes.copyOf()
        public override fun canonicalHostName(): String = value

        public companion object {

            /**
             * Parses a String for its IPv4 address.
             *
             * String can be either a URL containing the IPv4 address, or the
             * IPv4 address itself.
             *
             * @return [IPAddress.V4]
             * @throws [IllegalArgumentException] if no IPv4 address is found.
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toIPAddressV4(): V4 {
                return toIPAddressV4OrNull()
                    ?: throw IllegalArgumentException("$this does not contain an IPv4 address")
            }

            /**
             * Convert bytes to an IPv4 address.
             *
             * @return [IPAddress.V4]
             * @throws [IllegalArgumentException] if array size is not 4.
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toIPAddressV4(): V4 {
                return toIPAddressV4OrNull()
                    ?: throw IllegalArgumentException("Array must be 4 bytes in length")
            }

            /**
             * Parses a String for its IPv4 address.
             *
             * String can be either a URL containing the IPv4 address, or the
             * IPv4 address itself.
             *
             * @return [IPAddress.V4] or null if no IPv4 address is found.
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toIPAddressV4OrNull(): V4? {
                val stripped = findHostnameAndPortFromURL()
                    .substringBeforeLast(':')

                if (stripped == AnyHost.value) return AnyHost
                if (stripped == Loopback.value) return Loopback

                val splits = stripped.split('.')
                if (splits.size != 4) return null

                val bytes = try {
                    ByteArray(4) { i -> splits[i].toUByte().toByte() }
                } catch (_: NumberFormatException) {
                    return null
                }

                return V4(bytes, stripped)
            }

            /**
             * Convert bytes to an IPv4 address.
             *
             * @return [IPAddress.V4] or null if array size is not 4.
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toIPAddressV4OrNull(): V4? {
                if (size != 4) return null

                var anyhost = true
                var loopback = true
                for (i in 0..3) {
                    if (anyhost && this[i] != BYTES_ANY_HOST[i]) {
                        anyhost = false
                    }
                    if (loopback && this[i] != BYTES_LOOPBACK[i]) {
                        loopback = false
                    }
                    if (!anyhost && !loopback) break
                }

                if (anyhost) return AnyHost
                if (loopback) return Loopback

                return V4(copyOf(), null)
            }

            // Testing
            @JvmSynthetic
            internal fun V4.isLoopback(): Boolean = this is Loopback

            private val BYTES_ANY_HOST = ByteArray(4) { 0 }
            private val BYTES_LOOPBACK = byteArrayOf(127, 0, 0, 1)
        }

        // Typical IPv4 loopback address of 127.0.0.1
        private object Loopback: V4(BYTES_LOOPBACK, null)
    }

    /**
     * Holder for an IPv6 address
     * */
    public class V6 private constructor(value: String): IPAddress(value) {

        public override fun canonicalHostName(): String = "[$value]"

        public companion object {

            /**
             * Parses a String for its IPv6 address.
             *
             * String can be either a URL containing the IPv6 address, or the
             * IPv6 address itself.
             *
             * @return [IPAddress.V6]
             * @throws [IllegalArgumentException] if no IPv6 address is found
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toIPAddressV6(): V6 {
                return toIPAddressV6OrNull()
                    ?: throw IllegalArgumentException("$this does not contain an IPv6 address")
            }

            /**
             * Parses a String for its IPv6 address.
             *
             * String can be either a URL containing the IPv6 address, or the
             * IPv6 address itself.
             *
             * @return [IPAddress.V6] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toIPAddressV6OrNull(): V6? {
                var stripped = findHostnameAndPortFromURL()

                // is canonical with port >> [::1]:8080
                if (stripped.startsWith('[') && stripped.contains("]:")) {
                    stripped = stripped.substringBeforeLast(':')
                }

                if (stripped.startsWith('[') && stripped.endsWith(']')) {
                    stripped = stripped.drop(1).dropLast(1)
                }

                if (!stripped.matches(REGEX)) return null
                return V6(stripped)
            }

            // https://ihateregex.io/expr/ipv6/
            @Suppress("RegExpSimplifiable")
            private val REGEX: Regex = Regex(pattern =
                "(" +
                "([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|" +
                "([0-9a-fA-F]{1,4}:){1,7}:|" +
                "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
                "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +
                "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +
                "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +
                "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +
                "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +
                ":((:[0-9a-fA-F]{1,4}){1,7}|:)|" +
                "fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|" +
                "::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|" +
                "(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}" +
                "(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|" +
                "([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|" +
                "(2[0-4]|" +
                "1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|" +
                "(2[0-4]|1{0,1}[0-9]){0,1}[0-9])" +
                ")"
            )
        }
    }
}
