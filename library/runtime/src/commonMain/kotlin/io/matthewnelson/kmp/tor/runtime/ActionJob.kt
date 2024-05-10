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
import io.matthewnelson.kmp.tor.runtime.ctrl.TempTorCmdQueue
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

public abstract class ActionJob private constructor(
    @JvmField
    public val action: Action,
    onFailure: OnFailure,
    handler: UncaughtException.Handler,
): EnqueuedJob(action.name, onFailure, handler) {

    @get:JvmName("isStart")
    public val isStart: Boolean get() = this is StartJob
    @get:JvmName("isStop")
    public val isStop: Boolean get() = this is StopJob
    @get:JvmName("isRestart")
    public val isRestart: Boolean get() = this is RestartJob

    internal class StartJob internal constructor(
        onSuccess: OnSuccess<Unit>,
        onFailure: OnFailure,
        handler: UncaughtException.Handler,
        private val immediateExecute: Boolean = false,
    ): Sealed(Action.StartDaemon, onSuccess, onFailure, handler) {

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal override fun executing() {
            // When RealTorRuntime goes to pop it off the stack, this
            // will prevent it from throwing IllegalStateException such
            // that it can be executed && completed (for startService).
            if (immediateExecute) return

            super.executing()
        }

        init {
            // non-cancellable
            if (immediateExecute) onExecuting()
        }
    }

    internal class StopJob internal constructor(
        onSuccess: OnSuccess<Unit>,
        onFailure: OnFailure,
        handler: UncaughtException.Handler,
    ): Sealed(Action.StopDaemon, onSuccess, onFailure, handler)

    internal class RestartJob internal constructor(
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
        @Volatile
        private var _onErrorCause: Throwable? = null
        @Volatile
        private var _oldCmdQueue: TempTorCmdQueue? = null
        @get:JvmSynthetic
        internal val onErrorCause: Throwable? get() = _onErrorCause
        @get:JvmSynthetic
        internal val oldCmdQueue: TempTorCmdQueue? get() = _oldCmdQueue

        protected final override fun onCancellation(cause: CancellationException?) { _onSuccess = null }

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal open fun executing() { onExecuting() }

        @JvmSynthetic
        @Throws(IllegalArgumentException::class, IllegalStateException::class)
        internal fun attachOldQueue(queue: TempTorCmdQueue) {
            require(this !is StartJob) { "An old queue cannot be attached to $this" }
            check(state == State.Executing) { "Job state must be ${State.Executing}" }
            check(_oldCmdQueue == null) { "A queue is already attached" }

            _oldCmdQueue = queue

            // ensure it is destroyed no matter what
            invokeOnCompletion {
                _oldCmdQueue = null
                queue.connection?.destroy()
                queue.destroy()
            }
        }

        @JvmSynthetic
        internal fun error(cause: Throwable): Boolean {
            return onError(cause, withLock = {
                _onErrorCause = cause
                _onSuccess = null
            })
        }

        @JvmSynthetic
        internal fun completion(): Boolean {
            return onCompletion(Unit, withLock = {
                val onSuccess = _onSuccess
                _onSuccess = null
                onSuccess
            })
        }
    }
}
