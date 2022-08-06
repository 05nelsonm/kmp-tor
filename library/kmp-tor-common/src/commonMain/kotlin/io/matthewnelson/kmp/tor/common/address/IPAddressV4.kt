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
 * Holder for a valid IPv4 address
 *
 * @see [REGEX]
 * @see [RealIPAddressV4]
 * */
@SealedValueClass
@OptIn(ExperimentalTorApi::class)
sealed interface IPAddressV4: IPAddress {

    companion object {
        // https://ihateregex.io/expr/ip/
        @JvmStatic
        @Suppress("RegExpSimplifiable")
        val REGEX = Regex(pattern =
            "(" +
            "\\b25[0-5]|" +
            "\\b2[0-4][0-9]|" +
            "\\b[01]?[0-9][0-9]?)(\\.(25[0-5]|" +
            "2[0-4][0-9]|" +
            "[01]?[0-9][0-9]?)" +
            "){3}"
        )

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(address: String): IPAddressV4 {
            return RealIPAddressV4(address)
        }

        /**
         * Attempts to find the [IPAddressV4] for a given
         * string. This could be a URL, or the properly
         * formatted address itself.
         *
         * @see [findHostnameAndPortFromUrl]
         * @see [fromStringOrNull]
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): IPAddressV4 {
            val hostname = address.findHostnameAndPortFromUrl()
                .substringBeforeLast(':') // port

            try {
                return invoke(hostname)
            } catch (_: IllegalArgumentException) {}

            throw IllegalArgumentException("Failed to find valid IPv4 address from $address")
        }

        @JvmStatic
        fun fromStringOrNull(address: String): IPAddressV4? {
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
private value class RealIPAddressV4(override val value: String): IPAddressV4 {

    init {
        require(value.matches(IPAddressV4.REGEX)) {
            "$value is not a valid IPv4 address"
        }
    }

    override fun canonicalHostname(): String = value

    override fun toString(): String = "IPAddressV4(value=$value)"
}
