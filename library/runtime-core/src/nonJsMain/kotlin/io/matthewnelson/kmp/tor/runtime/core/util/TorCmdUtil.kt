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
@file:JvmName("TorCmdUtil")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.internal.commonExecuteAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmName

/**
 * Enqueues the [cmd], suspending the current coroutine until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Privileged.Processor]
 * */
@Throws(Throwable::class)
public actual suspend fun <Response: Any> TorCmd.Privileged.Processor.executeAsync(
    cmd: TorCmd.Privileged<Response>,
): Response = commonExecuteAsync(cmd)

/**
 * Enqueues the [cmd], suspending the current coroutine until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Unprivileged.Processor]
 * */
@Throws(Throwable::class)
public actual suspend fun <Response: Any> TorCmd.Unprivileged.Processor.executeAsync(
    cmd: TorCmd.Unprivileged<Response>,
): Response = commonExecuteAsync(cmd)

/**
 * Enqueues the [cmd], blocking the current thread until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Privileged.Processor]
 * */
@Throws(Throwable::class)
public fun <Response: Any> TorCmd.Privileged.Processor.execute(
    cmd: TorCmd.Privileged<Response>,
): Response = execute(Dispatchers.IO, cmd)

/**
 * Enqueues the [cmd], blocking the current thread until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Privileged.Processor]
 * */
@Throws(Throwable::class)
public fun <Response: Any> TorCmd.Privileged.Processor.execute(
    context: CoroutineContext,
    cmd: TorCmd.Privileged<Response>,
): Response = runBlocking(context) { executeAsync(cmd) }

/**
 * Enqueues the [cmd], blocking the current thread until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Unprivileged.Processor]
 * */
@Throws(Throwable::class)
public fun <Response: Any> TorCmd.Unprivileged.Processor.execute(
    cmd: TorCmd.Unprivileged<Response>,
): Response = execute(Dispatchers.IO, cmd)

/**
 * Enqueues the [cmd], blocking the current thread until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Unprivileged.Processor]
 * */
@Throws(Throwable::class)
public fun <Response: Any> TorCmd.Unprivileged.Processor.execute(
    context: CoroutineContext,
    cmd: TorCmd.Unprivileged<Response>,
): Response = runBlocking(context) { executeAsync(cmd) }
