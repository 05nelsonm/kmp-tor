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

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Action.Companion.restartDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.assertContains
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.assertDoesNotContain
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.ensureStoppedOnTestCompletion
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKmpTorApi::class, InternalKmpTorApi::class)
class ServiceFactoryUnitTest {

    private class TestFactory(
        val initializer: Initializer,
        private val start: ((Binder) -> Unit)? = null,
    ): TorRuntime.ServiceFactory(initializer) {

        val testBinder get() = binder

        override fun startService() {
            if (start == null) {
                binder.onBind(
                    emptySet(),
                    null,
                    emptySet(),
                    emptySet(),
                )
            } else {
                start.invoke(binder)
            }
        }
    }

    private fun env(
        dirName: String,
        start: ((TorRuntime.ServiceFactory.Binder) -> Unit)? = null,
        block: ThisBlock<TorRuntime.Environment.Builder> = ThisBlock {},
    ): TorRuntime.Environment = testEnv(dirName) {
        serviceFactoryLoader = object : TorRuntime.ServiceFactory.Loader() {
            override fun loadProtected(initializer: TorRuntime.ServiceFactory.Initializer): TorRuntime.ServiceFactory {
                return TestFactory(initializer, start)
            }
        }

        apply(block)
    }

    @Test
    fun givenEnvironment_whenServiceFactoryLoader_thenCreatesServiceFactory() = runTest {
        val lces = mutableListOf<Lifecycle.Event>()
        val runtime = TorRuntime.Builder(env("sf_create")) {
            observer(RuntimeEvent.LIFECYCLE.observer { lces.add(it) })
        }.ensureStoppedOnTestCompletion()

        assertIs<TestFactory>(runtime)
        assertEquals(2, lces.size)
        lces.assertContains("RealServiceFactoryCtrl", Lifecycle.Event.Name.OnCreate, fid = runtime)
        lces.assertContains(TestFactory::class.simpleName!!, Lifecycle.Event.Name.OnCreate, fid = runtime)
    }

    @Test
    fun givenInitializer_whenUsedMoreThanOnce_thenThrowsException() {
        val factory = TorRuntime.Builder(env("sf_is_instance")) {} as TestFactory
        assertFailsWith<IllegalStateException> {
            TestFactory(factory.initializer)
        }
    }

    @Test
    fun givenBinder_whenBindAndOtherInstanceIsNotDestroyed_thenDestroysPriorInstance() = runTest {
        val factory = TorRuntime.Builder(env("sf_multi_bind_destroy")) {}
            .ensureStoppedOnTestCompletion() as TestFactory

        val warnings = mutableListOf<String>()
        factory.subscribe(RuntimeEvent.LOG.WARN.observer { warnings.add(it) })
        val runtime1 = factory.testBinder.bind().ensureStoppedOnTestCompletion()
        factory.testBinder.bind().ensureStoppedOnTestCompletion()
        assertTrue(runtime1.isDestroyed())

        val warning = warnings.filter {
            it.contains("onBind was called before previous instance was destroyed")
        }
        assertEquals(1, warning.size)
    }

    @Test
    fun givenNoStart_whenBindWithoutEnqueue_thenAutoEnqueuesStartJob() = runTest {
        val factory = TorRuntime.Builder(env("sf_enqueue_start")) {}
            .ensureStoppedOnTestCompletion() as TestFactory

        val executes = mutableListOf<ActionJob>()
        factory.subscribe(RuntimeEvent.EXECUTE.ACTION.observer { executes.add(it) })
        factory.testBinder.bind().ensureStoppedOnTestCompletion()

        withContext(Dispatchers.Default) { delay(250.milliseconds) }
        assertEquals(1, executes.size)
        assertIs<ActionJob.StartJob>(executes.first())
    }

    @Test
    fun givenNoStart_whenTorCmd_thenThrowsException() = runTest {
        val factory = TorRuntime.Builder(env("sf_enqueue_cmd_fail")) {} as TestFactory
        assertFailsWith<IllegalStateException> { factory.executeAsync(TorCmd.Signal.Dump) }
    }

