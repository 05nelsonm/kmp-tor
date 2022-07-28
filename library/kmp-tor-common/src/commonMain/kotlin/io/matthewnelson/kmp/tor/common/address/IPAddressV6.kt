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
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid IPv6 address
 *
 * Note that [toString] will enclose [value] in brackets
 * (ie. [a01e:67d4:f5ac:1d66:8a17:ddc5:8a4a:190f])
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

        @JvmStatic
        fun fromStringOrNull(address: String): IPAddressV6? {
            return try {
                invoke(address)
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

    override fun toString(): String = "[$value]"
}
