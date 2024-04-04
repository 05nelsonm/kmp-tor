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

import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@OptIn(ExperimentalCoroutinesApi::class, InternalKmpTorApi::class)
class ReentrantLockUnitTest {

    @Test
    fun givenWithLockAsync_whenBlockThrows_thenUnlocks() = runTest {
        val lock = ReentrantLock()

        val dispatcher = newFixedThreadPoolContext(1, "Test.BG")
        currentCoroutineContext().job.invokeOnCompletion { dispatcher.close() }

        val job = launch(dispatcher) {
            val elapsed = measureTime {
                try {
                    lock.withLockAsync {
                        delay(10.milliseconds)

                        withContext(Dispatchers.IO) {
                            Blocking.threadSleep(10.milliseconds)
                            throw IllegalStateException()
                        }
                    }
                } catch (_: IllegalStateException) {}
            }

            assertTrue(elapsed > 15.milliseconds)

            lock.withLockAsync {
                delay(20.milliseconds)

                // Lock released even on cancellation
                throw CancellationException()
            }
        }

        try {
            lock.withLockAsync {
                withContext(Dispatchers.IO) {
                    delay(25.milliseconds)
                }

                throw IllegalStateException()
            }
        } catch (_: IllegalStateException) {}

        withContext(Dispatchers.IO) { delay(1.milliseconds) }

        val elapsed1 = measureTime { lock.withLock {} }
        assertTrue(elapsed1 > 15.milliseconds)

        withContext(Dispatchers.IO) { delay(1.milliseconds) }

        val elapsed2 = measureTime { lock.withLock {} }
        assertTrue(elapsed2 > 10.milliseconds)
        assertTrue(job.isCancelled)
    }
}
