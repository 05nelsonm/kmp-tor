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
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import kotlin.test.*

@OptIn(ExperimentalKmpTorApi::class)
class ServiceFactoryUnitTest {

    private class TestFactory(
        val initializer: Initializer
    ): TorRuntime.ServiceFactory(initializer) {

        val testBinder get() = binder

        override fun startService() {
            binder.onBind(
                emptySet(),
                null,
                emptySet(),
                emptySet(),
            )
        }
    }

    private val env = TorRuntime.Environment.Builder(
        "factory_unit_test/work".toFile(),
        "factory_unit_test/cache".toFile(),
        installer = { dir -> TorResources(dir) },
    ) {
        serviceFactoryLoader = object : TorRuntime.ServiceFactory.Loader() {
            override fun loadProtected(initializer: TorRuntime.ServiceFactory.Initializer): TorRuntime.ServiceFactory {
                return TestFactory(initializer)
            }
        }
    }

    @Test
    fun givenEnvironment_whenServiceFactoryLoader_thenCreatesServiceFactory() {
        val lces = mutableListOf<Lifecycle.Event>()
        val runtime = TorRuntime.Builder(env) {
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
        val factory = TorRuntime.Builder(env) {} as TestFactory
        assertFailsWith<IllegalStateException> {
            TestFactory(factory.initializer)
        }
    }

    @Test
    fun givenBinder_whenBindAndOtherInstanceIsNotDestroyed_thenDestroysPriorInstance() {
        val factory = TorRuntime.Builder(env) {} as TestFactory
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

    private fun TorRuntime.ServiceFactory.Binder.bind(
        events: Set<TorEvent> = emptySet(),
        network: NetworkObserver? = null,
        torEvent: Set<TorEvent.Observer> = emptySet(),
        runtimeEvent: Set<RuntimeEvent.Observer<*>> = emptySet(),
    ): Lifecycle.DestroyableTorRuntime = onBind(events, network, torEvent, runtimeEvent)
}
