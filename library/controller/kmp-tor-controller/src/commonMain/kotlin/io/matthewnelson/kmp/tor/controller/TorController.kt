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
package io.matthewnelson.kmp.tor.controller

import io.matthewnelson.kmp.tor.common.address.ProxyAddress
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.internal.controller.*

/**
 * Connects to Tor via it's control port in order to facilitate
 * asynchronous communication.
 *
 * Upon connecting, [TorController] will run continuously until
 * Tor has been shutdown or [disconnect] has been called.
 *
 * @see [RealTorController]
 * @see [TorControlProcessor]
 * @see [RealTorControlProcessor]
 * */
expect interface TorController: TorControlProcessor, TorEventProcessor<TorEvent.SealedListener> {
    val isConnected: Boolean

    fun disconnect()

    /**
     * Callback will be made upon disconnect, and directly afterwards
     * the reference to the callback cleared.
     *
     * @see [RealTorController.RealControlPortInteractor]'s init block
     * */
    @ExperimentalTorApi
    fun onDisconnect(action: ((TorController) -> Unit)?)

    companion object {

        /**
         * Opens a TCP connection to Tor's control port at the given [ProxyAddress]
         * */
        @Throws(TorControllerException::class)
        suspend fun newInstance(address: ProxyAddress): TorController

        /**
         * Opens a unix domain socket to Tor's control port at the give [Path]
         * */
        @Throws(TorControllerException::class)
        suspend fun newInstance(unixDomainSocket: Path): TorController
    }
}
