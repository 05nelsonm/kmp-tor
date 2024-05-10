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
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKmpTorApi::class)
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
        testDir: String,
        start: ((TorRuntime.ServiceFactory.Binder) -> Unit)? = null,
        block: TorRuntime.Environment.Builder.() -> Unit = {},
    ) = TorRuntime.Environment.Builder(
        SysTempDir.resolve("kmp_tor_test/$testDir/work"),
        SysTempDir.resolve("kmp_tor_test/$testDir/cache"),
        installer = { dir -> TorResources(dir) },
    ) {
        serviceFactoryLoader = object : TorRuntime.ServiceFactory.Loader() {
            override fun loadProtected(initializer: TorRuntime.ServiceFactory.Initializer): TorRuntime.ServiceFactory {
                return TestFactory(initializer, start)
            }
        }

        block(this)
    }

    @Test
    fun givenEnvironment_whenServiceFactoryLoader_thenCreatesServiceFactory() {
        val lces = mutableListOf<Lifecycle.Event>()
        val runtime = TorRuntime.Builder(env("sf_create")) {
            observer(RuntimeEvent.LIFECYCLE.observer { lces.add(it) })
        }

        assertIs<TestFactory>(runtime)
        assertEquals(2, lces.size)
        assertEquals("RealServiceFactoryCtrl", lces.first().className)
        assertEquals(TestFactory::class.simpleName, lces.last().className)

        lces.forEach { lce ->
            assertEquals(Lifecycle.Event.Name.OnCreate, lce.name)
            assertEquals(runtime.fidEllipses, lce.fid)
            assertEquals(runtime.hashCode(), lce.hash)
        }
    }

    @Test
    fun givenInitializer_whenUsedMoreThanOnce_thenThrowsException() {
        val factory = TorRuntime.Builder(env("sf_is_instance")) {} as TestFactory
        assertFailsWith<IllegalStateException> {
            TestFactory(factory.initializer)
        }
    }

    @Test
    fun givenBinder_whenBindAndOtherInstanceIsNotDestroyed_thenDestroysPriorInstance() {
        val factory = TorRuntime.Builder(env("sf_multi_bind_destroy")) {} as TestFactory
        val warnings = mutableListOf<String>()
        factory.subscribe(RuntimeEvent.LOG.WARN.observer { warnings.add(it) })
        val runtime1 = factory.testBinder.bind()
        val runtime2 = factory.testBinder.bind()
        assertTrue(runtime1.isDestroyed())

        val warning = warnings.filter {
            it.contains("onBind was called before previous instance was destroyed")
        }
        assertEquals(1, warning.size)
        runtime2.destroy()
    }

    @Test
    fun givenNoStart_whenBindWithoutEnqueue_thenAutoEnqueuesStartJob() = runTest {
        val factory = TorRuntime.Builder(env("sf_enqueue_start")) {} as TestFactory

        val executes = mutableListOf<ActionJob>()
        factory.subscribe(RuntimeEvent.EXECUTE.ACTION.observer { executes.add(it) })

        val runtime = factory.testBinder.bind()

        withContext(Dispatchers.Default) { delay(250.milliseconds) }
        assertEquals(1, executes.size)
        assertIs<ActionJob.StartJob>(executes.first())
        runtime.destroy()
    }

    @Test
    fun givenNoStart_whenTorCmd_thenThrowsException() = runTest {
        val factory = TorRuntime.Builder(env("sf_enqueue_cmd_fail")) {} as TestFactory
        assertFailsWith<IllegalStateException> { factory.executeAsync(TorCmd.Signal.Dump) }
    }

    @Test
    fun givenAwaitingStart_whenTorCmd_thenAddsToServiceFactorCtrlQueue() = runTest {
        val lces = mutableListOf<Lifecycle.Event>()
        val bindDelay = 50.milliseconds

        val factory = env("sf_enqueue_success", start = { binder ->
            launch(Dispatchers.Default) {
                println("LAUNCHED")
                // Simulate a delayed launch
                delay(bindDelay)
                binder.bind()
            }
        }).let { env -> TorRuntime.Builder(env) {
            observerStatic(RuntimeEvent.LIFECYCLE) { lces.add(it) }
        } } as TestFactory

        val actionJob = factory.enqueue(Action.StartDaemon, {}, {}) as ActionJob.StartJob
        assertEquals(EnqueuedJob.State.Executing, actionJob.state)

        val cmdJob = factory.enqueue(TorCmd.Signal.Dump, { assertIs<CancellationException>(it) }, {})
        factory.enqueue(Action.StopDaemon, {}, {})

        var containsOnCreate = false
        for (lce in lces) {
            if (lce.className != "RealTorRuntime") continue
            if (lce.name != Lifecycle.Event.Name.OnCreate) continue
            containsOnCreate = true
        }

        // RealTorRuntime was not created yet, which
        // means the cmdJob was successfully created
        // by RealServiceFactoryCtrl b/c start was
        // in the stack.
        assertFalse(containsOnCreate)
        cmdJob.cancel(null)

        withContext(Dispatchers.Default) { delay(bindDelay + 250.milliseconds) }

        println(actionJob)
        assertIs<InterruptedException>(actionJob.onErrorCause)

        var containsOnDestroy = false
        for (lce in lces) {
            if (lce.className != "RealTorRuntime") continue
            if (lce.name != Lifecycle.Event.Name.OnDestroy) continue
            containsOnDestroy = true
        }

        assertTrue(containsOnDestroy)
    }

    @Test
    fun givenBindTimeout_whenManyJobs_thenAllAreCompletedAsExpected() = runTest {
        val factory = env("sf_timeout_interrupt", start = { /* do nothing */ }).let { env ->
            TorRuntime.Builder(env) {}
        }

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

    private fun TorRuntime.ServiceFactory.Binder.bind(
        events: Set<TorEvent> = emptySet(),
        network: NetworkObserver? = null,
        torEvent: Set<TorEvent.Observer> = emptySet(),
        runtimeEvent: Set<RuntimeEvent.Observer<*>> = emptySet(),
    ): Lifecycle.DestroyableTorRuntime = onBind(events, network, torEvent, runtimeEvent)
}
