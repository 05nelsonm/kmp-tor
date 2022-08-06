package io.matthewnelson.kmp.tor.common.address

import kotlin.jvm.JvmStatic

/**
 * Base interface for denoting a String value is an [IPAddress]
 *
 * @see [Address]
 * @see [IPAddressV4]
 * @see [IPAddressV6]
 * */
sealed interface IPAddress: Address {

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): IPAddress {
            return IPAddressV4.fromStringOrNull(address)
                ?: IPAddressV6.fromStringOrNull(address)
                ?: throw IllegalArgumentException("Failed to find an IPv4 or IPv6 address from $address")
        }

        @JvmStatic
        fun fromStringOrNull(address: String): IPAddress? {
            return try {
                fromString(address)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
