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

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Action.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class ActionQueueUnitTest {

    private val lock = Mutex()
    private var lockCount = 0
    private val queue = ActionQueue.newInstance() // RealActionQueue
    private var dispatcher: CoroutineDispatcher? = null

    @Before
    fun before() {
        dispatcher = Executors
            .newSingleThreadExecutor()
            .asCoroutineDispatcher()
            .also { Dispatchers.setMain(it) }
    }

    @After
    fun after() {
        (dispatcher as ExecutorCoroutineDispatcher).close()
        dispatcher = null
        Dispatchers.resetMain()
    }

    @Test
    fun givenAction_whenJobCompletes_itIsRemovedFromQueueAutomatically() =
        runBlocking {
            val actions = listOf(
                Start,
                Stop,
                Restart,
                Stop
            )

            val holders = loadUpQueue(this, actions, delayTime = null)
            for (holder in holders) {
                assertTrue(holder.job.isCompleted)
            }

            Assert.assertTrue(queue.isEmpty())
        }

    @Test
    fun givenAction_whenRemovedByAction_cancelsJob() =
        runBlocking {
            val actions = listOf(
                Stop,
                Start,
                Restart,
                Stop,
                Start,
                Restart
            )

            val holders = loadUpQueue(this, actions, delayTime = 1_000)
            queue.removeByAction(Restart, "Interrupted by $Restart")

            for ((index, action) in actions.withIndex()) {
                val holder = holders[index]
                if (action == Restart) {
                    Assert.assertTrue(holder.job.isCancelled)
                    Assert.assertFalse(queue.contains(holder))
                } else {
                    Assert.assertTrue(holder.job.isActive)
                    Assert.assertTrue(queue.contains(holder))
                }
            }

            for (holder in holders) {
                holder.job.join()
            }
        }

    @Test
    fun givenActionHoldingLock_whenAllOtherActionsClearedAndCancelled_lockHolderFinishes() =
        runBlocking {
            val actions = listOf(
                Stop,
                Start,
                Restart,
                Stop,
                Start,
                Restart
            )

            val holders = loadUpQueue(this, actions, delayTime = 200, useLock = true)
            Assert.assertTrue(lock.holdsLock(holders.first().job))
            queue.removeByHolder(holders.first())
            queue.clear(interruptingAction = Controller)
            Assert.assertTrue(holders.first().job.isActive)
            Assert.assertTrue(lockCount == holders.size)

            for (i in 1 until holders.size) {
                Assert.assertFalse(lock.holdsLock(holders[i].job))
            }
            Assert.assertTrue(lock.holdsLock(holders.first().job))

            for (holder in holders) {
                holder.job.join()
            }
        }

    private suspend fun withTestLock(block: suspend () -> Unit) {
        lockCount++
        lock.withLock(currentCoroutineContext().job) { block.invoke() }
    }

    private suspend fun loadUpQueue(
        newScope: CoroutineScope,
        actions: List<TorManagerEvent.Action>,
        delayTime: Long? = null,
        useLock: Boolean = false
    ): List<ActionHolder> {
        delayTime?.let {
            if (it < 100L) {
                fail("delayTime cannot be less than 100L")
            }
        }

        val handler = CoroutineExceptionHandler { _, _ -> }

        val list: MutableList<ActionHolder> = ArrayList(actions.size)

        for (action in actions) {
            newScope.launch(handler) {
                queue.add(ActionHolder(action, currentCoroutineContext().job))
                delayTime?.let {
                    if (useLock) {
                        withTestLock {
                            delay(delayTime)
                        }
                    } else {
                        delay(delayTime)
                    }
                }
            }.let { job ->
               list.add(ActionHolder(action, job))
            }
        }

        delay(50L)

        return list
    }
}
