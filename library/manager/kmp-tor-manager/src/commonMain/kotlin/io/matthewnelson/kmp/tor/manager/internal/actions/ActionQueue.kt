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
import io.matthewnelson.kmp.tor.manager.internal.actions.ActionProcessor.Companion.INTERRUPT_MESSAGE
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.jvm.JvmSynthetic

internal sealed interface ActionQueue {
    @JvmSynthetic
    suspend fun add(holder: ActionHolder): Boolean
    @JvmSynthetic
    suspend fun clear(interruptingAction: Action)
    @JvmSynthetic
    suspend fun contains(holder: ActionHolder): Boolean
    @JvmSynthetic
    suspend fun contains(job: Job): Boolean
    @JvmSynthetic
    suspend fun contains(action: Action): Boolean
    @JvmSynthetic
    suspend fun isEmpty(): Boolean
    @JvmSynthetic
    suspend fun removeByAction(action: Action, cancelJobMessage: String): Boolean
    @JvmSynthetic
    suspend fun removeByHolder(holder: ActionHolder): Boolean
    @JvmSynthetic
    suspend fun <T> withQueueLock(block: suspend () -> T): T

    companion object {
        @JvmSynthetic
        internal fun newInstance(): ActionQueue =
            RealActionQueue()
    }
}

private class RealActionQueue: ActionQueue {
    private val queue: MutableSet<ActionHolder> = LinkedHashSet(5)
    private val lockQueueModify: Mutex = Mutex()
    private val lockQueue: Mutex = Mutex()

    override suspend fun add(holder: ActionHolder): Boolean {
        val added = lockQueueModify.withLock {
            queue.add(holder)
        }

        if (added) {
            holder.job.invokeOnCompletion { throwable ->
                if (lockQueueModify.tryLock()) {
                    queue.remove(holder)
                    lockQueueModify.unlock()
                } else {
                    @OptIn(DelicateCoroutinesApi::class)
                    GlobalScope.launch {
                        removeByHolder(holder)
                    }
                }

                if (throwable != null) {
                    throw throwable
                }
            }
        }

        return added
    }

    override suspend fun clear(interruptingAction: Action) {
        lockQueueModify.withLock {
            for (holder in queue) {
                holder.job.cancel("${holder.action} $INTERRUPT_MESSAGE${interruptingAction}")
            }
            queue.clear()
        }
    }

    override suspend fun contains(action: Action): Boolean {
        return lockQueueModify.withLock {
            queue.forEach { holder ->
                if (holder.action == action) {
                    return@withLock true
                }
            }
            false
        }
    }

    override suspend fun contains(holder: ActionHolder): Boolean {
        return lockQueueModify.withLock {
            queue.forEach {
                if (it == holder) {
                    return@withLock true
                }
            }
            false
        }
    }

    override suspend fun contains(job: Job): Boolean {
        return lockQueueModify.withLock {
            queue.forEach { holder ->
                if (holder.job == job) {
                    return@withLock true
                }
            }
            false
        }
    }

    override suspend fun isEmpty(): Boolean {
        return lockQueueModify.withLock {
            queue.isEmpty()
        }
    }

    override suspend fun removeByAction(action: Action, cancelJobMessage: String): Boolean {
        return lockQueueModify.withLock {
            val iterator = queue.iterator()
            var removedSomething = false
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (next.action == action) {
                    next.job.cancel(cancelJobMessage)
                    iterator.remove()
                    removedSomething = true
                }
            }
            removedSomething
        }
    }

    override suspend fun removeByHolder(holder: ActionHolder): Boolean {
        return lockQueueModify.withLock {
            queue.remove(holder)
        }
    }

    override suspend fun <T> withQueueLock(block: suspend () -> T): T {
        return lockQueue.withLock { block.invoke() }
    }
}
