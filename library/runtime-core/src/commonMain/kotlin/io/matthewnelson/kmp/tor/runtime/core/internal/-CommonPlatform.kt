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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import kotlinx.coroutines.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException

internal expect val UnixSocketsNotSupportedMessage: String?

internal expect inline val AFUnixSunPathSize: Int

internal expect inline val IsUnixLikeHost: Boolean

internal expect inline val IsAndroidHost: Boolean

internal expect inline val IsDarwinMobile: Boolean

@InternalKmpTorApi
@Throws(Throwable::class)
@OptIn(ExperimentalContracts::class)
internal suspend inline fun <Arg: EnqueuedJob.Argument, Success: Any> Arg.commonAwaitAsync(
    enqueue: (arg: Arg, onFailure: OnFailure, onSuccess: OnSuccess<Success>) -> EnqueuedJob,
): Success {
    contract {
        callsInPlace(enqueue, InvocationKind.AT_MOST_ONCE)
    }

    val cancellable = currentCoroutineContext()[Job].let { parent ->
        parent?.ensureActive()
        Job(parent)
    }

    var failure: Throwable? = null
    var success: Success? = null

    val job = enqueue(
        this,
        OnFailure { f -> failure = f },
        OnSuccess { s -> success = s },
    )

    job.invokeOnCompletion { cancellable.complete() }

    try {
        cancellable.join()
    } catch (e: CancellationException) {
        // Try cancelling EnqueuedJob
        job.cancel(e, signalAttempt = true)
    }

    // EnqueuedJob.cancel was unsuccessful. Wait for completion.
    if (job.isActive) {
        val nonCancellable = Job()
        job.invokeOnCompletion { nonCancellable.complete() }
        withContext(NonCancellable) { nonCancellable.join() }
    }

    if (job.isSuccess) {
        success?.let { return it }
        throw IllegalStateException("$job completed successfully, but no response was recovered")
    }

    throw job.cancellationException()
        ?: failure
        ?: IllegalStateException("$job completed exceptionally, but no cause was recovered")
}
