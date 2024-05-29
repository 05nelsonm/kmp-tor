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
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.EXECUTE.CMD.observeNewNym
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.ensureStoppedOnTestCompletion
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
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
//            observerStatic(RuntimeEvent.PROCESS.STDOUT) { println(it) }
            observerStatic(RuntimeEvent.PROCESS.STDERR) { println(it) }
//            observerStatic(RuntimeEvent.STATE) { println(it) }

            config { environment ->
                environment.torrcFile.writeUtf8("""
                    # Startup fail simulation
                    DNSPort -1
                """.trimIndent())
            }
        }.ensureStoppedOnTestCompletion()

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

    @Test
    fun givenStartedAction_whenCancelledOrInterrupted_thenStops() = runTest {
        val runtime = TorRuntime.Builder(testEnv("rt_interrupt")) {
//            observerStatic(RuntimeEvent.EXECUTE.ACTION) { println(it) }
//            observerStatic(RuntimeEvent.EXECUTE.CMD) { println(it) }
//            observerStatic(RuntimeEvent.LIFECYCLE) { println(it) }
//            observerStatic(RuntimeEvent.LISTENERS) { println(it) }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.LOG.INFO) { println(it) }
//            observerStatic(RuntimeEvent.LOG.WARN) { println(it) }
//            observerStatic(RuntimeEvent.PROCESS.STDOUT) { println(it) }
            observerStatic(RuntimeEvent.PROCESS.STDERR) { println(it) }
//            observerStatic(RuntimeEvent.STATE) { println(it) }
        }

        currentCoroutineContext().job.invokeOnCompletion { runtime.clearObservers() }
        runtime.ensureStoppedOnTestCompletion()

        var cancellableJob: Job? = null
        var interruptJob: EnqueuedJob? = null

        suspend fun assertInterrupted() {
            assertFailsWith<InterruptedException> { runtime.startDaemonAsync() }

            val j = interruptJob!!
            val latch = Job(currentCoroutineContext().job)
            j.invokeOnCompletion { latch.complete() }
            latch.join()

            assertTrue(runtime.state().isOff)
        }

        suspend fun assertCancellable() {
            val j = Job(currentCoroutineContext().job)
            cancellableJob = j
            assertFailsWith<CancellationException> {
                withContext(j) {
                    runtime.startDaemonAsync()
                }
            }
        }

        run {
            val observerACTION = RuntimeEvent.EXECUTE.ACTION.observer { job ->
                if (job.isStop) return@observer
                cancellableJob?.let {
                    if (it.isActive) {
                        it.cancel()
                        return@observer
                    }
                }

                interruptJob = runtime.enqueue(Action.StopDaemon, {}, {})
            }
            runtime.subscribe(observerACTION)

            assertInterrupted()
            assertCancellable()

            runtime.unsubscribe(observerACTION)
        }

        run {
            var eventClassName: String? = null
            var eventName: Lifecycle.Event.Name? = null
            val observerLCE = RuntimeEvent.LIFECYCLE.observer { event ->
                if (event.name != eventName) return@observer
                if (event.className != eventClassName) return@observer
                cancellableJob?.let {
                    if (it.isActive) {
                        it.cancel()
                        return@observer
                    }
                }
                interruptJob = runtime.enqueue(Action.StopDaemon, {}, {})
            }
            runtime.subscribe(observerLCE)

            listOf(
                "TorDaemon" to Lifecycle.Event.Name.OnStart,
                "RealTorCtrl" to Lifecycle.Event.Name.OnCreate,
            ).forEach { (clazz, event) ->
                eventClassName = clazz
                eventName = event

                assertInterrupted()
                assertCancellable()
            }
            runtime.unsubscribe(observerLCE)
        }

        run {
            var cmdClass: KClass<out TorCmd<*>>? = null
            val observerCMD = RuntimeEvent.EXECUTE.CMD.observer { job ->
                if (job.cmd != cmdClass) return@observer
                cancellableJob?.let {
                    if (it.isActive) {
                        it.cancel()
                        return@observer
                    }
                }
                interruptJob = runtime.enqueue(Action.StopDaemon, {}, {})
            }
            runtime.subscribe(observerCMD)

            listOf(
                TorCmd.Authenticate::class,
                TorCmd.Ownership.Take::class,
                TorCmd.Config.Load::class,
                TorCmd.SetEvents::class,
                TorCmd.Config.Reset::class,
            ).forEach { cmd ->
                cmdClass = cmd

                assertInterrupted()
                assertCancellable()
            }
            runtime.unsubscribe(observerCMD)
        }
    }

    @Test
    fun givenRuntime_whenExecuteCmdObserver_thenWorksAsExpected() = runTest {
        val runtime = TorRuntime.Builder(testEnv("rt_cmd_observer")) {}

        currentCoroutineContext().job.invokeOnCompletion { runtime.clearObservers() }
        runtime.ensureStoppedOnTestCompletion()

        val notices = mutableListOf<String?>()
        runtime.observeNewNym(null, null) { limited ->
            notices.add(limited)
        }

        runtime.startDaemonAsync()
        runtime.executeAsync(TorCmd.Signal.NewNym)
        runtime.executeAsync(TorCmd.Signal.NewNym)
        runtime.executeAsync(TorCmd.Signal.NewNym)
        runtime.executeAsync(TorCmd.Signal.NewNym)

        assertEquals(4, notices.size)
        assertNull(notices[0]) // SUCCESS
        assertNotNull(notices[1]) // Rate limiting
        assertNotNull(notices[2]) // Rate limiting
        assertNotNull(notices[3]) // Rate limiting
    }
}
