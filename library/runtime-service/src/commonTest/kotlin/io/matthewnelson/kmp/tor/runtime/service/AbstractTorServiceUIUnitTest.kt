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
package io.matthewnelson.kmp.tor.runtime.service

import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@Suppress("BooleanLiteralArgument")
@OptIn(ExperimentalKmpTorApi::class)
class AbstractTorServiceUIUnitTest {

    class TestUI
    // Only public to test init args on a few tests.
    // Otherwise, Factory.newInstanceUI must be utilized
    // for functionality
    public constructor(
        args: Args,
        init: Any = INIT,
        private val newInstanceState: ((args: AbstractTorServiceUI.Args.Instance) -> State)? = null,
    ): AbstractTorServiceUI<TestUI.Args, TestUI.Config, TestUI.State>(
        args,
        init,
    ) {

        var invocationOnDestroy = 0
            private set

        val scope = serviceChildScope

        val updates = mutableListOf<Update>()

        val instanceStatesTest: Collection<State> get() = instanceStates

        fun previousTest() { previous() }
        fun nextTest() { next() }

        protected override fun onUpdate(displayed: State, hasPrevious: Boolean, hasNext: Boolean) {
            updates.add(Update(displayed, Update.Indicators(hasPrevious, hasNext)))
        }

        protected override fun onDestroy() {
            super.onDestroy()
            invocationOnDestroy++
        }

        protected override fun newInstanceStateProtected(
            args: AbstractTorServiceUI.Args.Instance
        ): State {
            val callback = newInstanceState

            return if (callback != null) {
                callback(args)
            } else {
                State(args)
            }
        }

        class Args(
            defaultConfig: Config,
            scope: TestScope,
            val newInstanceState: ((args: Instance) -> State)? = null,
            init: Any = INIT
        ): AbstractTorServiceUI.Args.UI(
            defaultConfig,
            scope,
            init,
        )

        class Config(
            fields: Map<String, Any?>,
        ): AbstractTorServiceUI.Config(
            fields,
        )

        open class Factory(
            defaultConfig: Config,
            init: Any = INIT,
        ): AbstractTorServiceUI.Factory<Args, Config, State, TestUI>(
            defaultConfig,
            init,
        ) {

            public override fun newInstanceUIProtected(args: Args): TestUI {
                return TestUI(args, newInstanceState = args.newInstanceState)
            }
        }

        class State(args: AbstractTorServiceUI.Args.Instance): InstanceState<Config>(args) {

            val scope = instanceScope
            var invocationOnDestroy = 0
                private set

            override val events: Set<TorEvent> = emptySet()
            override val observersRuntimeEvent: Set<RuntimeEvent.Observer<*>> = emptySet()
            override val observersTorEvent: Set<TorEvent.Observer> = emptySet()

            override fun onDestroy() {
                super.onDestroy()
                invocationOnDestroy++
            }

            fun postStateChangeTest() { postStateChange() }
        }

        data class Update(val state: State, val indicators: Indicators) {

            data class Indicators(val hasPrevious: Boolean, val hasNext: Boolean)
        }
    }

    val config = TestUI.Config(mapOf("" to ""))

    @Test
    fun givenConstructor_whenInvalidINIT_thenThrowIllegalStateException() = runTest {
        val args = TestUI.Args(config, this)

        assertFailsWith<IllegalStateException> { TestUI(args, init = Any()) }
        // Should have also been initialized from UI init block
        // (before throwing exception for an invalid init argument)
        assertFailsWith<IllegalStateException> { args.initialize() }

        assertFailsWith<IllegalStateException> { TestUI.Args(config, this, init = Any()) }
        assertFailsWith<IllegalStateException> { TestUI.Factory(config, init = Any()) }
    }

    @Test
    fun givenArgs_whenInitialized_thenSuccessiveInitializeInvocationsThrowException() = runTest {
        val args = TestUI.Args(config, this)
        args.initialize()
        assertFailsWith<IllegalStateException> { args.initialize() }
    }

    @Test
    fun givenConfig_whenEmptyFields_thenThrowsException() {
        assertFailsWith<IllegalArgumentException> { TestUI.Config(emptyMap()) }
    }

