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
@file:Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.tor.runtime.util

import io.matthewnelson.kmp.tor.runtime.RuntimeAction
import io.matthewnelson.kmp.tor.runtime.internal.commonExecuteAsync

/**
 * Enqueues the [action], suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [startDaemonAsync]
 * @see [stopDaemonAsync]
 * @see [restartDaemonAsync]
 * */
//@Throws(Throwable::class)
public actual suspend fun <T: RuntimeAction.Processor> T.executeAsync(
    action: RuntimeAction,
): T = commonExecuteAsync(action)

/**
 * Starts the tor daemon, suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [RuntimeAction.StartDaemon]
 * */
//@Throws(Throwable::class)
public actual suspend inline fun <T: RuntimeAction.Processor> T.startDaemonAsync(): T =
    executeAsync(RuntimeAction.StartDaemon)

/**
 * Stops the tor daemon, suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [RuntimeAction.StopDaemon]
 * */
//@Throws(Throwable::class)
public actual suspend inline fun <T: RuntimeAction.Processor> T.stopDaemonAsync(): T =
    executeAsync(RuntimeAction.StopDaemon)

/**
 * Stops and then starts the tor daemon, suspending the
 * current coroutine until completion or cancellation/error.
 *
 * @see [RuntimeAction.RestartDaemon]
 * */
//@Throws(Throwable::class)
public actual suspend inline fun <T: RuntimeAction.Processor> T.restartDaemonAsync(): T =
    executeAsync(RuntimeAction.RestartDaemon)