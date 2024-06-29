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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AbstractTorServiceUIUnitTest {

    class TestUI(
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
            fields: Map<String, Any>,
            init: Any = INIT
        ): AbstractTorServiceUI.Config(
            fields,
            init,
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

            override fun onDestroy() {
                super.onDestroy()
                invocationOnDestroy++
            }
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

        assertFailsWith<IllegalStateException> { TestUI.Config(mapOf("" to ""), init = Any()) }
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

        val (job, instance) = ui.newInstanceState(instanceConfig = null)
        state = instance
        val lastArgs = iArgs!!
        assertFailsWith<IllegalStateException> { lastArgs.initialize() }

        // Check Args.Instance created are consumed when implementation
        // does not return new instance.
        assertFailsWith<IllegalStateException> { ui.newInstanceState(instanceConfig = null) }
        assertNotEquals(lastArgs, iArgs)
        assertFailsWith<IllegalStateException> { iArgs?.initialize() }

        currentCoroutineContext().job.invokeOnCompletion {
            assertTrue(instance.isDestroyed())
            assertFalse(instance.scope.isActive)
            assertFalse(job.isActive)
            assertEquals(1, instance.invocationOnDestroy)
        }
    }
}
