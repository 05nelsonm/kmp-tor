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

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import io.matthewnelson.kmp.tor.runtime.Action.Companion.restartDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime.Companion.TIMEOUT_START_SERVICE
import io.matthewnelson.kmp.tor.runtime.test.TestServiceFactory
import io.matthewnelson.kmp.tor.runtime.test.assertLCEsContain
import io.matthewnelson.kmp.tor.runtime.test.assertLCEsDoNotContain
import io.matthewnelson.kmp.tor.runtime.test.runTorTest
import kotlinx.coroutines.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@OptIn(ExperimentalKmpTorApi::class, InternalKmpTorApi::class)
class ServiceFactoryUnitTest {

    @Test
    fun givenInitializer_whenUsedMoreThanOnce_thenThrowsException() = runTorTest { runtime ->
        assertFailsWith<IllegalStateException> { TestServiceFactory(runtime.initializer) }
    }

    @Test
    fun givenBinder_whenBindAndOtherInstanceIsNotDestroyed_thenDestroysPriorInstance() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        val warnings = mutableListOf<String>()
        val lock = synchronizedObject()
        val observer = RuntimeEvent.LOG.WARN.observer { synchronized(lock) { warnings.add(it) } }
        runtime.subscribe(observer)

        val destroyable = runtime.testBinder.onBind(emptySet(), null, emptySet(), emptySet())
        runtime.unsubscribe(observer)
        destroyable.destroy()

