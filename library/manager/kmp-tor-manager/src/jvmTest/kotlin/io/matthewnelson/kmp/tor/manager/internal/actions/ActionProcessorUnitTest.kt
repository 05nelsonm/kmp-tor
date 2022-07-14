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

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Action.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class ActionProcessorUnitTest {

    private val processor: ActionProcessor =
        ActionProcessor()
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
    fun givenMultipleActionSubmissions_whenCleared_onlyJobsWaitingForExecutionAreCancelled() =
        runBlocking {
            var startCompleted = false
            var restartCompleted = false
            var commandCompleted = false
            var stopCompleted = false

            val handler = CoroutineExceptionHandler { _, _ ->  }

            val startJob = launch(handler) {
                processor.withProcessorLock(Start) {
                    delay(100L)
                    Result.success(true)
                }.getOrNull()?.let { startCompleted = it }
            }
            val restartJob = launch(handler) {
                processor.withProcessorLock(Restart) {
                    delay(100L)
                    Result.success(true)
                }.getOrNull()?.let { restartCompleted = it }
            }

            delay(25L)

            // queue should be cleared and cancelled, but executing
            // job remains untouched
            Assert.assertFalse(startJob.isCancelled)
            Assert.assertFalse((processor as ActionQueue).contains(startJob))
            Assert.assertTrue(processor.contains(restartJob))

            val commandJob = launch(handler) {
                processor.withProcessorLock(Controller) {
                    delay(100L)
                    Result.success(true)
                }.getOrNull()?.let { commandCompleted = it}
            }

            delay(25L)

            Assert.assertTrue(processor.contains(restartJob))
            Assert.assertTrue(processor.contains(commandJob))
            Assert.assertTrue(restartJob.isActive)
            Assert.assertTrue(commandJob.isActive)

            // submission of STOP should clear and cancel all other
            // Actions
            val stopJob = launch(handler) {
                processor.withProcessorLock(Stop) {
                    delay(100L)
                    Result.success(true)
                }.getOrNull()?.let { stopCompleted = it }
            }

            delay(25L)

            Assert.assertFalse(processor.contains(restartJob))
            Assert.assertFalse(processor.contains(commandJob))
            Assert.assertTrue(restartJob.isCancelled)
            Assert.assertTrue(commandJob.isCancelled)

            startJob.join()
            restartJob.join()
            commandJob.join()
            stopJob.join()

            Assert.assertTrue(processor.isEmpty())
            Assert.assertTrue(startCompleted)
            Assert.assertFalse(restartCompleted)
            Assert.assertFalse(commandCompleted)
            Assert.assertTrue(stopCompleted)
        }
}
