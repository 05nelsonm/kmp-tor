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
import io.matthewnelson.kmp.tor.runtime.FileID
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

        val updates = mutableListOf<Pair<FileID, String>>()

        val instanceStatesTest: Map<FileID, State> get() {
            return instanceStates.toMap()
        }

        protected override fun onUpdate(target: FileIDKey, type: UpdateType) {
            updates.add(target to type.name)
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
    fun givenFactory_whenNewUIInstance_thenArgsAreVerified() = runTest {
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
    fun givenFactory_whenNewUIInstance_thenOnDestroyCallbackSet() = runTest {
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
    fun givenUIInstance_whenNewInstanceState_thenArgsAreVerified() = runTest {
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
    fun givenUIInstance_whenInstanceState_thenDispatchesUpdatesToUI() = runTest {
        val factory = TestUI.Factory(config)
        val ui = factory.newInstanceUI(TestUI.Args(config, this))
        val (instanceJob, instance) = ui.newTestInstanceState(fid = "abcde12345")

        ui.instanceStatesTest.let { instances ->
            assertEquals(1, instances.size)
            assertEquals(instance, instances.values.first())
        }

        assertEquals(1, ui.updates.size)
        assertEquals("Added", ui.updates[0].second)
        assertEquals("abcde12345", ui.updates[0].first.fid)

        instance.postStateChangeTest()
        assertEquals(2, ui.updates.size)
        assertEquals("Changed", ui.updates[1].second)
        assertEquals("abcde12345", ui.updates[1].first.fid)

        instanceJob.cancel()
        assertEquals(3, ui.updates.size)
        assertEquals("Removed", ui.updates[2].second)
        assertEquals("abcde12345", ui.updates[2].first.fid)

        assertTrue(ui.instanceStatesTest.isEmpty())
    }

    // overloaded with defaults for making tests simpler
    private fun TestUI.newTestInstanceState(
        instanceConfig: Config? = null,
        fid: String = "abcde12345",
        debugger: () -> ((() -> String) -> Unit)? = { null },
        observeSignalNewNym: (String?, OnEvent.Executor?, OnEvent<String?>) -> Disposable? = { _, _, _ -> null },
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
