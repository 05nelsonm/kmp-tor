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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.address

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Proxy.Companion.toPortProxyOrNull
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.PortAvailability
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.findHostnameAndPortFromURL
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Holder for a port between 0 and 65535 (inclusive)
 *
 * @see [Proxy]
 * */
public open class Port private constructor(
    @JvmField
    public val value: Int,
): Comparable<Port> {

    public final override fun compareTo(other: Port): Int = value.compareTo(other.value)

    /**
     * Checks if the TCP port is available or not for the specified [address].
     *
     * **NOTE:** This is a blocking call and should be invoked from
     * a background thread.
     *
     * @param [address] The [IPAddress] to check. If null, [LocalHost.resolveIPv4] is used
     * @throws [IOException] if the check fails (e.g. calling from Main thread on Android)
     * */
    @JvmOverloads
    @Throws(IOException::class)
    public fun isAvailable(address: IPAddress? = null): Boolean {
        val ipAddress = address ?: LocalHost.resolveIPv4()

        try {
            return PortAvailability.of(ipAddress).isAvailable(value)
        } catch (t: Throwable) {
            throw t.wrapIOException()
        }
    }

    public companion object {

        public const val MIN: Int = 0
        public const val MAX: Int = 65535

        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun Int.toPort(): Port {
            return toPortOrNull()
                ?: throw IllegalArgumentException("$this is not a valid port")
        }

        /**
         * Parses a String for a port between 0 and 65535 (inclusive).
         *
         * String can be either a URL containing the port, or the
         * port itself.
         *
         * @return [Port]
         * @throws [IllegalArgumentException] if no port is found
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun String.toPort(): Port {
            return toPortOrNull()
                ?: throw IllegalArgumentException("$this does not contain a port")
        }

        @JvmStatic
        @JvmName("getOrNull")
        public fun Int.toPortOrNull(): Port? {
            // Try Port.Proxy first (more constrained)
            toPortProxyOrNull()?.let { return it }
            if (this !in MIN..MAX) return null
            return Port(this)
        }

        /**
         * Parses a String for a port between 0 and 65535 (inclusive).
         *
         * String can be either a URL containing the port, or the
         * port itself.
         *
         * @return [Port] or null
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun String.toPortOrNull(): Port? {
            // Try quick win first
            toIntOrNull()?.let { port -> return port.toPortOrNull() }

            // Try parsing as URL
            return findHostnameAndPortFromURL()
                .substringAfterLast(':')
                .toIntOrNull()
                ?.toPortOrNull()
        }
    }

    /**
     * A [Port] with a more constrained range of 1024 and 65535 (inclusive)
     * */
    public class Proxy private constructor(value: Int): Port(value) {

        /**
         * Finds the next available TCP port starting with the current
         * [value] and iterating up [limit] times.
         *
         * If [MAX] is exceeded while iterating through ports and [limit]
         * has not been exhausted, the remaining checks will start from [MIN].
         *
         * **NOTE:** This is a blocking call and should be invoked from
         * a background thread.
         *
         * @param [address] The [IPAddress] to check. If null, [LocalHost.resolveIPv4] is used
         * @throws [IllegalArgumentException] if [limit] is not between 1 to 10_000 (inclusive)
         * @throws [IOException] if the check fails (e.g. calling from Main thread on Android)
         * */
        @JvmOverloads
        @Throws(IllegalArgumentException::class, IOException::class)
        public fun findAvailable(limit: Int, address: IPAddress? = null): Proxy {
            require(limit in 1..10_000) { "limit must be between 1 to 10_000 (inclusive)" }

            val ipAddress = address ?: LocalHost.resolveIPv4()

            val availability = try {
                PortAvailability.of(ipAddress)
            } catch (t: Throwable) {
                throw t.wrapIOException {
                    "Failed to create ${PortAvailability::class.simpleName} for ${ipAddress.canonicalHostname()}"
                }
            }

            var remaining = limit
            var port = value

            while (remaining-- > 0) {
                if (availability.isAvailable(port)) return Proxy(port)
                port = if (port == Port.MAX) MIN else port + 1
            }

            val top = value + limit
            val ranges = if (top <= MAX) {
                "($value - $top)"
            } else {
                val bottom = top - MAX + MIN
                "($value - $MAX) and ($MIN - $bottom)"
            }

            throw IOException("Failed to find available port for ${ipAddress.canonicalHostname()} $ranges")
        }

        public companion object {

            public const val MIN: Int = 1024
            public const val MAX: Int = Port.MAX

            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun Int.toPortProxy(): Proxy {
                return toPortProxyOrNull()
                    ?: throw IllegalArgumentException("$this is not a valid proxy port")
            }

            /**
             * Parses a String for a port between 1024 and 65535 (inclusive).
             *
             * String can be either a URL containing the port, or the
             * port itself.
             *
             * @return [Port.Proxy]
             * @throws [IllegalArgumentException] if no port is found
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toPortProxy(): Proxy {
                return toPortProxyOrNull()
                    ?: throw IllegalArgumentException("$this does not contain a valid proxy port")
            }

            @JvmStatic
            @JvmName("getOrNull")
            public fun Int.toPortProxyOrNull(): Proxy? {
                if (this !in MIN..MAX) return null
                return Proxy(this)
            }

            /**
             * Parses a String for a port between 1024 and 65535 (inclusive).
             *
             * String can be either a URL containing the port, or the
             * port itself.
             *
             * @return [Port.Proxy] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toPortProxyOrNull(): Proxy? {
                val port = toPortOrNull()
                if (port is Proxy) return port
                return null
            }
        }
    }

    public final override fun equals(other: Any?): Boolean = other is Port && other.value == value
    public final override fun hashCode(): Int = 17 * 31 + value.hashCode()
    public final override fun toString(): String = value.toString()
}
