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

import io.matthewnelson.kmp.tor.runtime.api.address.IPAddress.V4.Companion.toIPAddressV4OrNull
import io.matthewnelson.kmp.tor.runtime.api.address.IPAddress.V6.Companion.toIPAddressV6OrNull
import io.matthewnelson.kmp.tor.runtime.api.internal.findHostnameAndPortFromURL
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Base abstraction for denoting a String value as an ip address
 * */
public sealed class IPAddress private constructor(value: String): Address(value) {

    public companion object {

        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun String.toIPAddress(): IPAddress {
            return toIPAddressOrNull()
                ?: throw IllegalArgumentException("$this does not contain an IP address")
        }

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
    public class V4 private constructor(value: String): IPAddress(value) {

        override fun canonicalHostname(): String = value

        public companion object {

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toIPAddressV4(): V4 {
                return toIPAddressV4OrNull()
                    ?: throw IllegalArgumentException("$this does not contain an IPv4 address")
            }

            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toIPAddressV4OrNull(): V4? {
                val stripped = findHostnameAndPortFromURL()
                    .substringBeforeLast(':')

                if (!stripped.matches(REGEX)) return null
                return V4(stripped)
            }

            // https://ihateregex.io/expr/ip/
            @Suppress("RegExpSimplifiable")
            private val REGEX: Regex = Regex(pattern =
                "(" +
                "\\b25[0-5]|" +
                "\\b2[0-4][0-9]|" +
                "\\b[01]?[0-9][0-9]?)(\\.(25[0-5]|" +
                "2[0-4][0-9]|" +
                "[01]?[0-9][0-9]?)" +
                "){3}"
            )
        }
    }

    /**
     * Holder for an IPv6 address
     * */
    public class V6 private constructor(value: String): IPAddress(value) {

        override fun canonicalHostname(): String = "[$value]"

        public companion object {

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toIPAddressV6(): V6 {
                return toIPAddressV6OrNull()
                    ?: throw IllegalArgumentException("$this does not contain an IPv6 address")
            }

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