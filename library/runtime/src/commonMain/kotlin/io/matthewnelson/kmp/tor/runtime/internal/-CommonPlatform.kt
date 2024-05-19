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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun TorRuntime.Environment.newRuntimeDispatcher(): CoroutineDispatcher

@Throws(Throwable::class)
internal expect fun File.setDirectoryPermissions()

@Throws(Throwable::class)
internal expect fun File.setFilePermissions()

@Throws(Throwable::class)
@Suppress("NOTHING_TO_INLINE")
internal suspend inline fun <T: Action.Processor> T.commonExecuteAsync(
    action: Action,
): T = suspendCancellableCoroutine { continuation ->
    val job = enqueue(
        action = action,
        onFailure = { t ->
            continuation.resumeWithException(t)
        },
        onSuccess = {
            continuation.resume(this)
        }
    )

    continuation.invokeOnCancellation { t ->
        val e = if (t is CancellationException) t else CancellationException(t)
        job.cancel(e)
    }
}



// No matter the Delay implementation (Coroutines Test library)
// Will delay the specified duration using a TimeSource.
internal suspend fun timedDelay(duration: Duration) {
    if (duration <= 0.milliseconds) return

    val startMark = TimeSource.Monotonic.markNow()
    var remainder = duration
    while (remainder > 1.milliseconds) {
        delay(remainder)
        remainder = duration - startMark.elapsedNow()
    }
}
