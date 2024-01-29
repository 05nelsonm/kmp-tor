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

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import kotlinx.cinterop.*
import org.kotlincrypto.endians.BigEndian.Companion.toBigEndian
import platform.posix.*
import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class, DelicateFileApi::class, UnsafeNumber::class)
internal actual value class InetAddressWrapper private actual constructor(
    private actual val value: Any
) {

    @Throws(Exception::class)
    internal actual fun openServerSocket(port: Int): AutoCloseable {
        val descriptor = memScoped {
            val address = value as Address

            val descriptor = socket(address.family.convert(), SOCK_STREAM, 0)
            if (descriptor < 0) {
                val errno = errno
                val message = strerror(errno)?.toKString() ?: "errno: $errno"
                throw IllegalStateException(message)
            }

            // Enable address re-use
            alloc<IntVar> { this.value = 1 }.let { reuseAddress ->
                setsockopt(descriptor, SOL_SOCKET, SO_REUSEADDR, reuseAddress.ptr, sizeOf<IntVar>().convert())
            }
            // Disable port re-use
            alloc<IntVar> { this.value = 0 }.let { reusePort ->
                setsockopt(descriptor, SOL_SOCKET, SO_REUSEPORT, reusePort.ptr, sizeOf<IntVar>().convert())
            }
            // non-blocking
            if (fcntl(descriptor, F_SETFL, O_NONBLOCK) != 0) {
                val errno = errno
                val message = strerror(errno)?.toKString() ?: "errno: $errno"
                throw IllegalStateException(message)
            }

            address.callback(port) { pointer, size ->
                val result = bind(descriptor, pointer, size)
                if (result != 0) throw errnoToIOException(errno)
            }

            listen(descriptor, 1).let { result ->
                if (result == 0) return@let
                val errno = errno
                val message = strerror(errno)?.toKString() ?: "errno: $errno"
                throw IllegalStateException(message)
            }

            descriptor
        }

        return object : AutoCloseable {
            override fun close() {
                shutdown(descriptor, SHUT_RDWR)
                close(descriptor)
            }
        }
    }

    internal actual companion object {

        @Throws(IOException::class)
        internal actual fun IPAddress.toInetAddressWrapper(): InetAddressWrapper = memScoped {
            val hint: CValue<addrinfo> = cValue {
                ai_family = when (this@toInetAddressWrapper) {
                    is IPAddress.V4 -> AF_INET
                    is IPAddress.V6 -> AF_INET6
                }
                ai_socktype = SOCK_STREAM
                ai_flags = AI_NUMERICHOST
                ai_protocol = 0
            }

            val result = alloc<CPointerVar<addrinfo>>()
            if (getaddrinfo(value, null, hint, result.ptr) != 0) {
                throw errnoToIOException(errno)
            }

            defer { freeaddrinfo(result.value) }

            val addr = result.pointed
                ?.ai_addr
                ?.pointed
                ?: throw IOException("Failed to retrieve sockaddr for ${canonicalHostname()}")

            val family: sa_family_t = addr.sa_family

            val callback: Callback = when (this@toInetAddressWrapper) {
                is IPAddress.V4 -> {
                    val sockaddr = addr.reinterpret<sockaddr_in>()
                    val address: in_addr_t = sockaddr.sin_addr.s_addr

                    Callback { port, block ->
                        cValue<sockaddr_in> {
                            sin_addr.s_addr = address
                            sin_port = portToSinPort(port)
                            sin_family = family

                            block(ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
                        }
                    }
                }
                is IPAddress.V6 -> {
                    val sockaddr = addr.reinterpret<sockaddr_in6>()
                    val flowInfo: uint32_t = sockaddr.sin6_flowinfo
                    val scopeId: uint32_t = sockaddr.sin6_scope_id

                    Callback { port, block ->
                        cValue<sockaddr_in6> {
                            sin6_family = family
                            sin6_flowinfo = flowInfo
                            sin6_port = portToSinPort(port)
                            sin6_scope_id = scopeId

                            block(ptr.reinterpret(), sizeOf<sockaddr_in6>().convert())
                        }
                    }
                }
            }

            InetAddressWrapper(Address(family, callback))
        }

        private fun portToSinPort(port: Int): UShort {
            @OptIn(ExperimentalNativeApi::class)
            if (!Platform.isLittleEndian) return port.toUShort()

            return port.toShort()
                .toBigEndian()
                .toLittleEndian()
                .toShort()
                .toUShort()
        }
    }

    private class Address(val family: sa_family_t, val callback: Callback)

    private fun interface Callback {
        operator fun invoke(
            port: Int,
            block: (CPointer<sockaddr>, socklen_t) -> Unit,
        )
    }
}
