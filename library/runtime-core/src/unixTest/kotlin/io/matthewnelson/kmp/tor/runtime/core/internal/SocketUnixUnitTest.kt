/*
 * Copyright (c) 2025 Matthew Nelson
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
package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.errnoToIOException
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.AF_UNIX
import platform.posix.FD_CLOEXEC
import platform.posix.F_GETFD
import platform.posix.SOCK_STREAM
import platform.posix.errno
import platform.posix.fcntl
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class, InternalKmpTorApi::class)
class SocketUnixUnitTest {

    @Test
    fun givenSocket_whenOpened_thenHasCLOEXEC() {
        val fd = kmptor_socket(AF_UNIX, SOCK_STREAM, 0)
        if (fd == -1) throw errnoToIOException(errno)

        try {
            val stat = fcntl(fd, F_GETFD)
            if (stat == -1) throw errnoToIOException(errno)
            assertTrue((stat or FD_CLOEXEC) == stat)
        } finally {
            if (kmptor_socket_close(fd) == -1) throw errnoToIOException(errno)
        }
    }
}
