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

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

@Suppress("NOTHING_TO_INLINE")
internal suspend inline fun <Response: Any> TorCmd.Privileged.Processor.commonExecuteAsync(
    cmd: TorCmd.Privileged<Response>,
): Response = suspendCancellableCoroutine { continuation ->
    var job: QueuedJob? = null

    try {
        job = enqueue(
            cmd = cmd,
            onFailure = { t ->
                continuation.resumeWithException(t)
            },
            onSuccess = { result ->
                @OptIn(ExperimentalCoroutinesApi::class)
                continuation.resume(result, onCancellation = { t -> job?.cancel(t) })
            },
        )
    } catch (e: IllegalStateException) {
        continuation.resumeWithException(e)
    }

    continuation.invokeOnCancellation { t -> job?.cancel(t) }
}

@Suppress("NOTHING_TO_INLINE")
internal suspend inline fun <Response: Any> TorCmd.Unprivileged.Processor.commonExecuteAsync(
    cmd: TorCmd.Unprivileged<Response>,
): Response = suspendCancellableCoroutine { continuation ->
    var job: QueuedJob? = null

    try {
        job = enqueue(
            cmd = cmd,
            onFailure = { t ->
                continuation.resumeWithException(t)
            },
            onSuccess = { result ->
                @OptIn(ExperimentalCoroutinesApi::class)
                continuation.resume(result, onCancellation = { t -> job?.cancel(t) })
            },
        )
    } catch (e: IllegalStateException) {
        continuation.resumeWithException(e)
    }

    continuation.invokeOnCancellation { t -> job?.cancel(t) }
}