    @Test
    fun givenFactory_whenNewUI_thenArgsAreVerified() = runTest {
        run {
            val args = TestUI.Args(config, this)
            val factory = TestUI.Factory(config)
            factory.newInstanceUI(args)

            assertFailsWith<IllegalStateException> { args.initialize() }
            assertFailsWith<IllegalStateException> { factory.newInstanceUI(args) }
        }

        run {
            val other = TestUI(TestUI.Args(config, this))
            val factory = object : TestUI.Factory(config) {
                override fun newInstanceUIProtected(args: TestUI.Args): TestUI {
                    return other
                }
            }

            val otherArgs = TestUI.Args(config, this)
            assertFailsWith<IllegalStateException> { factory.newInstanceUI(otherArgs) }
            assertFailsWith<IllegalStateException> { otherArgs.initialize() }
        }
    }

    @Test
    fun givenFactory_whenNewUI_thenOnDestroyCallbackSet() = runTest {
        val factory = TestUI.Factory(config)
        val instance = factory.newInstanceUI(TestUI.Args(config, this))

        assertTrue(instance.scope.isActive)
        assertFalse(instance.isDestroyed())

        currentCoroutineContext().job.invokeOnCompletion {
            assertFalse(instance.scope.isActive)
            assertTrue(instance.isDestroyed())
            assertEquals(1, instance.invocationOnDestroy)
        }
    }

    @Test
    fun givenUI_whenNewInstanceState_thenArgsAreVerified() = runTest {
        var state: TestUI.State? = null
        var iArgs: AbstractTorServiceUI.Args.Instance? = null

        val factory = TestUI.Factory(config)
        val ui = factory.newInstanceUI(TestUI.Args(config, this, newInstanceState = { args ->
            iArgs = args
            state ?: TestUI.State(args)
        }))

        val (job, instance) = ui.newTestInstanceState(fid = "abcde12345")
        state = instance
        val lastArgs = iArgs!!
        assertFailsWith<IllegalStateException> { lastArgs.initialize() }

        // Check Args.Instance created are consumed when implementation
        // does not return new instance.
        assertFailsWith<IllegalStateException> { ui.newTestInstanceState(fid = "") }
        assertNotEquals(lastArgs, iArgs)
        assertFailsWith<IllegalStateException> { iArgs?.initialize() }

        currentCoroutineContext().job.invokeOnCompletion {
            assertTrue(instance.isDestroyed())
            assertFalse(instance.scope.isActive)
            assertFalse(job.isActive)
            assertEquals(1, instance.invocationOnDestroy)
        }
    }

    @Test
    fun givenUI_whenInstanceState_thenDispatchesUpdatesToUI() = runTest {
        val factory = TestUI.Factory(config)
        val ui = factory.newInstanceUI(TestUI.Args(config, this))
        val (instanceJob, instance) = ui.newTestInstanceState(fid = "abcde12345")

        ui.instanceStatesTest.let { instances ->
            assertEquals(1, instances.size)
            assertEquals(instance, instances.first())
        }

        delayTest()
        assertEquals(1, ui.updates.size)
        assertEquals("abcde12345", ui.updates[0].state.fid)

        instance.postStateChangeTest()
        delayTest()
        assertEquals(2, ui.updates.size)
        assertEquals("abcde12345", ui.updates[1].state.fid)

        instanceJob.cancel()
        delayTest()

        // _displayed was set to null because this is the only instance
        // and posting the state change will just return b/c instance
        // key does not match _displayed.
        assertEquals(2, ui.updates.size)

        assertTrue(ui.instanceStatesTest.isEmpty())
    }

