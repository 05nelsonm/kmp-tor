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
package io.matthewnelson.kmp.tor.runtime.core.address

import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.MAX
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.MIN
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeralOrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort.Companion.findHostnameAndPortFromURL
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Holder for an integer between [MIN] and [MAX] (inclusive).
 *
 * e.g.
 *
 *     443.toPort()
 *     "443".toPort()
 *     "http://example.com:8080".toPort()
 *     "http://[::1]:8181".toPort()
 *
 * @see [toPort]
 * @see [toPortOrNull]
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.isAvailableAsync]
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.isAvailableSync]
 * */
public open class Port private constructor(
    @JvmField
    public val value: Int,
): Comparable<Port> {

    public companion object {

        /**
         * The minimum integer value of a [Port], `0`
         * */
        public const val MIN: Int = 0

        /**
         * The maximum integer value of a [Port], `65535`
         * */
        public const val MAX: Int = 65535

        /**
         * Wraps the integer in the [Port] class.
         *
         * If the value is within [Ephemeral.MIN] and [Ephemeral.MAX],
         * then [Ephemeral] is always returned.
         *
         * @throws [IllegalArgumentException] if not within [MIN] and
         *   [MAX] (inclusive).
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun Int.toPort(): Port {
            return toPortOrNull()
                ?: throw IllegalArgumentException("$this is not a valid port")
        }

        /**
         * Parses a String for a port between [MIN] and [MAX] (inclusive).
         *
         * String can be either a URL containing the port, or the
         * port string itself.
         *
         * If the value is within [Ephemeral.MIN] and [Ephemeral.MAX],
         * then [Ephemeral] is always returned.
         *
         * @throws [IllegalArgumentException] if no port is found.
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun String.toPort(): Port {
            return toPortOrNull()
                ?: throw IllegalArgumentException("$this does not contain a port")
        }

        /**
         * Wraps the integer in the [Port] class.
         *
         * If the value is within [Ephemeral.MIN] and [Ephemeral.MAX],
         * then [Ephemeral] is always returned.
         *
         * @return [Port] or `null` if not within [MIN] and [MAX] (inclusive)
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun Int.toPortOrNull(): Port? {
            // Try Port.Ephemeral first (more constrained)
            toPortEphemeralOrNull()?.let { return it }
            if (this !in MIN..MAX) return null
            return Port(this)
        }

        /**
         * Parses a String for a port between 0 and 65535 (inclusive).
         *
         * String can be either a URL containing the port, or the
         * port string itself.
         *
         * If the value is within [Ephemeral.MIN] and [Ephemeral.MAX],
         * then [Ephemeral] is always returned.
         *
         * @return [Port] or `null` if no port is found.
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun String.toPortOrNull(): Port? {
            // Try quick win first
            toIntOrNull()?.let { return it.toPortOrNull() }
            return findHostnameAndPortFromURL().toPortOrNull()
        }

        @JvmSynthetic
        internal fun HostAndPort.toPortOrNull(): Port? {
            var iLastColon = -1

            val checkIPv6 = run {
                var numColons = 0
                for (i in value.indices.reversed()) {
                    val c = value[i]
                    if (c != ':') continue
                    if (iLastColon == -1) iLastColon = i
                    numColons++
                    if (numColons > 1) break
                }

                numColons > 1
            }

            if (iLastColon == -1) return null

            if (checkIPv6) {
                // Ensure bracketed
                if (!value.startsWith('[')) return null
                val c = value.elementAtOrNull(iLastColon - 1)
                if (c != ']') return null
            }

            return value
                .substring(iLastColon + 1)
                .toIntOrNull()
                ?.toPortOrNull()
        }
    }

    /**
     * A [Port] with a more constrained range of [Ephemeral.MIN] to
     * [Ephemeral.MIN] (inclusive), in accordance with that specified in
     * [RFC 6056 section 3.2](https://datatracker.ietf.org/doc/html/rfc6056#section-3.2)
     *
     * e.g.
     *
     *     443.toPortEphemeralOrNull()
     *     "443".toPortEphemeralOrNull()
     *     "http://example.com:8080".toPortEphemeral()
     *     "http://[::1]:8181".toPortEphemeral()
     *
     * @see [toPortEphemeral]
     * @see [toPortEphemeralOrNull]
     * @see [io.matthewnelson.kmp.tor.runtime.core.util.isAvailableAsync]
     * @see [io.matthewnelson.kmp.tor.runtime.core.util.isAvailableSync]
     * @see [io.matthewnelson.kmp.tor.runtime.core.util.findNextAvailableAsync]
     * @see [io.matthewnelson.kmp.tor.runtime.core.util.findAvailableSync]
     * */
    public class Ephemeral private constructor(value: Int): Port(value) {

        public companion object {

            /**
             * The minimum integer value of a [Port.Ephemeral], `1024`
             * */
            public const val MIN: Int = 1024

            /**
             * The maximum integer value of a [Port.Ephemeral], `65535`
             * */
            public const val MAX: Int = Port.MAX

            /**
             * Wraps the integer in the [Ephemeral] class.
             *
             * @throws [IllegalArgumentException] if not within [MIN] and
             *   [MAX] (inclusive).
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun Int.toPortEphemeral(): Ephemeral {
                return toPortEphemeralOrNull()
                    ?: throw IllegalArgumentException("$this is not a valid ephemeral port")
            }

            /**
             * Parses a String for a port between [MIN] and [MAX] (inclusive).
             *
             * String can be either a URL containing the ephemeral port, or the
             * ephemeral port string itself.
             *
             * @throws [IllegalArgumentException] if no ephemeral port is found.
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toPortEphemeral(): Ephemeral {
                return toPortEphemeralOrNull()
                    ?: throw IllegalArgumentException("$this does not contain a valid ephemeral port")
            }

            /**
             * Wraps the integer in the [Ephemeral] class.
             *
             * @return [Ephemeral] or `null` if not within [MIN] and [MAX] (inclusive).
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun Int.toPortEphemeralOrNull(): Ephemeral? {
                if (this !in MIN..MAX) return null
                return Ephemeral(this)
            }

            /**
             * Parses a String for a port between [MIN] and [MAX] (inclusive).
             *
             * String can be either a URL containing the ephemeral port, or the
             * ephemeral port string itself.
             *
             * @return [Ephemeral] or `null` if no ephemeral port is found.
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toPortEphemeralOrNull(): Ephemeral? {
                val port = toPortOrNull()
                if (port is Ephemeral) return port
                return null
            }
        }
    }

    public final override fun compareTo(other: Port): Int = value.compareTo(other.value)

    /** @suppress */
    public final override fun equals(other: Any?): Boolean = other is Port && other.value == value
    /** @suppress */
    public final override fun hashCode(): Int = 17 * 31 + value.hashCode()
    /** @suppress */
    public final override fun toString(): String = value.toString()
}
