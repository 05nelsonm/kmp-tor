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
@file:Suppress("UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import kotlinx.cinterop.*
import org.kotlincrypto.bitops.endian.Endian
import org.kotlincrypto.bitops.endian.Endian.Big.bePackIntoUnsafe
import platform.posix.*
import kotlin.experimental.ExperimentalNativeApi

@OptIn(UnsafeNumber::class)
internal sealed class InetSocketAddress private constructor(
    internal val family: sa_family_t,
    internal val port: Int,
) {

    internal class V4 internal constructor(
        family: sa_family_t,
        port: Int,
        internal val address: in_addr_t,
    ): InetSocketAddress(family, port)

    internal class V6 internal constructor(
        family: sa_family_t,
        port: Int,
        internal val flowInfo: uint32_t,
        internal val scopeId: uint32_t,
    ): InetSocketAddress(family, port)

    internal companion object {

        @Throws(IOException::class)
        @OptIn(ExperimentalForeignApi::class)
        internal fun IPAddress.toInetSocketAddress(port: Int): InetSocketAddress = memScoped {
            val hint: CValue<addrinfo> = cValue {
                ai_family = when (this@toInetSocketAddress) {
                    is IPAddress.V4 -> AF_INET
                    is IPAddress.V6 -> AF_INET6
                }
                ai_socktype = SOCK_STREAM

                // This is only utilized for converting localhost
                // IP address to the addrinfo struct, so AI_NUMERICHOST
                // will mitigate any actual lookup performed by the
                // host machine.
                ai_flags = AI_NUMERICHOST

                ai_protocol = 0
            }

            val result = alloc<CPointerVar<addrinfo>>()
            if (getaddrinfo(value, port.toString(), hint, result.ptr) != 0) {
                throw errnoToIOException(errno)
            }

            defer { freeaddrinfo(result.value) }

            val addr = result.pointed
                ?.ai_addr
                ?.pointed
                ?: throw IOException("Failed to retrieve sockaddr for ${canonicalHostName()}")

            when (this@toInetSocketAddress) {
                is IPAddress.V4 -> {
                    val a = addr.reinterpret<sockaddr_in>()
                    V4(a.sin_family, a.sin_port.toSinPort().toInt(), a.sin_addr.s_addr)
                }
                is IPAddress.V6 -> {
                    val a = addr.reinterpret<sockaddr_in6>()
                    V6(a.sin6_family, a.sin6_port.toSinPort().toInt(), a.sin6_flowinfo, a.sin6_scope_id)
                }
            }
        }

        private fun UShort.toSinPort(): UShort {
            @OptIn(ExperimentalNativeApi::class)
            if (!Platform.isLittleEndian) return this

            val b = toShort().bePackIntoUnsafe(dest = ByteArray(2), destOffset = 0)
            return Endian.Little.shortOf(b[1], b[0]).toUShort()
        }
    }
}
