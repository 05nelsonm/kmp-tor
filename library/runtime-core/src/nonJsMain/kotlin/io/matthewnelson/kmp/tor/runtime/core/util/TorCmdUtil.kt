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

import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.internal.commonExecuteAsync
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration.Companion.milliseconds

/**
 * Enqueues the [cmd], suspending the current coroutine until completion
 * or cancellation/error.
 *
 * @see [TorCmd.Privileged.Processor]
 * @see [executeSync]
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
 * @see [executeSync]
 * */
@Throws(Throwable::class)
public actual suspend fun <Response: Any> TorCmd.Unprivileged.Processor.executeAsync(
    cmd: TorCmd.Unprivileged<Response>,
): Response = commonExecuteAsync(cmd)

/**
 * Enqueues the [cmd], blocking the current thread until completion
 * or cancellation/error.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @see [TorCmd.Privileged.Processor]
 * @see [executeAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
public fun <Response: Any> TorCmd.Privileged.Processor.executeSync(
    cmd: TorCmd.Privileged<Response>,
    cancellation: (() -> CancellationException?)? = null,
): Response {
    var failure: Throwable? = null
    var success: Response? = null

    return enqueue(
        cmd = cmd,
        onFailure = { failure = it },
        onSuccess = { success = it },
    ).executeSync(
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
 * @see [executeAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
public fun <Response: Any> TorCmd.Unprivileged.Processor.executeSync(
    cmd: TorCmd.Unprivileged<Response>,
    cancellation: (() -> CancellationException?)? = null,
): Response {
    var fail: Throwable? = null
    var success: Response? = null

    return enqueue(
        cmd = cmd,
        onFailure = { fail = it },
        onSuccess = { success = it },
    ).executeSync(
        success = { success },
        failure = { fail },
        cancellation = cancellation,
    )
}

private inline fun <Response: Any> QueuedJob.executeSync(
    success: () -> Response?,
    failure: () -> Throwable?,
    noinline cancellation: (() -> CancellationException?)?,
): Response {
    var callback = cancellation

    while (true) {
        success()?.let { return it }
        failure()?.let { throw it }

        Blocking.threadSleep(25.milliseconds)

        // No possibility of cancellation. Skip.
        if (callback == null) continue

        val exception = try {
            callback() ?: continue
        } catch (t: Throwable) {
            if (t is CancellationException) t else CancellationException(t)
        }

        // de-reference callback as to not invoke it anymore
        // in the event that job is unable to be cancelled (e.g.
        // is in State.Executing), will just continue to loop
        // until execution completes.
        callback = null
        cancel(exception)
    }
}
