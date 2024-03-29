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
@file:Suppress("UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.tor.controller.internal.ext

import io.matthewnelson.kmp.tor.controller.TorController
import io.matthewnelson.kmp.tor.controller.internal.controller.RealTorController
import io.matthewnelson.kmp.tor.controller.internal.getTorControllerDispatcher
import io.matthewnelson.kmp.tor.controller.internal.io.ReaderWrapper
import io.matthewnelson.kmp.tor.controller.internal.io.SocketWrapper
import io.matthewnelson.kmp.tor.controller.internal.io.WriterWrapper
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.IOException
import java.net.Socket
import java.net.SocketException

@Suppress("nothing_to_inline")
@Throws(IOException::class, SocketException::class)
internal inline fun Socket.toTorController(
    dispatcher: ExecutorCoroutineDispatcher? = null
): TorController {
    try {
        if (!isConnected) {
            throw IOException("Socket.connect must be called before retrieving a new instance of TorController")
        }

        val readerWrapper = ReaderWrapper.wrap(getInputStream().reader().buffered())
        val writerWrapper = WriterWrapper.wrap(getOutputStream().writer())
        val socketWrapper = SocketWrapper.wrap(this)

        @OptIn(ExperimentalCoroutinesApi::class)
        return RealTorController(
            reader = readerWrapper,
            writer = writerWrapper,
            socket = socketWrapper,
            dispatcher = dispatcher ?: getTorControllerDispatcher(),
        )
    } catch (e: Exception) {
        try {
            close()
        } catch (_: Exception) {}
        try {
            dispatcher?.close()
        } catch (_: Exception) {}
        throw e
    }
}
