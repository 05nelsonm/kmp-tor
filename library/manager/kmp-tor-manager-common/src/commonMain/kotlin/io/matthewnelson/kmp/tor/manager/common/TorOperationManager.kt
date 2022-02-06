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
package io.matthewnelson.kmp.tor.manager.common

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent

/**
 * Base interface for [start], [stop], and [restart] operations.
 * */
interface TorOperationManager {
    /**
     * Starts Tor if it is stopped. Does nothing otherwise.
     * */
    suspend fun start(): Result<Any?>

    /**
     * Will start Tor using TorManager's coroutine scope.
     *
     * Error response is directed to the [TorManagerEvent.SealedListener] via
     * [TorManagerEvent.Error]
     * */
    fun startQuietly()

    /**
     * Stops, and then starts Tor again.
     * */
    suspend fun restart(): Result<Any?>

    /**
     * Will restart Tor using TorManager's coroutine scope.
     *
     * Error response is directed to the [TorManagerEvent.SealedListener] via
     * [TorManagerEvent.Error]
     * */
    fun restartQuietly()

    /**
     * Stops Tor if it is not stopped.
     * */
    suspend fun stop(): Result<Any?>

    /**
     * Will stop Tor using TorManager's coroutine scope.
     *
     * Error response is directed to the [TorManagerEvent.SealedListener] via
     * [TorManagerEvent.Error]
     * */
    fun stopQuietly()
}
