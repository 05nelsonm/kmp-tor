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
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.ReadBuffer
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import kotlinx.cinterop.*
import platform.posix.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalForeignApi::class, InternalKmpTorApi::class, UnsafeNumber::class)
internal class NativeCtrlConnection internal constructor(
    private val descriptor: Int
): CtrlConnection {

    @Volatile
    private var _isClosed: Boolean = false
    @Volatile
    private var _isReading: Boolean = false
    private val lock = SynchronizedObject()

    @OptIn(InternalProcessApi::class)
    @Throws(CancellationException::class, IllegalStateException::class)
    override suspend fun startRead(parser: CtrlConnection.Parser) {
        synchronized(lock) {
            check(!_isClosed) { "Connection is closed" }
            check(!_isReading) { "Already reading input" }
            _isReading = true
        }

        val feed = ReadBuffer.lineOutputFeed(parser::parse)
        val buf = ReadBuffer.allocate()

        var interrupted = 0
        while (true) {
            val read = buf.buf.usePinned { pinned ->
                read(
                    descriptor,
                    pinned.addressOf(0),
                    buf.buf.size.convert(),
                ).toInt()
            }

            if (read == -1 && errno == EINTR && !_isClosed && interrupted++ < 3) continue
            if (read <= 0) break

            interrupted = 0
            feed.onData(buf, read)
        }

        buf.buf.fill(0)
        feed.close()
    }

    @Throws(CancellationException::class, IOException::class)
    override suspend fun write(command: ByteArray) {
        if (_isClosed) throw IOException("Connection is closed")
        if (command.isEmpty()) return

        synchronized(lock) {
            if (_isClosed) throw IOException("Connection is closed")

            command.usePinned { pinned ->
                var written = 0
                var interrupted = 0
                while (written < command.size) {
                    val write = write(
                        descriptor,
                        pinned.addressOf(written),
                        (command.size - written).convert(),
                    ).toInt()

                    if (write == 0) break
                    if (write == -1) {
                        val errno = errno
                        if (errno == EINTR && interrupted++ < 3) continue
                        throw errnoToIOException(errno)
                    }

                    written += write
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (_isClosed) return

        synchronized(lock) {
            if (_isClosed) return
            _isClosed = true
            shutdown(descriptor, SHUT_RDWR)
            close(descriptor)
        }
    }
}
