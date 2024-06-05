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

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder
import io.matthewnelson.encoding.core.EncodingException
import io.matthewnelson.encoding.core.use
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4OrNull
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V6.Companion.toIPAddressV6
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V6.Companion.toIPAddressV6OrNull
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort
import io.matthewnelson.kmp.tor.runtime.core.internal.HostAndPort.Companion.findHostnameAndPortFromURL
import kotlin.jvm.*

/**
 * Base abstraction for denoting an ip address
 *
 * @see [V4]
 * @see [V6]
 * */
public sealed class IPAddress private constructor(
    @JvmField
    protected val bytes: ByteArray,
    value: String,
): Address(value) {

    /**
     * Returns the raw bytes for this [IPAddress] in network byte order.
     * Will either be 4 or 16 bytes in length ([V4] or [V6] respectively).
     * */
    public fun address(): ByteArray = bytes.copyOf()

    public companion object {

        /**
         * Parses a String for its IPv4 or IPv6 address.
         *
         * String can be either a URL containing the IP address, or the
         * IPv4/IPv6 address itself.
         *
         * @return [IPAddress]
         * @throws [IllegalArgumentException] if no IP address is found
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun String.toIPAddress(): IPAddress {
            return toIPAddressOrNull()
                ?: throw IllegalArgumentException("$this does not contain an IP address")
        }

        /**
         * Converts network ordered bytes to an IPv4 or IPv6 address.
         *
         * @return [IPAddress]
         * @throws [IllegalArgumentException] if array size is not 4 or 16.
         * */
        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun ByteArray.toIPAddress(): IPAddress = when (size) {
            4 -> toIPAddressV4()
            16 -> toIPAddressV6()
            else -> throw IllegalArgumentException("Invalid array size[$size]")
        }

        /**
         * Parses a String for its IPv4 or IPv6 address.
         *
         * String can be either a URL containing the IP address, or the
         * IPv4/IPv6 address itself.
         *
         * @return [IPAddress] or null
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun String.toIPAddressOrNull(): IPAddress? {
            return findHostnameAndPortFromURL().toIPAddressOrNull()
        }

        /**
         * Converts network ordered bytes to an IPv4 or IPv6 address.
         *
         * @return [IPAddress] or null if array size is not 4 or 16.
         * */
        @JvmStatic
        @JvmName("getOrNull")
        public fun ByteArray.toIPAddressOrNull(): IPAddress? = try {
            toIPAddress()
        } catch (_: IllegalArgumentException) {
            null
        }

        @JvmSynthetic
        internal fun HostAndPort.toIPAddressOrNull(): IPAddress? {
            return toIPAddressV4OrNull() ?: toIPAddressV6OrNull()
        }
    }

    /**
     * Holder for an IPv4 address
     * */
    public open class V4 private constructor(bytes: ByteArray, value: String): IPAddress(bytes, value) {

        /**
         * `0.0.0.0`
         * */
        public object AnyHost: V4(ByteArray(4) { 0 }, "0.0.0.0")

        public companion object {

            /**
             * Parses a String for its IPv4 address.
             *
             * String can be either a URL containing the IPv4 address, or the
             * IPv4 address itself.
             *
             * @return [IPAddress.V4]
             * @throws [IllegalArgumentException] if no IPv4 address is found.
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toIPAddressV4(): V4 {
                return toIPAddressV4OrNull()
                    ?: throw IllegalArgumentException("$this does not contain an IPv4 address")
            }

            /**
             * Convert network ordered bytes to an IPv4 address.
             *
             * @return [IPAddress.V4]
             * @throws [IllegalArgumentException] if array size is not 4.
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toIPAddressV4(): V4 {
                return toIPAddressV4OrNull()
                    ?: throw IllegalArgumentException("Array must be 4 bytes in length")
            }

            /**
             * Parses a String for its IPv4 address.
             *
             * String can be either a URL containing the IPv4 address, or the
             * IPv4 address itself.
             *
             * @return [IPAddress.V4] or null if no IPv4 address is found.
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toIPAddressV4OrNull(): V4? {
                return findHostnameAndPortFromURL().toIPAddressV4OrNull()
            }

            /**
             * Convert network ordered bytes to an IPv4 address.
             *
             * @return [IPAddress.V4] or null if array size is not 4.
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun ByteArray.toIPAddressV4OrNull(): V4? {
                if (size != 4) return null

                var anyHost = true
                var loopback = true
                for (i in indices) {
                    val b = this[i]
                    if (anyHost && b != AnyHost.bytes[i]) {
                        anyHost = false
                    }
                    if (loopback && b != Loopback.bytes[i]) {
                        loopback = false
                    }
                    if (!anyHost && !loopback) break
                }

                if (anyHost) return AnyHost
                if (loopback) return Loopback

                val hostAddress = buildString(capacity = 3 + (3 * 4)) {
                    joinTo(this, ".") { it.toUByte().toString() }
                }

                return V4(copyOf(), hostAddress)
            }

            @JvmSynthetic
            internal fun HostAndPort.toIPAddressV4OrNull(): V4? {
                val stripped = value.substringBeforeLast(':')

                if (stripped == AnyHost.value) return AnyHost
                if (stripped == Loopback.value) return Loopback

                val splits = stripped.split('.')
                if (splits.size != 4) return null

                val bytes = try {
                    ByteArray(4) { i -> splits[i].toUByte().toByte() }
                } catch (_: NumberFormatException) {
                    null
                }

                if (bytes == null) return null

                return V4(bytes, stripped)
            }

            @JvmSynthetic
            internal fun loopback(): V4 = Loopback

            // Testing
            @JvmSynthetic
            internal fun V4.isLoopback(): Boolean = this is Loopback
        }

        // Typical IPv4 loopback address of 127.0.0.1
        private object Loopback: V4(byteArrayOf(127, 0, 0, 1), "127.0.0.1")
    }

    /**
     * Holder for an IPv6 address
     *
     * **NOTE:** No resolution of device network interfaces
     * are performed for a non-null [scope].
     *
     * @param [scope] The network interface name or index
     *   number, or null if no scope was expressed.
     * */
    public open class V6 private constructor(
        @JvmField
        public val scope: String?,
        bytes: ByteArray,
        value: String,
    ): IPAddress(bytes, value + if (scope == null) "" else "%$scope") {

        /**
         * `::0`
         *
         * @see [of]
         * @see [NoScope]
         * */
        public open class AnyHost private constructor(
            scope: String?,
            bytes: ByteArray,
            value: String,
        ): V6(scope, bytes, value) {

            /**
             * Static instance of [AnyHost] that does not have a [scope].
             * */
            public companion object NoScope: AnyHost(
                scope = null,
                bytes = ByteArray(16) { 0 },
                value = "0:0:0:0:0:0:0:0"
            ) {

                /**
                 * Returns [AnyHost] with provided [scope], or the [NoScope] instance
                 * if [scope] is null.
                 *
                 * @param [scope] The network interface name or index number, or null.
                 * @throws [IllegalArgumentException] if non-null [scope] is an empty
                 *   string, or an integer less than 1.
                 * */
                @JvmStatic
                @Throws(IllegalArgumentException::class)
                public fun of(scope: String?): AnyHost {
                    if (scope == null) return NoScope
                    val msg = scope.isValidScopeOrErrorMessage()
                    if (msg != null) throw IllegalArgumentException(msg)
                    return AnyHost(scope, bytes, value)
                }
            }
        }

        public companion object {

            /**
             * Parses a String for its IPv6 address.
             *
             * String can be either a URL containing the IPv6 address, or the
             * IPv6 address itself.
             *
             * @return [IPAddress.V6]
             * @throws [IllegalArgumentException] if no IPv6 address is found
             * */
            @JvmStatic
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun String.toIPAddressV6(): V6 {
                return toIPAddressV6OrNull()
                    ?: throw IllegalArgumentException("$this does not contain a valid IPv6 address")
            }

            /**
             * Convert network ordered bytes to an IPv6 address.
             *
             * @param [scope] The network interface name or index number, or null.
             * @return [IPAddress.V6]
             * @throws [IllegalArgumentException] if array size is not 16, or
             *   if non-null scope is an empty string or an integer less than 1.
             * */
            @JvmStatic
            @JvmOverloads
            @JvmName("get")
            @Throws(IllegalArgumentException::class)
            public fun ByteArray.toIPAddressV6(scope: String? = null): V6 {
                return toIPAddressV6(scope, copy = true)
            }

            /**
             * Parses a String for its IPv6 address.
             *
             * String can be either a URL containing the IPv6 address, or the
             * IPv6 address itself.
             *
             * @return [IPAddress.V6] or null
             * */
            @JvmStatic
            @JvmName("getOrNull")
            public fun String.toIPAddressV6OrNull(): V6? {
                return findHostnameAndPortFromURL().toIPAddressV6OrNull()
            }

            /**
             * Convert network ordered bytes to an IPv6 address.
             *
             * @param [scope] The network interface name or index number, or null.
             * @return [IPAddress.V6] or null if array size is not 16, or if
             *   non-null scope is an empty string or an integer less than 1.
             * */
            @JvmStatic
            @JvmOverloads
            @JvmName("getOrNull")
            public fun ByteArray.toIPAddressV6OrNull(scope: String? = null): V6? = try {
                toIPAddressV6(scope)
            } catch (_: IllegalArgumentException) {
                null
            }

            @JvmSynthetic
            internal fun HostAndPort.toIPAddressV6OrNull(): V6? {
                if (value.isEmpty()) return null
                var stripped = value

                // Square brackets
                run {
                    val iClosing = stripped.indexOfLast { it == ']' }
                    val startBracket = stripped.startsWith('[')

                    // No start bracket, yes closing bracket. Invalid.
                    if (!startBracket && iClosing != -1) return null

                    if (iClosing == -1) {
                        // Yes start bracket, no closing bracket. Invalid.
                        if (startBracket) return null

                        // No start bracket, no closing bracket. Valid.
                    } else {
                        // Yes start bracket, yes closing bracket. Strip.
                        stripped = stripped.substring(1, iClosing)
                    }
                }

                val scope: String? = run {
                    val iPct = stripped.indexOfLast { it == '%' }
                    if (iPct == -1) return@run null

                    @Suppress("LocalVariableName")
                    val _scope = stripped.substring(iPct + 1)

                    // Interface name or index number bad. Invalid.
                    if (_scope.isValidScopeOrErrorMessage() != null) return null

                    stripped = stripped.substring(0, iPct)
                    _scope
                }

                // Early elimination for some common values.
                when (stripped) {
                    "::", // Eliminating `::` early makes parsing blocks easier
                    "::0", AnyHost.value -> AnyHost.of(scope)
                    "::1", Loopback.value -> Loopback.of(scope)
                    else -> null
                }?.let { return it }

                val blocks8: List<String> = stripped.split(':', limit = 10).let { split ->
                    // min (3)         to max (9)
                    // *:: or ::*      to ::*:*:*:*:*:*:* or *:*:*:*:*:*:*::
                    if (split.size !in 3..9) return@let emptyList()

                    var iExpand = -1

                    val blocks: MutableList<String> = run {
                        val emptyFirst = split.first().isEmpty()
                        val emptyLast = split.last().isEmpty()

                        // Started and ended with `:`, but `::` was eliminated. Invalid.
                        if (emptyFirst && emptyLast) return@let emptyList()

                        @Suppress("LocalVariableName")
                        val _blocks = (split as? MutableList<String>) ?: split.toMutableList()

                        // Replace first/last empty block with `0`
                        if (emptyFirst) {
                            // Must start with ::, otherwise invalid.
                            iExpand = 1
                            _blocks.removeFirst()
                            _blocks.add(0, "0")
                        }
                        if (emptyLast) {
                            // Must end with ::, otherwise invalid.
                            iExpand = split.lastIndex - 1
                            _blocks.removeLast()
                            _blocks.add("0")
                        }

                        _blocks
                    }

                    var hasEmptyBlock = false

                    for (i in blocks.indices) {
                        if (blocks[i].isNotEmpty()) continue
                        hasEmptyBlock = true

                        if (iExpand == -1) {
                            iExpand = i
                            continue
                        }

                        // Multiple `::` expressions. Invalid.
                        if (iExpand != i) return@let emptyList()
                    }

                    // No expression of `::`
                    if (iExpand == -1) return@let blocks

                    // Have single `::` expression. Deal with it.

                    // Indicates that first or last block was
                    // empty at start and replaced with `0` + had
                    // iExpanded set to the expected index, but
                    // parsing all blocks did not observe any empty
                    // blocks at all.
                    //
                    // So, started or ended with single `:` instead
                    // of expected `::`. Invalid.
                    if (!hasEmptyBlock) return@let emptyList()

                    blocks.removeAt(iExpand)
                    while (blocks.size < 8) { blocks.add(iExpand, "0") }
                    blocks
                }

                if (blocks8.size != 8) return null

                var iB = 0
                val bytes = ByteArray(16)

                // 8 non-empty blocks. Decode.
                try {
                    BASE_16.newDecoderFeed { byte -> bytes[iB++] = byte }.use { feed ->
                        for (block in blocks8) {
                            val iNonZero = block.indexOfFirst { it != '0' }

                            // zero block
                            if (iNonZero == -1) {
                                iB += 2
                                continue
                            }

                            val len = block.length - iNonZero

                            // Block exceeds 2 bytes. Invalid.
                            if (len > 4) break

                            // If less than 4 characters, prefix with `0`
                            repeat(4 - len) { feed.consume('0') }

                            repeat(len) { i -> feed.consume(block[i + iNonZero]) }
                        }
                    }
                } catch (_: EncodingException) {}

                // Either encountered bad block length or encoding
                // exception (non-hex character). Invalid.
                if (iB != bytes.size) return null

                // Scope already validated. Will not throw.
                return bytes.toIPAddressV6(scope, copy = false)
            }

            @Throws(IllegalArgumentException::class)
            private fun ByteArray.toIPAddressV6(scope: String?, copy: Boolean): V6 {
                require(size == 16) { "Array must be 16 bytes in length" }
                val bytes = if (copy) copyOf() else this

                var anyHost = true
                var loopback = true
                for (i in indices) {
                    val b = bytes[i]
                    if (anyHost && b != AnyHost.bytes[i]) {
                        anyHost = false
                    }
                    if (loopback && b != Loopback.bytes[i]) {
                        loopback = false
                    }
                    if (!anyHost && !loopback) break
                }

                if (anyHost) return AnyHost.of(scope)
                if (loopback) return Loopback.of(scope)

                scope?.isValidScopeOrErrorMessage()?.let { msg ->
                    throw IllegalArgumentException(msg)
                }

                val sb = StringBuilder((4 * 8) + 7)

                var count = 0
                BASE_16.newEncoderFeed(out = Encoder.OutFeed { char ->
                    val isBlockEnd = ++count % 4 == 0

                    // Trim leading 0 chars from each block.
                    if (char == '0' && !isBlockEnd) {
                        val last = sb.lastOrNull() ?: return@OutFeed
                        if (last == ':') return@OutFeed
                    }

                    sb.append(char)

                    if (isBlockEnd && count < 32) sb.append(':')
                }).use { feed ->
                    bytes.forEach { byte -> feed.consume(byte) }
                }

                return V6(scope, bytes, sb.toString())
            }

            @JvmSynthetic
            internal fun loopback(): V6 = Loopback.NoScope

            // Testing
            @JvmSynthetic
            internal fun V6.isLoopback(): Boolean = this is Loopback


            // Returns null if valid, or an error message if invalid
            private fun String.isValidScopeOrErrorMessage(): String? {
                if (isEmpty()) {
                    return "Invalid scope. Must be the interface name or number."
                }

                val index = toIntOrNull() ?: return null // Interface name
                if (index > 0) return null
                return "Invalid scope. Interface number must be greater than 0."
            }

            private val BASE_16 = Base16 { strict(); encodeToLowercase = true }
        }

        private open class Loopback private constructor(
            scope: String?,
            bytes: ByteArray,
            value: String,
        ): V6(scope, bytes, value) {

            companion object NoScope: Loopback(
                scope = null,
                bytes = ByteArray(16) { i -> if (i == 15) 1 else 0 },
                value = "0:0:0:0:0:0:0:1",
            ) {

                @Throws(IllegalArgumentException::class)
                fun of(scope: String?): Loopback {
                    if (scope == null) return NoScope
                    val msg = scope.isValidScopeOrErrorMessage()
                    if (msg != null) throw IllegalArgumentException(msg)
                    return Loopback(scope, bytes, value)
                }
            }
        }
    }
}
