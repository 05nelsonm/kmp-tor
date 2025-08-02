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
@file:Suppress("REDUNDANT_ELSE_IN_WHEN")
@file:JvmName("IPAddressUtil")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V4.Companion.toIPAddressV4
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V6.Companion.toIPAddressV6
import java.net.*

/**
 * Convert [java.net.InetAddress] to [IPAddress].
 *
 * @throws [UnsupportedOperationException] if not [Inet4Address] or [Inet6Address].
 * */
@JvmName("ipAddressOf")
public fun InetAddress.toIPAddress(): IPAddress = when (this) {
    is Inet4Address -> toIPAddressV4()
    is Inet6Address -> toIPAddressV6()
    else -> throw UnsupportedOperationException("Unsupported InetAddress type. Must be Inet4 or Inet6.")
}

/**
 * Convert [java.net.Inet4Address] to [IPAddress.V4].
 * */
@JvmName("ipAddressV4Of")
public fun Inet4Address.toIPAddressV4(): IPAddress.V4 {
    return address.toIPAddressV4(copy = false)
}

/**
 * Convert [java.net.Inet6Address] to [IPAddress.V6]
 * */
@JvmName("ipAddressV6Of")
public fun Inet6Address.toIPAddressV6(): IPAddress.V6 {
    val scope = if (scopeId > 0) scopeId.toString() else scopedInterface?.name
    return address.toIPAddressV6(scope, copy = false)
}

/**
 * Convert [IPAddress] to [java.net.InetAddress].
 *
 * @throws [SocketException] if [IPAddress.V6.scope] is a non-null
 *   network interface name that does not exist on the host machine.
 * */
@JvmName("inetAddressOf")
public fun IPAddress.toInetAddress(): InetAddress = when(this) {
    is IPAddress.V4 -> toInet4Address()
    is IPAddress.V6 -> toInet6Address()
}

/**
 * Convert [IPAddress.V4] to [java.net.Inet4Address].
 * */
@JvmName("inet4AddressOf")
public fun IPAddress.V4.toInet4Address(): Inet4Address {
    return Inet4Address.getByAddress(value, addressInternal()) as Inet4Address
}

/**
 * Convert [IPAddress.V6] to [java.net.Inet6Address].
 *
 * @throws [SocketException] if [IPAddress.V6.scope] is a non-null
 *   network interface name that does not exist on the host machine.
 * */
@JvmName("inet6AddressOf")
public fun IPAddress.V6.toInet6Address(): Inet6Address {
    val scope = scope
    val scopeId = scope?.toIntOrNull()

    return when {
        scope == null -> Inet6Address.getByAddress(
            value,
            addressInternal(),
            null
        )
        scopeId != null -> Inet6Address.getByAddress(
            value,
            addressInternal(),
            scopeId,
        )
        else -> {
            val nif = NetworkInterface.getByName(scope)

            Inet6Address.getByAddress(
                value,
                addressInternal(),
                nif
            )
        }
    }
}
