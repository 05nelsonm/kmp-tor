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
@file:JvmName("JobUtil")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helper for building higher order, functional,
 * synchronous APIs.
 *
 * In the event that the caller thread is interrupted,
 * [EnqueuedJob.cancel] will be attempted. If successful,
 * the [CancellationException] will be thrown. If
 * unsuccessful, the [InterruptedException] is ignored
 * as the [EnqueuedJob] is in a non-cancellable state.
 *
 * [success] should **NOT** throw exception, and
 * should return the result of [OnSuccess].
 *
 * [failure] should **NOT** throw exception, and
 * should return the result of [OnFailure].
 *
 * [cancellation] callback (if non-null) is available
 * to check things between while loop execution. A return
 * value of null indicates "do not cancel". A return value of
 * [CancellationException] will pass it to [EnqueuedJob.cancel].
 * If unable to cancel the [EnqueuedJob] because it is in a
 * non-cancellable state, no further invocations of
 * [cancellation] will be had.
 *
 * @example [executeSync]
 * */
@InternalKmpTorApi
@Throws(Throwable::class)
public fun <Response: Any> EnqueuedJob.awaitSync(
    success: () -> Response?,
    failure: () -> Throwable?,
    cancellation: (() -> CancellationException?)?,
): Response {
    var callback = cancellation

    while (isActive) {
        success()?.let { return it }
        failure()?.let { throw it }

        try {
            Blocking.threadSleep(10.milliseconds)
        } catch (e: InterruptedException) {
            // Try to cancel
            if (cancel(CancellationException("Interrupted", e))) break

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
                CancellationException("awaitSync.cancellation threw exception", t)
            }
        }

        // de-reference callback as to not invoke it anymore
        // in the event that job is unable to be cancelled (e.g.
        // is in State.Executing), will just continue to loop
        // until execution completes.
        callback = null
        cancel(cause)
    }

    if (state == EnqueuedJob.State.Success) {
        success()?.let { return it }
        throw IllegalStateException("$this completed successfully, but no response was recovered by awaitSync.success()")
    }

    throw cancellationException
        ?: failure()
        ?: IllegalStateException("$this completed exceptionally, but no cause was recovered by awaitSync.failure()")
}
