/*
 * Copyright (c) 2021 Matthew Nelson
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
package io.matthewnelson.kmp.tor.controller.common.control.usecase

import io.matthewnelson.kmp.tor.common.address.IPAddress
import io.matthewnelson.kmp.tor.common.address.IPAddressV4
import io.matthewnelson.kmp.tor.common.address.IPAddressV6
import io.matthewnelson.kmp.tor.common.address.OnionAddress
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * "MAPADDRESS" 1*(Address "=" Address SP) CRLF
 *
 * https://torproject.gitlab.io/torspec/control-spec/#mapaddress
 * */
interface TorControlMapAddress {

    suspend fun mapAddress(mapping: Mapping): Result<Mapped>

    suspend fun mapAddress(mappings: Set<Mapping>): Result<Set<Mapped>>

    data class Mapping(
        @JvmField
        val from: String,
        @JvmField
        val to: String,
    ) {

        constructor(from: IPAddress, to: IPAddress): this(from.value, to.value)
        constructor(from: IPAddress, to: String): this(from.value, to)
        constructor(from: String, to: IPAddress): this(from, to.value)
        constructor(from: IPAddress, to: OnionAddress): this(from, to.canonicalHostname())

        companion object {
            private val NULL_IPV4 = IPAddressV4("0.0.0.0")
            private val NULL_IPV6 = IPAddressV6("::0")
            private const val NULL_HOST = "."

            /**
             * Tor will generate a random host value (ex: 4lr2xdqckbl4nttj.virtual)
             * and map [to] to it.
             *
             * If [to] is already mapped to a random host value, Tor will simply return
             * that value.
             * */
            @JvmStatic
            fun anyHost(to: String) = Mapping(NULL_HOST, to)

            /**
             * Tor will generate a random [IPAddressV4] value and map [to] to it.
             *
             * If [to] is already mapped to an [IPAddressV4], Tor will simply return
             * that value.
             * */
            @JvmStatic
            fun anyIPv4(to: String) = Mapping(NULL_IPV4, to)

            @JvmStatic
            fun anyIPv4(to: OnionAddress) = Mapping(NULL_IPV4, to)

            /**
             * Tor will generate a random [IPAddressV6] value and map [to] to it.
             *
             * If [to] is already mapped to an [IPAddressV6], Tor will simply return
             * that value.
             * */
            @JvmStatic
            fun anyIPv6(to: String) = Mapping(NULL_IPV6, to)

            @JvmStatic
            fun anyIPv6(to: OnionAddress) = Mapping(NULL_IPV6, to)

            /**
             * Generates a [Mapping] which will result in the unmapping of any
             * addresses associated with [from].
             *
             * Ex:
             *
             *    val mapped = mapAddress(Mapping(from = "192.168.7.102", to = "torproject.org")).getOrThrow()
             *    // "192.168.7.102" is now mapped to "torproject.org"
             *
             *    val unmapped = mapAddress(Mapping.unmap(mapped.from)).getOrThrow()
             *    assertTrue(unmapped.isUnmapping)
             *    // "192.168.7.102" is no longer mapped to "torproject.org"
             * */
            @JvmStatic
            fun unmap(from: String) = Mapping(from, from)

            @JvmStatic
            fun unmap(from: IPAddress) = Mapping(from, from)
        }
    }

    class Mapped(
        @JvmField
        val from: String,
        @JvmField
        val to: String,
    ) {

        /**
         * If the [Mapped] is the result of a request to unmap an address (ie. [Mapping.unmap]).
         * */
        @get:JvmName("isUnmapping")
        val isUnmapping: Boolean get() = from == to

        override fun equals(other: Any?): Boolean = other is Mapped && other.from == from
        override fun hashCode(): Int = 17 * 31 + from.hashCode()
        override fun toString(): String = "Mapped(from=$from, to=$to)"
    }
}
