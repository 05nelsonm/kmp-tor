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
package io.matthewnelson.kmp.tor.ext.unix.socket

import jnr.unixsocket.UnixSocket
import jnr.unixsocket.UnixSocketAddress
import jnr.unixsocket.UnixSocketChannel
import java.io.File
import java.io.IOException
import java.net.Socket

object UnixDomainSocketFactory {

    @JvmStatic
    @Throws(IOException::class)
    fun create(unixSocketPath: String): Socket {
        val socket = UnixSocket(UnixSocketChannel.open())

        try {
            val address = UnixSocketAddress(unixSocketPath)
            socket.connect(address)
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {}
            throw e
        }

        return socket
    }
}
