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

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.Companion.toIPAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Companion.toPortOrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort.Companion.findHostnameAndPortFromURL
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Holder for a inet socket address' parts
 *
 * e.g.
 *
 *     "127.0.0.1:8080".toIPSocketAddress()
 *     "[::1]:8080".toIPSocketAddress()
 *     IPSocketAddress(IPAddress.V4.AnyHost, 8080.toPort())
 *
 * @see [toIPSocketAddress]
 * @see [toIPSocketAddressOrNull]
 * */
public class IPSocketAddress(
    @JvmField
    public val address: IPAddress,
    @JvmField
    public val port: Port,
): Address(address.canonicalHostName() + ':' + port) {

    public operator fun component1(): IPAddress = address
    public operator fun component2(): Port = port

    public fun copy(address: IPAddress): IPSocketAddress = copy(address, port)
    public fun copy(port: Port): IPSocketAddress = copy(address, port)
    public fun copy(address: IPAddress, port: Port): IPSocketAddress {
        if (address == this.address && port == this.port) return this
        return IPSocketAddress(address, port)
    }

    public companion object {

        /**
         * Parses a String for its IPv4 or IPv6 address and port.
         *
         * String can be either a URL containing the IP address and port, or the
         * IP address and port itself.
         *
         * @return [IPSocketAddress]
         * @throws [IllegalArgumentException] If not a valid IP address and port.
         * */
        @JvmStatic
        @JvmName("get")
        public fun String.toIPSocketAddress(): IPSocketAddress {
            return toIPSocketAddressOrNull()
                ?: throw IllegalArgumentException("$this does not contain a valid IP address & port")
        }

        /**
         * Parses a String for its IPv4 or IPv6 address and port.
         *
         * String can be either a URL containing the IP address and port, or the
         * IP address and port itself.
         *
         * @return [IPSocketAddress] or null
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun String.toIPSocketAddressOrNull(): IPSocketAddress? {
            val stripped = findHostnameAndPortFromURL()
            val address = stripped.toIPAddressOrNull() ?: return null
            val port = stripped.toPortOrNull() ?: return null
            return IPSocketAddress(address, port)
        }
    }
}
