package io.matthewnelson.kmp.tor.common.address

import io.matthewnelson.component.parcelize.Parcelable
import kotlin.jvm.JvmStatic

/**
 * Base interface for denoting a String value is an [IPAddress]
 *
 * @see [IPAddressV4]
 * @see [IPAddressV6]
 * */
sealed interface IPAddress: Parcelable {

    val value: String

    /**
     * Prints the [IPAddress] as it's canonicalized hostname.
     *
     * [IPAddressV4] -> "127.0.0.1"
     * [IPAddressV6] -> "[::1]" // bracketed
     * */
    fun canonicalHostname(): String

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): IPAddress {
            return IPAddressV4.fromStringOrNull(address)
                ?: IPAddressV6.fromStringOrNull(address)
                ?: throw IllegalArgumentException("'$address' was neither an IPv4 or IPv6 address")
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
