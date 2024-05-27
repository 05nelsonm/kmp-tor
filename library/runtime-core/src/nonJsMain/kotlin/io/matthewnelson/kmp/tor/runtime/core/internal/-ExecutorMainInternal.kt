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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal actual object ExecutorMainInternal: OnEvent.Executor {

    private val SCOPE by lazy {
        val main = run {
            // Will throw if Missing
            Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext)

            try {
                Dispatchers.Main.immediate
            } catch (_: UnsupportedOperationException) {
                Dispatchers.Main
            }
        }

        CoroutineScope(context =
            CoroutineName("OnEvent.Executor.Main")
            + SupervisorJob()
            + main
        )
    }

    @InternalKmpTorApi
    actual override fun execute(handler: CoroutineContext, executable: Executable) {
        SCOPE.launch(handler) { executable.execute() }
    }
}
