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

internal class TorCmdJob<Success: Any> private constructor(
    internal val cmd: TorCmd<Success>,
    onSuccess: OnSuccess<Success>,
    onFailure: OnFailure,
    handler: UncaughtException.Handler,
): QueuedJob(cmd.keyword, onFailure, handler) {

    @Volatile
    private var _onSuccess: OnSuccess<Success>? = onSuccess

    protected override fun onCancellation(cause: CancellationException?) { _onSuccess = null }

    @JvmSynthetic
    @Throws(IllegalStateException::class)
    internal fun executing() { onExecuting() }

    @JvmSynthetic
    internal fun error(cause: Throwable) {
        onError(cause, withLock = { _onSuccess = null })
    }

    @JvmSynthetic
    internal fun completion(response: Success) {
        onCompletion(response, withLock = {
            val onSuccess = _onSuccess
            _onSuccess = null
            onSuccess
        })
    }

    internal companion object {

        @JvmSynthetic
        internal fun <Success: Any> of(
            cmd: TorCmd<Success>,
            onSuccess: OnSuccess<Success>,
            onFailure: OnFailure,
            handler: UncaughtException.Handler,
        ): TorCmdJob<Success> = TorCmdJob(cmd, onSuccess, onFailure, handler)
    }
}
