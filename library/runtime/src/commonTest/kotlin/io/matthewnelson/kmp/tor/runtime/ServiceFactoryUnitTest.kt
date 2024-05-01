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

import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import kotlin.test.*

@OptIn(ExperimentalKmpTorApi::class)
class ServiceFactoryUnitTest {

    private class TestFactory(
        val initializer: Initializer
    ): TorRuntime.ServiceFactory(initializer) {

        val testBinder get() = binder

        override fun startService() { throw RuntimeException("Not Implemented") }
    }

    private val env = TorRuntime.Environment.Builder(
        "factory_unit_test/work".toFile(),
        "factory_unit_test/cache".toFile(),
        installer = { dir -> object : ResourceInstaller<ResourceInstaller.Paths.Tor>(dir) {
            override fun install(): Paths.Tor { fail() }
        } }
    ) {
        serviceFactoryLoader = object : TorRuntime.ServiceFactory.Loader() {
            override fun loadProtected(initializer: TorRuntime.ServiceFactory.Initializer): TorRuntime.ServiceFactory {
                return TestFactory(initializer)
            }
        }
    }

    @Test
    fun givenEnvironment_whenServiceFactoryLoader_thenCreatesServiceFactory() {
        val runtime = TorRuntime.Builder(env) {}
        assertIs<TestFactory>(runtime)
    }

    @Test
    fun givenInitializer_whenUsedMoreThanOnce_thenThrowsException() {
        val factory = TorRuntime.Builder(env) {} as TestFactory
        assertFailsWith<IllegalStateException> {
            TestFactory(factory.initializer)
        }
    }

    @Test
    fun givenBinder_whenBindAndOtherInstanceIsNotDestroyed_thenDestroysPriorInstance() {
        val factory = TorRuntime.Builder(env) {} as TestFactory
        var invocationWarn = 0
        factory.subscribe(RuntimeEvent.LOG.WARN.observer { invocationWarn++ })
        val runtime = factory.testBinder.bind()
        factory.testBinder.bind().destroy()
        assertTrue(runtime.isDestroyed())
        assertEquals(1, invocationWarn)
    }

    private fun TorRuntime.ServiceFactory.Binder.bind(
        events: Set<TorEvent> = emptySet(),
        network: NetworkObserver? = null,
        torEvent: Set<TorEvent.Observer> = emptySet(),
        runtimeEvent: Set<RuntimeEvent.Observer<*>> = emptySet(),
    ): Lifecycle.DestroyableTorRuntime = onBind(events, network, torEvent, runtimeEvent)
}