        val warning = synchronized(lock) {
            warnings.filter {
                it.contains("onBind was called before previous instance was destroyed")
            }
        }
        assertEquals(1, warning.size)
    }

    @Test
    fun givenNoStartJob_whenOnBind_thenEnqueuesStartJob() = runTorTest { runtime ->
        val executes = mutableListOf<ActionJob>()
        val lock = synchronizedObject()
        val observer = RuntimeEvent.EXECUTE.ACTION.observer { synchronized(lock) { executes.add(it) } }

        runtime.subscribe(observer)

        runtime.testBinder.onBind(emptySet(), null, emptySet(), emptySet())
        val start = TimeSource.Monotonic.markNow()

        while (executes.isEmpty()) {
            withContext(Dispatchers.Default) { delay(100.milliseconds) }

            if (start.elapsedNow() > 2_000.milliseconds) {
                break
            }
        }

        runtime.unsubscribe(observer)

        synchronized(lock) {
            assertEquals(1, executes.size)
            assertIs<ActionJob.StartJob>(executes.first())
        }
    }

    @Test
    fun givenNotStarted_whenTorCmd_thenThrowsException() = runTorTest { runtime ->
        assertIs<TorState.Daemon.Off>(runtime.state().daemon)
        assertFailsWith<IllegalStateException> { runtime.executeAsync(TorCmd.Signal.Dump) }
    }

    @Test
    fun givenAwaitingStart_whenTorCmd_thenAddsToServiceFactoryCtrlQueue() = runTorTest { runtime ->
        // Simulate delayed startup (service launching)
        runtime.serviceStart = { binder ->
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Default) {
                delay(50.milliseconds)
                binder.onBind(emptySet(), null, emptySet(), emptySet())
            }
        }

        val lces = mutableListOf<Lifecycle.Event>()
        val lock = synchronizedObject()
        runtime.subscribe(RuntimeEvent.LIFECYCLE.observer { event -> synchronized(lock) { lces.add(event) } })

        val actionJob = runtime.enqueue(Action.StartDaemon, {}, {}) as ActionJob.StartJob
        assertEquals(EnqueuedJob.State.Executing, actionJob.state)
        synchronized(lock) { lces.assertLCEsDoNotContain("RealTorRuntime", Lifecycle.Event.Name.OnCreate) }

        val cmdJob = runtime.enqueue(TorCmd.Signal.Dump, { assertIs<CancellationException>(it) }, {},)
        val stopJob = runtime.enqueue(Action.StopDaemon, {}, {})
        assertTrue(cmdJob.cancel(null))

        val latch = Job(currentCoroutineContext().job)
        stopJob.invokeOnCompletion { latch.complete() }
        latch.join()

        assertIs<InterruptedException>(actionJob.onErrorCause)

        synchronized(lock) {
            lces.assertLCEsContain("RealTorRuntime", Lifecycle.Event.Name.OnCreate)
            lces.assertLCEsContain("RealTorRuntime", Lifecycle.Event.Name.OnDestroy)
        }
    }

    @Test
    fun givenBindTimeout_whenManyJobs_thenAllAreCompletedAsExpected() = runTorTest { runtime ->
        runtime.serviceStart = { /* no-op */ }

        // All job failure occurrences should be of type InterruptedException
        val onFailure = OnFailure { assertIs<InterruptedException>(it) }

        var invocationSuccess = 0

        val jobs = listOf(
            runtime.enqueue(Action.StartDaemon, onFailure) { invocationSuccess++ },
            runtime.enqueue(Action.StopDaemon, onFailure) { invocationSuccess++ },
            runtime.enqueue(Action.RestartDaemon, onFailure) { invocationSuccess++ },
            runtime.enqueue(Action.StartDaemon, onFailure) { invocationSuccess++ },
            runtime.enqueue(TorCmd.Signal.Dump, onFailure) { invocationSuccess++ },
            runtime.enqueue(TorCmd.Signal.NewNym, onFailure) { invocationSuccess++ },
            runtime.enqueue(TorCmd.Signal.Heartbeat, onFailure) { invocationSuccess++ },
        )

        withContext(Dispatchers.Default) { delay(TIMEOUT_START_SERVICE) }

        for (job in jobs) {
            var i = 0
            while (job.isActive && i++ < 10) {
                withContext(Dispatchers.Default) { delay(50.milliseconds) }
            }

            val expected = if (job is ActionJob.StopJob) {
                EnqueuedJob.State.Success
            } else {
                EnqueuedJob.State.Error
            }

            assertEquals(expected, job.state)
        }

        // ActionStop should be the only successful one
        assertEquals(1, invocationSuccess)

        // The temporary cmd queue should have been destroyed and reset to null
        assertFailsWith<IllegalStateException> { runtime.executeAsync(TorCmd.Signal.Dump) }
    }

    @Test
    fun givenActionStop_whenAlreadyStopped_thenIsImmediateSuccess() = runTorTest { runtime ->
        assertIs<TorState.Daemon.Off>(runtime.state().daemon)
        val job = runtime.enqueue(Action.StopDaemon, {}, {})
        assertEquals(EnqueuedJob.State.Success, job.state)
    }

    @Test
    fun givenActionRestart_whenAlreadyStarted_thenIsNotDestroyed() = runTorTest { runtime ->
        val lces = mutableListOf<Lifecycle.Event>()
        val lock = synchronizedObject()

        val observer = RuntimeEvent.LIFECYCLE.observer { synchronized(lock) { lces.add(it) } }
        runtime.subscribe(observer)

        runtime.startDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        synchronized(lock) {
            lces.assertLCEsContain("RealTorRuntime", Lifecycle.Event.Name.OnCreate, fid = runtime)
            lces.assertLCEsContain("DestroyableTorRuntime", Lifecycle.Event.Name.OnCreate, fid = runtime)
            lces.assertLCEsContain("TorDaemon", Lifecycle.Event.Name.OnCreate, fid = runtime)
            lces.assertLCEsContain("TorDaemon", Lifecycle.Event.Name.OnStart, fid = runtime)
            lces.assertLCEsContain("RealTorCtrl", Lifecycle.Event.Name.OnCreate, fid = runtime)

            lces.assertLCEsDoNotContain("TorDaemon", Lifecycle.Event.Name.OnDestroy, fid = runtime)
            lces.assertLCEsDoNotContain("RealTorCtrl", Lifecycle.Event.Name.OnDestroy, fid = runtime)
            lces.clear()
        }

        // Should already be started, so should do nothing
        runtime.startDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        synchronized(lock) { assertTrue(lces.isEmpty()) }

        runtime.restartDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        synchronized(lock) {
            // Old process stopped
            lces.assertLCEsContain("TorDaemon", Lifecycle.Event.Name.OnDestroy, fid = runtime)
            lces.assertLCEsContain("RealTorCtrl", Lifecycle.Event.Name.OnDestroy, fid = runtime)

            // New process started
            lces.assertLCEsContain("TorDaemon", Lifecycle.Event.Name.OnCreate, fid = runtime)
            lces.assertLCEsContain("TorDaemon", Lifecycle.Event.Name.OnStart, fid = runtime)
            lces.assertLCEsContain("RealTorCtrl", Lifecycle.Event.Name.OnCreate, fid = runtime)

            lces.assertLCEsDoNotContain("RealTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = runtime)
            lces.assertLCEsDoNotContain("DestroyableTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = runtime)
            lces.clear()
        }

        runtime.stopDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        runtime.unsubscribe(observer)
        synchronized(lock) {
            lces.assertLCEsContain("RealTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = runtime)
            lces.assertLCEsContain("DestroyableTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = runtime)
        }
    }
}
