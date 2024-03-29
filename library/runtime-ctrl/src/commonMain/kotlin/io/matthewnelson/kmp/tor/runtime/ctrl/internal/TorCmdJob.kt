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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic

internal class TorCmdJob<Response: Any> private constructor(
    internal val cmd: TorCmd<Response>,
    onSuccess: OnSuccess<Response>,
    onFailure: OnFailure,
    handler: UncaughtException.Handler,
): QueuedJob(cmd.keyword.name, onFailure, handler) {

    @Volatile
    private var onSuccess: OnSuccess<Response>? = onSuccess

    protected override fun onCancellation(cause: CancellationException?) { onSuccess = null }

    @JvmSynthetic
    @Throws(IllegalStateException::class)
    internal fun executing() { onExecuting() }

    @JvmSynthetic
    internal fun error(cause: Throwable) {
        onError(cause, withLock = { onSuccess = null })
    }

    @JvmSynthetic
    internal fun completion(response: Response) {
        onCompletion(response, withLock = {
            val onSuccess = onSuccess
            this.onSuccess = null
            onSuccess
        })
    }

    internal companion object {

        @JvmSynthetic
        internal fun <Response: Any> of(
            cmd: TorCmd<Response>,
            onSuccess: OnSuccess<Response>,
            onFailure: OnFailure,
            handler: UncaughtException.Handler,
        ): TorCmdJob<Response> = TorCmdJob(cmd, onSuccess, onFailure, handler)
    }
}
