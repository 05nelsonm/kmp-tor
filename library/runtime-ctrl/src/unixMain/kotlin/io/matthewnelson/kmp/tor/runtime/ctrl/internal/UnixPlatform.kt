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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.internal.kmptor_socket
import io.matthewnelson.kmp.tor.runtime.core.internal.kmptor_socket_close
import kotlinx.cinterop.*
import platform.posix.*

@Throws(Throwable::class)
@OptIn(ExperimentalForeignApi::class, InternalKmpTorApi::class, UnsafeNumber::class)
internal actual fun File.connect(): CtrlConnection {
    val sockfd = kmptor_socket(AF_UNIX, SOCK_STREAM, 0)
    if (sockfd < 0) throw errnoToIOException(errno)

    socketAddress(AF_UNIX) { pointer, len ->
        if (connect(sockfd, pointer, len) != 0) {
            val e = errnoToIOException(errno)
            if (kmptor_socket_close(sockfd) == -1) {
                e.addSuppressed(errnoToIOException(errno))
            }
            throw e
        }
    }

    return NativeCtrlConnection(sockfd)
}

@OptIn(ExperimentalForeignApi::class)
internal expect inline fun File.socketAddress(
    family: Int,
    block: (CValuesRef<sockaddr>, len: socklen_t) -> Unit,
)
