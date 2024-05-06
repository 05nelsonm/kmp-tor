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
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic
import kotlin.reflect.KClass

/**
 * A wrapper around the jobs returned by [TorCmd.Unprivileged.Processor] and
 * [TorCmd.Privileged.Processor] to inform observers that they are being
 * executed.
 *
 * The job is non-cancelling, created just prior to when the actual [TorCmd]
 * contents are to be written to the control connection.
 *
 * This is useful for seeing what is happening and, if needed, attaching
 * [invokeOnCompletion] handlers for reactionary purposes.
 *
 * @see [RuntimeEvent.EXECUTE.CMD]
 * @param [cmd] The [TorCmd] class being executed.
 * */
public class TorCmdJob private constructor(
    @JvmField
    public val cmd: KClass<out TorCmd<*>>,
    private val delegate: QueuedJob,
): QueuedJob(delegate.name, OnFailure.noOp(), UncaughtException.Handler.THROW) {

    init {
        onExecuting()

        delegate.invokeOnCompletion { cancellation ->
            if (delegate.isError) {
                onError(cancellation ?: EXCEPTION, null)
                return@invokeOnCompletion
            }

            onCompletion(null, null)
        }
    }

    public override fun equals(other: Any?): Boolean = other is TorCmdJob && other.delegate == delegate
    public override fun hashCode(): Int = delegate.hashCode()

    internal companion object {

        @JvmSynthetic
        internal fun interceptor(
            notify: (job: TorCmdJob) -> Unit,
        ): TorCmdInterceptor<TorCmd<*>> = TorCmdInterceptor.intercept<TorCmd<*>> { job, cmd ->
            notify(TorCmdJob(cmd::class, job))
            cmd
        }

        private val EXCEPTION = Exception()
    }
}
