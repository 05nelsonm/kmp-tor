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
package io.matthewnelson.kmp.tor.controller.internal.controller

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.exceptions.ControllerShutdownException
import io.matthewnelson.kmp.tor.controller.common.exceptions.TorControllerException
import kotlin.coroutines.cancellation.CancellationException

@Suppress("SpellCheckingInspection")
internal interface ControlPortInteractor {
    val isConnected: Boolean

    @Throws(
        CancellationException::class,
        ControllerShutdownException::class,
        TorControllerException::class,
    )
    @OptIn(InternalTorApi::class)
    suspend fun processCommand(command: String): List<ReplyLine.SingleLine>

    @Throws(
        CancellationException::class,
        ControllerShutdownException::class,
        TorControllerException::class,
    )
    @OptIn(InternalTorApi::class)
    suspend fun <T: Any> processCommand(
        command: String,
        transform: List<ReplyLine.SingleLine>.() -> T
    ): T
}
