/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.common.address

import io.matthewnelson.component.parcelize.Parcelize
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import io.matthewnelson.kmp.tor.common.internal.findHostnameAndPortFromUrl
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid IPv6 address
 *
 * @see [REGEX]
 * @see [RealIPAddressV6]
 * */
@SealedValueClass
@OptIn(ExperimentalTorApi::class)
sealed interface IPAddressV6: IPAddress {

    companion object {
        // https://ihateregex.io/expr/ipv6/
        @JvmStatic
        @Suppress("RegExpSimplifiable", "SpellCheckingInspection")
        val REGEX = Regex(pattern =
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

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(address: String): IPAddressV6 {
            val stripped = if (address.startsWith('[') && address.endsWith(']')) {
                address.drop(1).dropLast(1)
            } else {
                address
            }

            return RealIPAddressV6(stripped)
        }

        /**
         * Attempts to find the [IPAddressV6] for a given
         * string. This could be a URL, or the properly
         * formatted IPv6 address itself.
         *
         * @see [findHostnameAndPortFromUrl]
         * @see [fromStringOrNull]
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): IPAddressV6 {
            val hostnameAndPort = address.findHostnameAndPortFromUrl()

            try {
                // is canonicalized with port >> [::1]:8080
                return if (hostnameAndPort.startsWith('[') && hostnameAndPort.contains("]:")) {
                    invoke(hostnameAndPort.substringBeforeLast(':'))
                } else {
                    invoke(hostnameAndPort)
                }
            } catch (_: IllegalArgumentException) {}

            throw IllegalArgumentException("Failed to find valid IPv6 address from $address")
        }

        @JvmStatic
        fun fromStringOrNull(address: String): IPAddressV6? {
            return try {
                fromString(address)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

@JvmInline
@Parcelize
private value class RealIPAddressV6(override val value: String): IPAddressV6 {

    init {
        require(value.matches(IPAddressV6.REGEX)) {
            "$value is not a valid IPv6 address"
        }
    }

    override fun canonicalHostname(): String = "[$value]"

    override fun toString(): String = "IPAddressV6(value=$value)"
}
