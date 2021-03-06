/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager.internal.actions

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Action
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Action.*
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ActionProcessor private constructor(
    processorLock: Mutex?,
    delegate: ActionQueue
): ActionQueue by delegate {

    companion object {
        const val INTERRUPT_MESSAGE = "Interrupted by "

        internal operator fun invoke(processorLock: Mutex? = null): ActionProcessor {
            return ActionProcessor(processorLock, ActionQueue.newInstance())
        }
    }

    private val lockProcessor: Mutex = processorLock ?: Mutex()

    internal suspend fun <T> withProcessorLock(
        action: Action, block: suspend () -> Result<T>
    ): Result<T> {
        val holder = ActionHolder(action, currentCoroutineContext().job)

        val ex: TorManagerException? = withQueueLock {
            if (contains(holder.job)) {
                return@withQueueLock TorManagerException(
                    "Lock acquisition attempted with an already existing Coroutine Job"
                )
            }

            when (action) {
                is Start,
                is Stop,
                is Restart -> { clear(action) }
                is Controller -> { /* no-op */ }
            }

            add(holder)
            null
        }

        if (ex != null) {
            return Result.failure(ex)
        }

        return lockProcessor.withLock {
            removeByHolder(holder)
            block.invoke()
        }
    }
}
