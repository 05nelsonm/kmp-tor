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
package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.TorCmdJob
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Intercept the currently executing [QueuedJob] and its associated
 * [TorCmd] in order to apply [QueuedJob.invokeOnCompletion] handles,
 * or modify the command's arguments.
 *
 * If no modifications are needed for the [TorCmd], the originating
 * [TorCmd] should be returned.
 *
 * e.g.
 *
 *     TorCmdInterceptor.intercept<TorCmd.SetEvents> { job, cmd ->
 *         job.invokeOnCompletion {
 *             if (job.isError) return@invokeOnCompletion
 *             // do something...
 *         }
 *
 *         if (cmd.events.contains(TorEvent.NOTICE)) {
 *             // Do not replace the command
 *             cmd
 *         } else {
 *             // This is what will end up being executed
 *             TorCmd.SetEvents(cmd.events + TorEvent.NOTICE)
 *         }
 *     }
 *
 * @see [intercept]
 * */
public class TorCmdInterceptor<C: TorCmd<*>> private constructor(
    private val _intercept: (job: TorCmdJob<*>) -> C?,
) {

    @JvmSynthetic
    internal fun invoke(job: TorCmdJob<*>): TorCmd<*>? {
        if (job.state != QueuedJob.State.Executing) return null

        val result = _intercept(job) ?: return null
        if (result == job.cmd) return null
        if (result::class != job.cmd::class) return null

        return result
    }

    public companion object {

        /**
         * Creates a [TorCmdInterceptor] for the specified [TorCmd].
         *
         * [intercept] **should not** throw exception. This will result
         * in the executing job completing exceptionally.
         * */
        @JvmStatic
        public inline fun <reified C: TorCmd<*>> intercept(
            crossinline intercept: (job: QueuedJob, cmd: C) -> C
        ): TorCmdInterceptor<C> = of { job ->
            if (job.cmd !is C) return@of null
            intercept(job, job.cmd)
        }

        @JvmSynthetic
        @PublishedApi
        internal fun <C: TorCmd<*>> of(
            intercept: (job: TorCmdJob<*>) -> C?,
        ): TorCmdInterceptor<C> = TorCmdInterceptor(intercept)
    }
}

private fun ll(e: Set<TorEvent>) {
    (e + TorEvent.NOTICE).let {

    }
}
