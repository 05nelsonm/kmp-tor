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
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.ensureStoppedOnTestCompletion
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class TorRuntimeUnitTest {

    @Test
    fun givenStartedState_whenFailure_thenStopExecuted() = runTest {
        val lockJob = SynchronizedObject()
        val executeJobs = mutableListOf<ActionJob>()

        val runtime = TorRuntime.Builder(testEnv("rt_start_fail")) {
            observerStatic(RuntimeEvent.EXECUTE.ACTION) { job ->
                synchronized(lockJob) { executeJobs.add(job) }
            }

//            observerStatic(RuntimeEvent.LIFECYCLE) { println(it) }
//            observerStatic(RuntimeEvent.LISTENERS) { println(it) }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.LOG.INFO) { println(it) }
//            observerStatic(RuntimeEvent.LOG.WARN) { println(it) }
//            observerStatic(RuntimeEvent.LOG.PROCESS) { println(it) }
//            observerStatic(RuntimeEvent.STATE) { println(it) }

            config { environment ->
                environment.torrcFile.writeUtf8("""
                    # Startup fail simulation
                    DNSPort -1
                """.trimIndent())
            }
        }.ensureStoppedOnTestCompletion()

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

            suspend fun EnqueuedJob.await() {
                val latch = Job(currentCoroutineContext().job)
                invokeOnCompletion { latch.complete() }
                latch.join()
            }

            job.await()
            failureJob!!.await()

            // Slight delay for StopJob to do its thing
            withContext(Dispatchers.Default) { delay(150.milliseconds) }

            synchronized(lockJob) {
                assertEquals(3, executeJobs.size)
                assertEquals(job, executeJobs.first())
                assertEquals(2, invocationFailure)
                assertEquals(1, executeJobs.count { it is ActionJob.StopJob })
                executeJobs.clear()
            }

            // Ensure that cmd queue is unavailable
            try {
                runtime.executeAsync(TorCmd.Signal.NewNym)
                fail()
            } catch (e: IllegalStateException) {
                assertEquals("Tor is stopped or stopping", e.message)
            }
        }
    }
}
