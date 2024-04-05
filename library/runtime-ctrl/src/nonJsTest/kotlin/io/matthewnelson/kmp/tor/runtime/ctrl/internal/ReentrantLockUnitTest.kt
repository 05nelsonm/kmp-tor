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
@file:Suppress("UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.process.Blocking
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
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
                        delay(20.milliseconds)

                        withContext(Dispatchers.IO) {
                            Blocking.threadSleep(20.milliseconds)
                            throw IllegalStateException()
                        }
                    }
                } catch (_: IllegalStateException) {}
            }

            assertTrue(elapsed > 60.milliseconds)
        }

        // Will acquire lock first making the
        // launched coroutine wait for it.
        try {
            lock.withLockAsync {
                withContext(Dispatchers.IO) {
                    delay(50.milliseconds)
                }

                throw IllegalStateException()
            }
        } catch (_: IllegalStateException) {}

        job.join()
    }
}