    @Test
    fun givenUI_whenMultipleInstanceStates_thenUpdatesHasPreviousOrNextAsExpected() = runTest {
        val factory = TestUI.Factory(config)
        val ui = factory.newInstanceUI(TestUI.Args(config, this))

        val numInstances = 5
        val instances = mutableListOf<Pair<Job, TestUI.State>>()
        repeat(numInstances) { i ->
            val pair = ui.newTestInstanceState(fid = "abcde12345$i")
            instances.add(pair)
            delayTest()
        }

        assertEquals(numInstances, instances.size)
        assertEquals(instances.size, ui.instanceStatesTest.size)

        // Even with 5 added, the only update posted should be when
        // the 2nd instance was created for hasNext
        assertEquals(2, ui.updates.size)
        assertEquals(TestUI.Update.Indicators(false, false), ui.updates[0].indicators)
        assertEquals(TestUI.Update.Indicators(false, true), ui.updates[1].indicators)

        // instanceNumber 0 -> 1
        ui.nextTest()
        delayTest()
        assertEquals(3, ui.updates.size)
        assertEquals(TestUI.Update.Indicators(true, true), ui.updates[2].indicators)

        // instanceNumber 1 -> 2
        ui.nextTest()
        // instanceNumber 2 -> 3
        ui.nextTest()

        delayTest()
        // 1st update should not post an update b/c the _displayed
        // variable that was set was changed in 2nd call. This indicates
        // that the launch lambda performs a check before calling onUpdate.
        assertEquals(4, ui.updates.size)
        assertEquals(ui.instanceStatesTest.elementAt(3), ui.updates[3].state)
        assertEquals(TestUI.Update.Indicators(true, true), ui.updates[3].indicators)

        // instanceNumber 3 -> 2
        ui.previousTest()
        delayTest()
        assertEquals(5, ui.updates.size)
        assertEquals(ui.instanceStatesTest.elementAt(2), ui.updates[4].state)
        assertEquals(TestUI.Update.Indicators(true, true), ui.updates[4].indicators)

        // instanceNumber 2 -> 3
        ui.nextTest()
        // instanceNumber 3 -> 4
        ui.nextTest()
        delayTest()
        assertEquals(6, ui.updates.size)
        assertEquals(ui.instanceStatesTest.last(), ui.updates[5].state)
        assertEquals(TestUI.Update.Indicators(true, false), ui.updates[5].indicators)

        // Should not do anything because on last instance
        ui.nextTest()
        delayTest()
        assertEquals(6, ui.updates.size)

        // instanceNumber 4 -> 3
        // Instance removed, update with new displayed Instance
        instances.last().first.cancel()
        // Should immediately remove instance, but not dispatch an update
        // b/c is not next to currently displayed.
        assertEquals(numInstances - 1, ui.instanceStatesTest.size)
        delayTest()
        assertEquals(7, ui.updates.size)
        assertEquals(ui.instanceStatesTest.last(), ui.updates[6].state)
        assertEquals(TestUI.Update.Indicators(true, false), ui.updates[6].indicators)

        // instanceNumber 3 -> 2
        instances.first().first.cancel()
        // Should immediately remove instance, but not dispatch an update
        // b/c is not next to currently displayed.
        assertEquals(numInstances - 2, ui.instanceStatesTest.size)
        delayTest()
        assertEquals(7, ui.updates.size)

        // Should not post b/c it is not the currently displayed instance
        ui.instanceStatesTest.first().postStateChangeTest()
        delayTest()
        assertEquals(7, ui.updates.size)

        // instanceNumber 2 -> 1
        ui.previousTest()
        delayTest()
        assertEquals(8, ui.updates.size)
        assertEquals(TestUI.Update.Indicators(true, true), ui.updates[7].indicators)

        assertEquals(3, ui.instanceStatesTest.size)
        assertEquals(instances[2].first, ui.instanceStatesTest.elementAt(1).scope.coroutineContext.job)

        // instanceNumber 1 -> 0
        instances[2].first.cancel()
        delayTest()
        assertEquals(9, ui.updates.size)
        assertEquals(TestUI.Update.Indicators(false, true), ui.updates[8].indicators)

        // Destroy remaining
        instances.forEach { it.first.cancel() }
        delayTest()
        // No updates should post b/c all were removed and the
        // currently set _displayed variable is null and does not
        // match the instance in the coroutine launch lambda, so
        // stops.
        assertEquals(9, ui.updates.size)
    }

    private suspend fun delayTest() = delay(500)

    // overloaded with defaults for making tests simpler
    private fun TestUI.newTestInstanceState(
        instanceConfig: Config? = null,
        fid: String = "abcde12345",
        debugger: () -> ((() -> String) -> Unit)? = { null },
        observeSignalNewNym: (String?, OnEvent.Executor?, OnEvent<String?>) -> Disposable.Once? = { _, _, _ -> null },
        processorAction: () -> Action.Processor? = { null },
        processorTorCmd: () -> TorCmd.Unprivileged.Processor? = { null },
    ): Pair<CompletableJob, TestUI.State> = newInstanceState(
        instanceConfig,
        fid,
        debugger,
        observeSignalNewNym,
        processorAction,
        processorTorCmd,
    )
}
