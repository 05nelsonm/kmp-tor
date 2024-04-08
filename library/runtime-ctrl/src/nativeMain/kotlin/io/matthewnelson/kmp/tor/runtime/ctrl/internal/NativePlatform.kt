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

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import kotlinx.cinterop.*
import platform.posix.*

@Throws(Throwable::class)
@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
internal actual fun ProxyAddress.connect(): CtrlConnection = memScoped {
    val (family, len) = when (address) {
        is IPAddress.V4 -> AF_INET to sizeOf<sockaddr_in>()
        is IPAddress.V6 -> AF_INET6 to sizeOf<sockaddr_in6>()
    }

    val hint: CValue<addrinfo> = cValue {
        ai_family = family
        ai_socktype = SOCK_STREAM
        ai_flags = AI_PASSIVE or AI_NUMERICSERV
        ai_protocol = 0
    }
    val result = alloc<CPointerVar<addrinfo>>()

    if (getaddrinfo(address.value, port.toString(), hint, result.ptr) != 0) {
        throw errnoToIOException(errno)
    }

    defer { freeaddrinfo(result.value) }

    var info: addrinfo? = result.pointed
        ?: throw IOException("Failed to resolve addrinfo for ${this@connect}")

    val descriptor = socket(family, SOCK_STREAM, 0)
    if (descriptor < 0) throw errnoToIOException(errno)

    var connected = false
    var lastErrno: Int? = null
    while (info != null && !connected) {
        val sockaddr = info.ai_addr?.pointed

        if (sockaddr != null) {
            connected = connect(descriptor, sockaddr.ptr, len.convert()) == 0
            if (!connected) lastErrno = errno
        }

        info = info.ai_next?.pointed
    }

    if (!connected) {
        close(descriptor)
        throw lastErrno?.let { errnoToIOException(it) }
            ?: IOException("Failed to connect to ${this@connect}")
    }

    NativeCtrlConnection(descriptor)
}
