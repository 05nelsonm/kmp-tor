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
        val lock = reentrantLock()

        var started = 0
        val jobs = Array(5) {
            // Occupy first 5 threads available from Dispatchers.IO
            launch(Dispatchers.IO) {
                lock.withLock { started++ }
                while (true) {
                    Blocking.threadSleep(5.milliseconds)
                    if (!isActive) break
                }
            }
        }

        while (true) {
            if (lock.withLock { started } == jobs.size) break
            withContext(Dispatchers.IO) { delay(5.milliseconds) }
        }

        val dispatcher = newFixedThreadPoolContext(1, "Test.BG")

        currentCoroutineContext().job.apply {
            invokeOnCompletion { dispatcher.close() }
            invokeOnCompletion { jobs.forEach { it.cancel() } }
        }

        val job = launch(dispatcher) {

            delay(2.milliseconds)

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

        jobs.forEach { it.cancelAndJoin() }
        job.join()
    }
}
