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
package io.matthewnelson.kmp.tor.runtime.util

import io.matthewnelson.kmp.tor.runtime.Action

/**
 * Enqueues the [action], suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [startDaemonAsync]
 * @see [stopDaemonAsync]
 * @see [restartDaemonAsync]
 * @see [io.matthewnelson.kmp.tor.runtime.util.executeSync]
 * */
@Throws(Throwable::class)
public expect suspend fun <T: Action.Processor> T.executeAsync(
    action: Action,
): T

/**
 * Starts the tor daemon, suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [Action.StartDaemon]
 * @see [io.matthewnelson.kmp.tor.runtime.util.startDaemonSync]
 * */
@Throws(Throwable::class)
public expect suspend inline fun <T: Action.Processor> T.startDaemonAsync(): T

/**
 * Stops the tor daemon, suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [Action.StopDaemon]
 * @see [io.matthewnelson.kmp.tor.runtime.util.stopDaemonSync]
 * */
@Throws(Throwable::class)
public expect suspend inline fun <T: Action.Processor> T.stopDaemonAsync(): T

/**
 * Stops and then starts the tor daemon, suspending the
 * current coroutine until completion or cancellation/error.
 *
 * @see [Action.RestartDaemon]
 * @see [io.matthewnelson.kmp.tor.runtime.util.restartDaemonSync]
 * */
@Throws(Throwable::class)
public expect suspend inline fun <T: Action.Processor> T.restartDaemonAsync(): T
