/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.controller.internal.io

import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import java.io.BufferedReader
import java.io.IOException
import java.net.Socket
import java.net.SocketException

@JvmInline
internal actual value class ReaderWrapper private actual constructor(private val value: Any) {

    private inline val asBufferedReader get() = value as BufferedReader

    @JvmSynthetic
    @Throws(TorControllerException::class)
    actual fun readLine(): String? {
        return try {
            asBufferedReader.readLine()
        } catch (e: IOException) {
            throw TorControllerException(e)
        }
    }

    companion object {
        @JvmSynthetic
        @Throws(IOException::class, SocketException::class)
        internal fun from(socket: Socket): ReaderWrapper =
            ReaderWrapper(socket.getInputStream().reader().buffered())
    }
}
