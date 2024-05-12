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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor

internal open class ObserverLogProcess internal constructor(): RuntimeEvent.Observer<String>(
    RuntimeEvent.LOG.PROCESS,
    null,
    OnEvent.Executor.Immediate,
    OnEvent.noOp(),
) {

    // Is registered via RealTorRuntime.factory
    internal val newNymInterceptor = TorCmdInterceptor.intercept<TorCmd.Signal.NewNym> { job, cmd ->
        job.invokeOnCompletion {
            if (job.isError) return@invokeOnCompletion
            // TODO: Listen for rate-limit notice
        }

        cmd
    }

    protected override fun notify(data: String) {
        val notice = data
            .substringAfter(NOTICE, "")
            .ifBlank { null } ?: return

        // TODO: parse data
    }

    private companion object {
        private const val NOTICE = " [notice] "
    }
}
