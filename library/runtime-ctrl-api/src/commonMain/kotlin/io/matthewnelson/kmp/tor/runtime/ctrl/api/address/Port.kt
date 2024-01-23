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

import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Proxy.Companion.toPortProxyOrNull
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.findHostnameAndPortFromURL
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
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
