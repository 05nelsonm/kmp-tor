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
@file:Suppress(
    "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING",
    "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT",
    "UnnecessaryOptInAnnotation",
)

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.internal.InetSocketAddress.Companion.toInetSocketAddress
import kotlinx.cinterop.*
import org.kotlincrypto.endians.BigEndian.Companion.toBigEndian
import platform.posix.*
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class, UnsafeNumber::class)
internal actual value class ServerSocketProducer private actual constructor(
    private actual val value: Any
) {

    @Throws(Exception::class)
    internal actual fun open(port: Int): AutoCloseable = memScoped {
        val address = (value as IPAddress).toInetSocketAddress(port)

        val descriptor = socket(address.family.convert(), SOCK_STREAM, 0)
        if (descriptor < 0) {
            val errno = errno
            val message = strerror(errno)?.toKString() ?: "errno: $errno"
            throw IllegalStateException(message)
        }

        val closeableDescriptor = object : AutoCloseable {
            override fun close() {
                close(descriptor)
            }
        }

        // Enable address re-use
        alloc<IntVar> { this.value = 0 }.let { reuseAddress ->
            setsockopt(descriptor, SOL_SOCKET, SO_REUSEADDR, reuseAddress.ptr, sizeOf<IntVar>().convert())
        }
        // Disable port re-use
        alloc<IntVar> { this.value = 0 }.let { reusePort ->
            setsockopt(descriptor, SOL_SOCKET, SO_REUSEPORT, reusePort.ptr, sizeOf<IntVar>().convert())
        }
        // non-blocking
        if (fcntl(descriptor, F_SETFL, O_NONBLOCK) != 0) {
            closeableDescriptor.use {}
            val errno = errno
            val message = strerror(errno)?.toKString() ?: "errno: $errno"
            throw IllegalStateException(message)
        }

        address.doBind { pointer, size ->
            if (bind(descriptor, pointer, size) != 0) {
                val errno = errno
                closeableDescriptor.use {}
                throw errnoToIOException(errno)
            }
        }

        val closeableShutdown = object : AutoCloseable {
            override fun close() {
                shutdown(descriptor, SHUT_RDWR)
                closeableDescriptor.use {}
            }
        }

        listen(descriptor, 1).let { result ->
            if (result == 0) return@let
            val errno = errno
            val message = strerror(errno)?.toKString() ?: "errno: $errno"
            closeableShutdown.use {}
            throw IllegalStateException(message)
        }

        closeableShutdown
    }

    internal actual companion object {

        @Throws(IOException::class)
        internal actual fun IPAddress.toServerSocketProducer(): ServerSocketProducer {
            return ServerSocketProducer(this)
        }

        private fun InetSocketAddress.doBind(block: (CPointer<sockaddr>, socklen_t) -> Unit) {
            when (this) {
                is InetSocketAddress.V4 -> cValue<sockaddr_in> {
                    sin_family = family
                    sin_addr.s_addr = address
                    sin_port = port.toSinPort()

                    block(ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
                }
                is InetSocketAddress.V6 -> cValue<sockaddr_in6> {
                    sin6_family = family
                    sin6_flowinfo = flowInfo
                    sin6_port = port.toSinPort()
                    sin6_scope_id = scopeId

                    block(ptr.reinterpret(), sizeOf<sockaddr_in6>().convert())
                }
            }
        }

        private fun Int.toSinPort(): UShort {
            @OptIn(ExperimentalNativeApi::class)
            if (!Platform.isLittleEndian) return toUShort()

            return toShort()
                .toBigEndian()
                .toLittleEndian()
                .toShort()
                .toUShort()
        }
    }
}
