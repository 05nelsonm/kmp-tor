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
package io.matthewnelson.kmp.tor.controller.internal.ext

import io.matthewnelson.kmp.tor.common.address.ProxyAddress
import io.matthewnelson.kmp.tor.controller.TorController
import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import io.matthewnelson.kmp.tor.controller.internal.getTorControllerDispatcher
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

@Throws(TorControllerException::class)
@Suppress("nothing_to_inline", "BlockingMethodInNonBlockingContext")
internal suspend inline fun ProxyAddress.toTorController(): TorController {
    val dispatchers = getTorControllerDispatcher()
    val socket = Socket(Proxy.NO_PROXY)

    try {
        withContext(dispatchers) {
            val address = InetSocketAddress(address.value, port.value)

            socket.connect(address)
        }

        return socket.toTorController(dispatchers)
    } catch (e: Exception) {
        try {
            socket.close()
        } catch (_: Exception){}
        try {
            dispatchers.close()
        } catch (_: Exception) {}

        throw TorControllerException("Failed to open socket for $this", e)
    }
}
