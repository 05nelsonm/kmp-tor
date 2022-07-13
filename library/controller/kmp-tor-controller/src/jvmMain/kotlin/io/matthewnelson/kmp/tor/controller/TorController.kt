/*
 * Copyright (c) 2021 Matthew Nelson
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
@file:JvmName("jvmTorController")

package io.matthewnelson.kmp.tor.controller

import io.matthewnelson.kmp.tor.common.address.ProxyAddress
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.controller.common.internal.isUnixPath
import io.matthewnelson.kmp.tor.controller.internal.controller.RealTorController
import io.matthewnelson.kmp.tor.controller.internal.controller.getTorControllerDispatcher
import io.matthewnelson.kmp.tor.controller.internal.util.toTorController
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.net.SocketException

/**
 * Connects to Tor via it's control port in order to facilitate
 * asynchronous communication.
 *
 * Upon connecting, [TorController] will run continuously until
 * Tor has been shutdown or [disconnect] has been called.
 *
 * @see [newInstance]
 * @see [RealTorController]
 * @see [TorControlProcessor]
 * @see [RealTorControlProcessor]
 * */
actual interface TorController: TorControlProcessor, TorEventProcessor<TorEvent.SealedListener> {
    actual val isConnected: Boolean

    actual fun disconnect()

    /**
     * Callback will be made upon disconnect, and directly afterwards
     * the reference to the callback cleared.
     *
     * @see [RealTorController.RealControlPortInteractor]'s init block
     * */
    @ExperimentalTorApi
    actual fun onDisconnect(action: ((TorController) -> Unit)?)

    actual companion object {
        /**
         * Creates a [TorController] from the provided [Socket]. Socket must
         * already be connected.
         *
         * @throws [IOException] if an input/output stream cannot be retrieved
         * @throws [SocketException] if an input/output stream cannot be retrieved
         * */
        @JvmStatic
        @Throws(IOException::class, SocketException::class)
        fun newInstance(socket: Socket): TorController = socket.toTorController()

        /**
         * Opens a TCP connection to Tor's control port at the given [ProxyAddress]
         * */
        @Throws(TorControllerException::class)
        actual suspend fun newInstance(address: ProxyAddress): TorController = address.toTorController()

        /**
         * Opens a unix domain socket to Tor's control port at the give [Path]
         * */
        @Throws(TorControllerException::class)
        actual suspend fun newInstance(unixDomainSocket: Path): TorController {
            @OptIn(InternalTorApi::class)
            if (!unixDomainSocket.isUnixPath) {
                throw TorControllerException("Unix domain socket path must start with '/'")
            }

            @OptIn(InternalTorApi::class)
            if (!PlatformUtil.hasControlUnixDomainSocketSupport) {
                throw TorControllerException("UnixDomainSockets unsupported")
            }

            @OptIn(InternalTorApi::class)
            val clazz: Class<*> = try {
                Class.forName(PlatformUtil.UNIX_DOMAIN_SOCKET_FACTORY_CLASS)
                    ?: throw ClassNotFoundException(PlatformUtil.UNIX_DOMAIN_SOCKET_FACTORY_CLASS)
            } catch (e: ClassNotFoundException) {
                throw TorControllerException("UnixDomainSockets unsupported. Add the kmp-tor-ext-unix-socket dependency", e)
            }

            val dispatchers = getTorControllerDispatcher()

            return try {
                withContext(dispatchers) {
                    clazz
                        .getMethod("create", String::class.java)
                        .invoke(null, unixDomainSocket.value) as Socket
                }.toTorController(dispatchers)
            } catch (e: Exception) {
                try {
                    dispatchers.close()
                } catch (_: Exception) {}

                throw TorControllerException("Failed to open unix socket to $unixDomainSocket", e)
            }
        }
    }
}
