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

import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AbstractTorServiceUIUnitTest {

    class TestUI(
        args: Args,
        init: Any = INIT,
    ): AbstractTorServiceUI<TestUI.Args, TestUI.Config>(
        args,
        init,
    ) {

        init {
            serviceChildScope.cancel()
        }

        var invocationOnDestroy = 0
            private set

        override fun onDestroy() {
            super.onDestroy()
            invocationOnDestroy++
        }

        class Args(
            defaultConfig: Config,
            scope: TestScope,
            init: Any = INIT
        ): AbstractTorServiceUI.Args(
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
        ): AbstractTorServiceUI.Factory<Args, Config, TestUI>(
            defaultConfig,
            init,
        ) {

            public override fun newInstanceProtected(args: Args): TestUI {
                return TestUI(args)
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
    fun givenArgs_whenInitialized_thenSuccessiveInvocationsThrowException() = runTest {
        val args = TestUI.Args(config, this)
        args.initialize()
        assertFailsWith<IllegalStateException> { args.initialize() }
    }

    @Test
    fun givenConfig_whenEmptyFields_thenThrowsException() {
        assertFailsWith<IllegalArgumentException> { TestUI.Config(emptyMap()) }
    }

    @Test
    fun givenFactory_whenNewInstance_thenArgsAreVerified() = runTest {
        run {
            val args = TestUI.Args(config, this)
            val factory = TestUI.Factory(config)
            factory.newInstance(args)

            assertFailsWith<IllegalStateException> { args.initialize() }
            assertFailsWith<IllegalStateException> { factory.newInstance(args) }
        }

        run {
            val other = TestUI(TestUI.Args(config, this))
            val factory = object : TestUI.Factory(config) {
                override fun newInstanceProtected(args: TestUI.Args): TestUI {
                    return other
                }
            }

            val otherArgs = TestUI.Args(config, this)
            assertFailsWith<IllegalStateException> { factory.newInstance(otherArgs) }
            assertFailsWith<IllegalStateException> { otherArgs.initialize() }
        }
    }

    @Test
    fun testFactory_whenNewInstance_thenOnDestroyCallbackSet() = runTest {
        val factory = TestUI.Factory(config)
        val instance = factory.newInstance(TestUI.Args(config, this))

        currentCoroutineContext().job.invokeOnCompletion {
            assertEquals(1, instance.invocationOnDestroy)
        }
    }
}
