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

import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun TorRuntime.Environment.newRuntimeDispatcher(): CoroutineDispatcher

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
