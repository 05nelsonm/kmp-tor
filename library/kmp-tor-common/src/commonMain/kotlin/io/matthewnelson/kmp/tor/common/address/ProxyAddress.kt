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

import io.matthewnelson.component.parcelize.Parcelable
import io.matthewnelson.component.parcelize.Parcelize
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Holder for a single proxy address' parts
 *
 * Example:
 *
 *   127.0.0.1:9050
 *
 * Will be translated to:
 *
 *   val proxyAddress = ProxyAddress(address = IPAddressV4("127.0.0.1"), port = Port(9050))
 *   println(proxyAddress) // output >>> 127.0.0.1:9050
 *
 *   -OR-
 *
 *   val proxyAddress = ProxyAddress(address = IPAddressV6("::1"), port = Port(9050))
 *   println(proxyAddress) // output >>> [::1]:9050
 *
 * @see [fromString]
 * */
@Parcelize
class ProxyAddress(
    @JvmField
    val address: IPAddress,
    @JvmField
    val port: Port,
): Parcelable {

    fun copy() = ProxyAddress(address, port)
    fun copy(address: IPAddress) = ProxyAddress(address, port)
    fun copy(port: Port) = ProxyAddress(address, port)
    fun copy(address: IPAddress, port: Port) = ProxyAddress(address, port)

    override fun equals(other: Any?): Boolean {
        return  other is ProxyAddress       &&
                other.address == address    &&
                other.port == port
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 31 + address.hashCode()
        result = result * 31 + port.hashCode()
        return result
    }

    override fun toString(): String = "$address:${port.value}"

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): ProxyAddress {
            try {
                val portString = address.substringAfterLast(':')
                val port = Port(portString.toInt())
                val ipAddress = address.substringBeforeLast(":$portString")

                return ProxyAddress(address = IPAddress.fromString(ipAddress), port = port)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed to parse $address for an ipAddress and port")
            }
        }
    }

    @Throws(IllegalArgumentException::class)
    @Deprecated(message = "Use ProxyAddress(IPAddress.fromString(...), Port(...))")
    constructor(ipAddress: String, port: Port): this(IPAddress.fromString(ipAddress), port)

    @Deprecated(
        message = "Use address.value",
        replaceWith = ReplaceWith("address.value"),
        level = DeprecationLevel.WARNING,
    )
    fun component1(): String = address.value

    @Deprecated(
        message = "Use port",
        replaceWith = ReplaceWith("port"),
        level = DeprecationLevel.WARNING,
    )
    fun component2(): Port = port

    @Throws(IllegalArgumentException::class)
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "Use copy(address = IPAddress.fromString(...))")
    fun copy(ipAddress: String) = ProxyAddress(IPAddress.fromString(ipAddress), port)

    @Throws(IllegalArgumentException::class)
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated(message = "Use copy(address = IPAddress.fromString(...), port = Port(...))")
    fun copy(ipAddress: String, port: Port) = ProxyAddress(IPAddress.fromString(ipAddress), port)

    @JvmField
    @Deprecated(
        message = "Use address.value",
        replaceWith = ReplaceWith("address.value"),
        level = DeprecationLevel.WARNING,
    )
    val ipAddress: String = address.value
}
