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
@file:JvmName("QueuedJobUtil")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.time.Duration.Companion.milliseconds

@InternalKmpTorApi
@Throws(InterruptedException::class, Throwable::class)
public inline fun <Response: Any> QueuedJob.awaitSync(
    success: () -> Response?,
    failure: () -> Throwable?,
    noinline cancellation: (() -> CancellationException?)?,
): Response {
    var callback = cancellation

    while (isActive) {
        success()?.let { return it }
        failure()?.let { throw it }

        Blocking.threadSleep(10.milliseconds)

        // No possibility of cancellation. Skip.
        if (callback == null) continue

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

    success()?.let { return it }

    throw cancellationException
        ?: failure()
        ?: IllegalStateException("$this completed, but no cause was recovered by awaitSync.failure")
}
