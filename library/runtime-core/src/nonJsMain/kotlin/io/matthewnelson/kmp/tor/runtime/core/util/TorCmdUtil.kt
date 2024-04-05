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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.internal.commonExecuteAsync
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Enqueues the [cmd], suspending the current coroutine until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Privileged.Processor]
 * @see [awaitSync]
 * */
@Throws(Throwable::class)
public actual suspend fun <Success: Any> TorCmd.Privileged.Processor.executeAsync(
    cmd: TorCmd.Privileged<Success>,
): Success = commonExecuteAsync(cmd)

/**
 * Enqueues the [cmd], suspending the current coroutine until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Unprivileged.Processor]
 * @see [awaitSync]
 * */
@Throws(Throwable::class)
public actual suspend fun <Success: Any> TorCmd.Unprivileged.Processor.executeAsync(
    cmd: TorCmd.Unprivileged<Success>,
): Success = commonExecuteAsync(cmd)

/**
 * Enqueues the [cmd], blocking the current thread until completion
 * or cancellation/error.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @see [TorCmd.Privileged.Processor]
 * @see [awaitSync]
 * @see [executeAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
public fun <Success: Any> TorCmd.Privileged.Processor.executeSync(
    cmd: TorCmd.Privileged<Success>,
    cancellation: (() -> CancellationException?)? = null,
): Success {
    var failure: Throwable? = null
    var success: Success? = null

    @OptIn(InternalKmpTorApi::class)
    return enqueue(
        cmd = cmd,
        onFailure = { failure = it },
        onSuccess = { success = it },
    ).awaitSync(
        success = { success },
        failure = { failure },
        cancellation = cancellation,
    )
}

/**
 * Enqueues the [cmd], blocking the current thread until completion
 * or cancellation/error.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @see [TorCmd.Unprivileged.Processor]
 * @see [awaitSync]
 * @see [executeAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
public fun <Success: Any> TorCmd.Unprivileged.Processor.executeSync(
    cmd: TorCmd.Unprivileged<Success>,
    cancellation: (() -> CancellationException?)? = null,
): Success {
    var fail: Throwable? = null
    var success: Success? = null

    @OptIn(InternalKmpTorApi::class)
    return enqueue(
        cmd = cmd,
        onFailure = { fail = it },
        onSuccess = { success = it },
    ).awaitSync(
        success = { success },
        failure = { fail },
        cancellation = cancellation,
    )
}
