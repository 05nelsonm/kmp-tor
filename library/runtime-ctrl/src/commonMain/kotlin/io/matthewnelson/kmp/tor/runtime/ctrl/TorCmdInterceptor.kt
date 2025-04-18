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
@file:Suppress("unused")

package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.TorCmdJob
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Intercept the currently executing [EnqueuedJob] and its associated
 * [TorCmd] in order to apply [EnqueuedJob.invokeOnCompletion] handles,
 * or modify the command's arguments.
 *
 * If no modifications are needed for the [TorCmd], the originating
 * [TorCmd] should be returned.
 *
 * **NOTE:** The following [TorCmd] **cannot** be replaced:
 *  - [TorCmd.Onion.Add]
 *  - [TorCmd.Onion.Delete]
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
public class TorCmdInterceptor<C: TorCmd<*>> @PublishedApi internal constructor(
    private val _intercept: (job: EnqueuedJob, cmd: TorCmd<*>) -> TorCmd<*>?,
) {

    @JvmSynthetic
    internal fun invoke(job: TorCmdJob<*>): TorCmd<*>? {
        if (job.state != EnqueuedJob.State.Executing) return null

        val result = _intercept(job, job.cmd) ?: return null

        if (job.cmd is TorCmd.Onion.Add) return null
        if (job.cmd is TorCmd.Onion.Delete) return null

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
            crossinline intercept: (job: EnqueuedJob, cmd: C) -> C,
        ): TorCmdInterceptor<C> = TorCmdInterceptor { job, cmd ->
            if (cmd !is C) null else intercept(job, cmd)
        }
    }
}
