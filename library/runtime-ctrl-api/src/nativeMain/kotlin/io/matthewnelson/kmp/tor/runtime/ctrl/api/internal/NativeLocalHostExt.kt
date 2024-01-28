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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "FunctionName", "UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.tor.runtime.ctrl.api.internal

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.V4.Companion.toIPAddressV4OrNull
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.V6.Companion.toIPAddressV6OrNull
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import kotlinx.cinterop.*
import platform.posix.*

@Throws(Exception::class)
internal actual fun LocalHost.Companion.tryPlatformResolve(set: LinkedHashSet<IPAddress>) {
    @OptIn(ExperimentalForeignApi::class)
    memScoped {
        val hint: CValue<addrinfo> = cValue {
            ai_family = AF_UNSPEC
            ai_socktype = SOCK_STREAM
            ai_flags = AI_PASSIVE or AI_NUMERICSERV
            ai_protocol = 0
        }

        val result = alloc<CPointerVar<addrinfo>>()

        if (getaddrinfo("localhost", null, hint, result.ptr) != 0) {
            @OptIn(DelicateFileApi::class)
            throw errnoToIOException(errno)
        }

        var info: addrinfo? = result.pointed
        while (info != null) {
            val address = info.ai_addr
                ?.pointed
                ?.toIPAddressOrNull()

            if (address != null) set.add(address)

            info = info.ai_next?.pointed
        }
    }
}

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun sockaddr.toIPAddressOrNull(): IPAddress? {
    return when (sa_family.toInt()) {
        AF_INET -> ptr.reinterpret<sockaddr_in>()
            .pointed
            .toIPAddressV4OrNull()
        AF_INET6 -> ptr.reinterpret<sockaddr_in6>()
            .pointed
            .toIPAddressV6OrNull()
        else -> null
    }
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private inline fun sockaddr_in.toIPAddressV4OrNull(): IPAddress.V4? = memScoped {
    val string = allocArray<ByteVar>(INET_ADDRSTRLEN)
    val value = cValue<in_addr> {
        s_addr = sin_addr.s_addr
    }

    platform_inet_ntop(
        sin_family.convert(),
        value,
        string.reinterpret(),
        INET_ADDRSTRLEN.convert()
    )?.toKString()?.toIPAddressV4OrNull()
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private inline fun sockaddr_in6.toIPAddressV6OrNull(): IPAddress.V6? = memScoped {
    val string = allocArray<ByteVar>(INET6_ADDRSTRLEN)

    platform_inet_ntop(
        sin6_family.convert(),
        sin6_addr.ptr,
        string.reinterpret(),
        INET6_ADDRSTRLEN.convert(),
    )?.toKString()?.toIPAddressV6OrNull()
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(ExperimentalForeignApi::class)
internal expect inline fun platform_inet_ntop(
    family: Int,
    src: CValuesRef<*>?,
    dst: CValuesRef<ByteVar>?,
    size: socklen_t,
): CPointer<ByteVar>?

internal actual fun LocalHost.Companion.execIfConfig(): String {
    // TODO
    return ""
}