    @Test
    fun givenAwaitingStart_whenTorCmd_thenAddsToServiceFactorCtrlQueue() = runTest {
        val lces = mutableListOf<Lifecycle.Event>()
        val lock = SynchronizedObject()
        val bindDelay = 50.milliseconds

        val factory = env("sf_enqueue_success", start = { binder ->
            launch(Dispatchers.Default) {
//                println("LAUNCHED")
                // Simulate a delayed launch
                delay(bindDelay)
                binder.bind()
            }
        }).let { env -> TorRuntime.Builder(env) {
            observerStatic(RuntimeEvent.LIFECYCLE) {
                synchronized(lock) { lces.add(it) }
            }
        } }.ensureStoppedOnTestCompletion() as TestFactory

        val actionJob = factory.enqueue(Action.StartDaemon, {}, {}) as ActionJob.StartJob
        assertEquals(EnqueuedJob.State.Executing, actionJob.state)

        val cmdJob = factory.enqueue(TorCmd.Signal.Dump, { assertIs<CancellationException>(it) }, {})
        factory.enqueue(Action.StopDaemon, {}, {})

        lces.assertDoesNotContain("RealTorRuntime", Lifecycle.Event.Name.OnCreate)
        cmdJob.cancel(null)

        withContext(Dispatchers.Default) { delay(bindDelay + 250.milliseconds) }

        assertIs<InterruptedException>(actionJob.onErrorCause)

        lces.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnCreate)
        lces.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnDestroy)
    }

    @Test
    fun givenBindTimeout_whenManyJobs_thenAllAreCompletedAsExpected() = runTest {
        val factory = env("sf_timeout_interrupt", start = { /* do nothing */ }).let { env ->
            TorRuntime.Builder(env) {}
        }.ensureStoppedOnTestCompletion()

        val onFailure = OnFailure { assertIs<InterruptedException>(it) }

        var invocationSuccess = 0
        val jobs = listOf(
            factory.enqueue(Action.StartDaemon, onFailure) { invocationSuccess++ },
            factory.enqueue(Action.StopDaemon, onFailure) { invocationSuccess++ },
            factory.enqueue(Action.RestartDaemon, onFailure) { invocationSuccess++ },
            factory.enqueue(Action.StartDaemon, onFailure) { invocationSuccess++ },
            factory.enqueue(TorCmd.Signal.Dump, onFailure) { invocationSuccess++ },
            factory.enqueue(TorCmd.Signal.NewNym, onFailure) { invocationSuccess++ },
            factory.enqueue(TorCmd.Signal.Heartbeat, onFailure) { invocationSuccess++ },
        )

        // Timeout is 500ms, so have to wait until after that.
        withContext(Dispatchers.Default) { delay(1_000.milliseconds) }

        for (job in jobs) {

            var i = 0
            while (job.isActive && i++ < 10) {
                withContext(Dispatchers.Default) {
                    delay(50.milliseconds)
                }
            }

            val expected = if (job is ActionJob.StopJob) {
                EnqueuedJob.State.Success
            } else {
                EnqueuedJob.State.Error
            }

            assertEquals(expected, job.state)
        }

        // For ActionStop should be the only successful one
        assertEquals(1, invocationSuccess)

        // the cmd queue should have been destroyed and reset to null
        assertFailsWith<IllegalStateException> { factory.executeAsync(TorCmd.Signal.Dump) }
    }

    @Test
    fun givenActionStop_whenAlreadyStopped_thenIsImmediateSuccess() {
        val factory = TorRuntime.Builder(env("sf_stop_immediate")) {}
        val job = factory.enqueue(Action.StopDaemon, {}, {})
        assertEquals(EnqueuedJob.State.Success, job.state)
    }

    @Test
    fun givenActionRestart_whenAlreadyStarted_thenIsNotDestroyed() = runTest {
        val lces = mutableListOf<Lifecycle.Event>()
        val lock = SynchronizedObject()

        val factory = TorRuntime.Builder(env("sf_restart")) {
            observerStatic(RuntimeEvent.LIFECYCLE) {
                synchronized(lock) { lces.add(it) }
            }

//            observerStatic(RuntimeEvent.LIFECYCLE) { println(it) }
//            observerStatic(RuntimeEvent.LISTENERS) { println(it) }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.LOG.INFO) { println(it) }
//            observerStatic(RuntimeEvent.LOG.WARN) { println(it) }
//            observerStatic(RuntimeEvent.LOG.PROCESS) { println(it) }
//            observerStatic(RuntimeEvent.STATE) { println(it) }
        }.ensureStoppedOnTestCompletion()

        factory.startDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        synchronized(lock) {
            lces.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnCreate, fid = factory)
            lces.assertContains("DestroyableTorRuntime", Lifecycle.Event.Name.OnCreate, fid = factory)
            lces.assertContains("TorProcess", Lifecycle.Event.Name.OnCreate, fid = factory)
            lces.assertContains("TorProcess", Lifecycle.Event.Name.OnStart, fid = factory)
            lces.assertContains("RealTorCtrl", Lifecycle.Event.Name.OnCreate, fid = factory)

            lces.assertDoesNotContain("TorProcess", Lifecycle.Event.Name.OnDestroy, fid = factory)
            lces.assertDoesNotContain("RealTorCtrl", Lifecycle.Event.Name.OnDestroy, fid = factory)
            lces.clear()
        }

        // Should already be started and do nothing
        factory.startDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        synchronized(lock) { assertTrue(lces.isEmpty()) }

        factory.restartDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        synchronized(lock) {
            // Old process stopped
            lces.assertContains("TorProcess", Lifecycle.Event.Name.OnDestroy, fid = factory)
            lces.assertContains("RealTorCtrl", Lifecycle.Event.Name.OnDestroy, fid = factory)

            // New process started
            lces.assertContains("TorProcess", Lifecycle.Event.Name.OnCreate, fid = factory)
            lces.assertContains("TorProcess", Lifecycle.Event.Name.OnStart, fid = factory)
            lces.assertContains("RealTorCtrl", Lifecycle.Event.Name.OnCreate, fid = factory)

            lces.assertDoesNotContain("RealTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = factory)
            lces.assertDoesNotContain("DestroyableTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = factory)
            lces.clear()
        }

        factory.stopDaemonAsync()
        withContext(Dispatchers.Default) { delay(50.milliseconds) }

        synchronized(lock) {
            lces.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = factory)
            lces.assertContains("DestroyableTorRuntime", Lifecycle.Event.Name.OnDestroy, fid = factory)
        }
    }

    @Test
    fun givenActionStart_whenFailures_thenFailsAsExpected() = runTest {
        val lces = mutableListOf<Lifecycle.Event>()
        val lock = SynchronizedObject()

        var failureScenario = ConfigBuilderCallback { throw AssertionError() }

        val factory = TorRuntime.Builder(env("sf_process_fail")) {
            observerStatic(RuntimeEvent.LIFECYCLE) {
                synchronized(lock) { lces.add(it) }
            }

//            observerStatic(RuntimeEvent.LIFECYCLE) { println(it) }
//            observerStatic(RuntimeEvent.LISTENERS) { println(it) }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.LOG.INFO) { println(it) }
//            observerStatic(RuntimeEvent.LOG.WARN) { println(it) }
            observerStatic(RuntimeEvent.LOG.PROCESS) { println(it) }
            observerStatic(RuntimeEvent.STATE) { println(it) }
            config { environment ->
                apply(environment, failureScenario)
            }
        }.ensureStoppedOnTestCompletion()

        factory.environment().torrcFile.delete()

        suspend fun assertLCEs() {
            withContext(Dispatchers.Default) { delay(50.milliseconds) }

            synchronized(lock) {
                lces.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnDestroy)
                lces.assertContains("DestroyableTorRuntime", Lifecycle.Event.Name.OnDestroy)
                lces.assertContains("TorProcess", Lifecycle.Event.Name.OnCreate)
                lces.assertContains("TorProcess", Lifecycle.Event.Name.OnDestroy)
                lces.clear()
            }
        }

        // Should cause TorConfigGenerator.generate to fail
        assertFailsWith<AssertionError> { factory.startDaemonAsync() }
        assertLCEs()

        // Should cause --verify-config check to fail
        failureScenario = ConfigBuilderCallback { environment ->
            environment.torrcFile.writeUtf8("DNSPort -1")
        }

        assertFailsWith<IOException> {
            try {
                factory.startDaemonAsync()
            } catch (t: Throwable) {
//                t.printStackTrace()
                throw t
            }
        }
        assertLCEs()

        // Now ensure that any queued TorCmd are also interrupted when start fails
        val startAction = factory.enqueue(Action.StartDaemon, {}, {}) as ActionJob.StartJob
        val cmdAction = factory.enqueue(TorCmd.Signal.NewNym, {}, {})

        withContext(Dispatchers.Default) {
            while (startAction.isActive) {
                delay(10.milliseconds)
            }
        }

        assertLCEs()
        assertTrue(startAction.isError)
        assertTrue(cmdAction.isError)
        assertIs<IOException>(startAction.onErrorCause)
    }

    private fun TorRuntime.ServiceFactory.Binder.bind(
        events: Set<TorEvent> = emptySet(),
        network: NetworkObserver? = null,
        torEvent: Set<TorEvent.Observer> = emptySet(),
        runtimeEvent: Set<RuntimeEvent.Observer<*>> = emptySet(),
    ): Lifecycle.DestroyableTorRuntime = onBind(events, network, torEvent, runtimeEvent)
}
