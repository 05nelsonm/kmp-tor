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
package io.matthewnelson.kmp.tor.controller.internal.controller

import io.matthewnelson.kmp.tor.controller.common.exceptions.ControllerShutdownException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmSynthetic

internal class TorControlProcessorLock(
    private val dispatcher: CoroutineDispatcher,
    private val isConnected: () -> Boolean
) {

    private val lock = Mutex()

    @JvmSynthetic
    internal suspend fun <T: Any?> withContextAndLock(block: suspend () -> Result<T>): Result<T> {
        if (!isConnected.invoke()) {
            return Result.failure(ControllerShutdownException("Tor has stopped and a new connection is required"))
        }

        return lock.withLock {
            try {
                withContext(dispatcher) {
                    try {
                        block.invoke()
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }
            } catch (e: Exception) {
                Result.failure(ControllerShutdownException("Tor has stopped and a new connection is required", e))
            }
        }
    }
}
