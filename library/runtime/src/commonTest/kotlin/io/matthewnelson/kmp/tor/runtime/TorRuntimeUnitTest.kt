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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.EXECUTE.CMD.observeSignalNewNym
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.runTorTest
import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class TorRuntimeUnitTest {

    @Test
    @Suppress("RedundantSamConstructor")
    fun givenStartedState_whenFailure_thenStopExecuted() = runTorTest(
        config = ConfigCallback { throw IOException() }
    ) { runtime ->
        val executes = mutableListOf<ActionJob>()
        val lock = SynchronizedObject()
        val observer = RuntimeEvent.EXECUTE.ACTION.observer { job ->
            synchronized(lock) { executes.add(job) }
        }
        runtime.subscribe(observer)

        suspend fun EnqueuedJob.await() {
            val latch = Job(currentCoroutineContext().job)
            invokeOnCompletion { latch.complete() }
            latch.join()
        }

        listOf(
            Action.StartDaemon,
            Action.RestartDaemon,
        ).forEach { action ->
            var invocationFailure = 0

            var failureJob: EnqueuedJob? = null

            val job = runtime.enqueue(
                action,
                onFailure = { t ->
                    invocationFailure++
                    assertIs<IOException>(t)

                    failureJob = runtime.enqueue(
                        action,
                        onFailure = { t2 ->
                            invocationFailure++
                            assertIs<IOException>(t2)

                            // Enqueue a new job when this one fails so that the
                            // stack is not empty when processStack is called.
                            //
                            // This is immediately cancelled here, but will ensure that
                            // if no executable actions are on the stack, and the last
                            // startup job failed, it will execute StopJob to clean up.
                            val stackJob = runtime.enqueue(action, {}, {})
                            stackJob.cancel(CancellationException())
                            assertTrue(stackJob.isCancelled)
                        },
                        onSuccess = { fail("tor should have failed to start...") },
                    )
                },
                onSuccess = { fail("tor should have failed to start...") },
            )

            job.await()
            failureJob!!.await()

            // Slight delay for StopJob to do its thing
            withContext(Dispatchers.Default) { delay(150.milliseconds) }

            synchronized(lock) {
                assertEquals(3, executes.size)
                assertEquals(job, executes.first())
                assertEquals(2, invocationFailure)
                assertEquals(1, executes.count { it is ActionJob.StopJob })
                executes.clear()
            }

            // Ensure that cmd queue is unavailable
            try {
                runtime.executeAsync(TorCmd.Signal.NewNym)
                fail()
            } catch (e: IllegalStateException) {
                assertEquals("Tor is not started", e.message)
            }
        }
    }

    @Test
    fun givenRuntime_whenObserveSignalNewNym_thenWorksAsExpected() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        val notices = mutableListOf<String?>()
        val lock = SynchronizedObject()
        val disposable = runtime.observeSignalNewNym(null, null) { limited ->
            synchronized(lock) { notices.add(limited) }
        }

        repeat(4) { runtime.executeAsync(TorCmd.Signal.NewNym) }

        runtime.stopDaemonAsync()

        disposable.dispose()

        assertEquals(4, notices.size)

        // Was rate-limited at least 1 time
        //
        // tor "might" not rate-limit on the 2nd call, so.
        assertTrue(notices.count { it != null } >= 1)
    }
}
