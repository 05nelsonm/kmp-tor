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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.tor.runtime.core.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

public abstract class ActionJob private constructor(
    @JvmField
    public val action: Action,
    onFailure: OnFailure,
    handler: UncaughtException.Handler,
): QueuedJob(action.name, onFailure, handler) {

    internal abstract class Start internal constructor(
        onSuccess: OnSuccess<Unit>,
        onFailure: OnFailure,
        handler: UncaughtException.Handler,
    ): Sealed(Action.StartDaemon, onSuccess, onFailure, handler)

    internal abstract class Stop internal constructor(
        onSuccess: OnSuccess<Unit>,
        onFailure: OnFailure,
        handler: UncaughtException.Handler,
    ): Sealed(Action.StopDaemon, onSuccess, onFailure, handler)

    internal abstract class Restart internal constructor(
        onSuccess: OnSuccess<Unit>,
        onFailure: OnFailure,
        handler: UncaughtException.Handler,
    ): Sealed(Action.RestartDaemon, onSuccess, onFailure, handler)

    internal sealed class Sealed(
        action: Action,
        onSuccess: OnSuccess<Unit>,
        onFailure: OnFailure,
        handler: UncaughtException.Handler,
    ): ActionJob(action, onFailure, handler) {

        @Volatile
        private var _onSuccess: OnSuccess<Unit>? = onSuccess

        protected override fun onCancellation(cause: CancellationException?) { _onSuccess = null }

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun executing() { onExecuting() }

        @JvmSynthetic
        internal open fun error(cause: Throwable): Boolean {
            return onError(cause, withLock = { _onSuccess = null })
        }

        @JvmSynthetic
        internal open fun completion(): Boolean {
            return onCompletion(Unit, withLock = {
                val onSuccess = _onSuccess
                _onSuccess = null
                onSuccess
            })
        }
    }
}
