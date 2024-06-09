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
@file:JvmName("EnqueuedJobUtil")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.internal.commonAwaitAsync
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helper for creating APIs that wrap an [EnqueuedJob] with
 * asynchronous execution functionality.
 *
 * **NOTE:** This is an internal API not meant for public consumption.
 *
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.awaitSync]
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.executeAsync]
 * @see [io.matthewnelson.kmp.tor.runtime.Action.Companion.executeAsync]
 * */
@InternalKmpTorApi
@Throws(Throwable::class)
@OptIn(ExperimentalContracts::class)
@Deprecated("Not meant for public use", level = DeprecationLevel.ERROR)
public actual suspend fun <Arg: EnqueuedJob.Argument, Success: Any> Arg.awaitAsync(
    enqueue: (arg: Arg, onFailure: OnFailure, onSuccess: OnSuccess<Success>) -> EnqueuedJob,
): Success {
    contract {
        callsInPlace(enqueue, InvocationKind.AT_MOST_ONCE)
    }

    return commonAwaitAsync(enqueue)
}

/**
 * Helper for creating APIs that wrap an [EnqueuedJob] with
 * synchronous execution functionality.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * **NOTE:** This is an internal API not meant for public consumption.
 *
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.awaitAsync]
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.executeSync]
 * @see [io.matthewnelson.kmp.tor.runtime.Action.Companion.executeSync]
 * */
@InternalKmpTorApi
@Throws(Throwable::class)
@OptIn(ExperimentalContracts::class)
@Deprecated("Not meant for public use", level = DeprecationLevel.ERROR)
public fun <Arg: EnqueuedJob.Argument, Success: Any> Arg.awaitSync(
    enqueue: (arg: Arg, onFailure: OnFailure, onSuccess: OnSuccess<Success>) -> EnqueuedJob,
    cancellation: (() -> CancellationException?)?,
): Success {
    contract {
        callsInPlace(enqueue, InvocationKind.EXACTLY_ONCE)
    }

    var failure: Throwable? = null
    var success: Success? = null

    return enqueue(
        this,
        OnFailure { f -> failure = f },
        OnSuccess { s -> success = s },
    ).awaitSync(
        success = { success },
        failure = { failure },
        cancellation = cancellation,
    )
}

@Throws(Throwable::class)
internal inline fun <Success: Any> EnqueuedJob.awaitSync(
    success: () -> Success?,
    failure: () -> Throwable?,
    noinline cancellation: (() -> CancellationException?)?,
): Success {
    var callback = cancellation

    val duration = 10.milliseconds

    while (isActive) {
        success()?.let { return it }
        failure()?.let { throw it }

        try {
            Blocking.threadSleep(duration)
        } catch (e: InterruptedException) {
            // Try to cancel
            val wasCancelled = cancel(
                cause = CancellationException("Interrupted", e),
                signalAttempt = true,
            )
            if (wasCancelled) break

            // non-cancellable state. continue.
        }

        // No possibility of cancellation. Skip.
        if (callback == null) continue
        if (!isActive) break

        val cause = try {
            callback() ?: continue
        } catch (t: Throwable) {
            if (t is CancellationException) {
                t
            } else {
                CancellationException("awaitSync.cancellation() threw exception", t)
            }
        }

        // de-reference callback as to not invoke it anymore
        // in the event that job is unable to be cancelled (e.g.
        // is in State.Executing), will just continue to loop
        // until execution completes.
        callback = null
        cancel(cause, signalAttempt = true)
    }

    if (isSuccess) {
        success()?.let { return it }
        throw IllegalStateException("$this completed successfully, but no response was recovered")
    }

    throw cancellationException()
        ?: failure()
        ?: IllegalStateException("$this completed exceptionally, but no cause was recovered")
}
