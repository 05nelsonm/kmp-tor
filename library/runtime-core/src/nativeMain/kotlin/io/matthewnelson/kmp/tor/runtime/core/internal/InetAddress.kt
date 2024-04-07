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
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import kotlinx.cinterop.*
import platform.posix.*

@InternalKmpTorApi
@OptIn(UnsafeNumber::class)
public sealed class InetAddress private constructor(public val family: sa_family_t) {

    public class V4 internal constructor(
        family: sa_family_t,
        public val address: in_addr_t,
    ): InetAddress(family)

    public class V6 internal constructor(
        family: sa_family_t,
        public val flowInfo: uint32_t,
        public val scopeId: uint32_t,
    ): InetAddress(family)

    public companion object {

        @InternalKmpTorApi
        @ExperimentalForeignApi
        @Throws(IOException::class)
        public fun IPAddress.toInetAddress(): InetAddress = memScoped {
            val hint: CValue<addrinfo> = cValue {
                ai_family = when (this@toInetAddress) {
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

            when (this@toInetAddress) {
                is IPAddress.V4 -> {
                    val a = addr.reinterpret<sockaddr_in>()
                    V4(addr.sa_family, a.sin_addr.s_addr)
                }
                is IPAddress.V6 -> {
                    val a = addr.reinterpret<sockaddr_in6>()
                    V6(addr.sa_family, a.sin6_flowinfo, a.sin6_scope_id)
                }
            }
        }
    }
}
