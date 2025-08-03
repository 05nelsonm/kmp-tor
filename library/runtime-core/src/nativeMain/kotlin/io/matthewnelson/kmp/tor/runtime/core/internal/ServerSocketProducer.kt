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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.internal.InetSocketAddress.Companion.toInetSocketAddress
import kotlinx.cinterop.*
import org.kotlincrypto.bitops.endian.Endian
import org.kotlincrypto.bitops.endian.Endian.Big.bePackIntoUnsafe
import platform.posix.*
import kotlin.concurrent.AtomicReference
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class, InternalKmpTorApi::class, UnsafeNumber::class)
internal actual value class ServerSocketProducer private actual constructor(private actual val value: Any) {

    @Throws(Exception::class)
    internal actual fun open(port: Int): Closeable = memScoped {
        val address = (value as IPAddress).toInetSocketAddress(port)

        val sockfd = kmptor_socket(address.family.convert(), SOCK_STREAM, 0)
        if (sockfd == -1) throw errnoToIOException(errno)

        val closeable = object : Closeable {
            private val once = AtomicReference<Int?>(sockfd)
            override fun close() {
                val sockfd = once.getAndSet(null) ?: return
                if (kmptor_socket_close(sockfd) == 0) return
                throw errnoToIOException(errno)
            }
        }

        // TODO: Investigate proper address/port re-use settings...
        // Enable address re-use
        alloc<IntVar> { this.value = 0 }.let { reuseAddress ->
            setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, reuseAddress.ptr, sizeOf<IntVar>().convert())
        }
        // Disable port re-use
        alloc<IntVar> { this.value = 0 }.let { reusePort ->
            setsockopt(sockfd, SOL_SOCKET, SO_REUSEPORT, reusePort.ptr, sizeOf<IntVar>().convert())
        }

        address.doBind { ptr, socklen ->
            if (bind(sockfd, ptr, socklen) != 0) {
                val e = errnoToIOException(errno)
                try {
                    closeable.close()
                } catch (t: IOException) {
                    e.addSuppressed(t)
                }
                throw e
            }
        }

        listen(sockfd, 1).let { result ->
            if (result == 0) return@let
            val e = errnoToIOException(errno)
            try {
                closeable.close()
            } catch (t: IOException) {
                e.addSuppressed(t)
            }
            throw e
        }

        closeable
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

            val b = toShort().bePackIntoUnsafe(dest = ByteArray(2), destOffset = 0)
            return Endian.Little.shortOf(b[1], b[0]).toUShort()
        }
    }
}
