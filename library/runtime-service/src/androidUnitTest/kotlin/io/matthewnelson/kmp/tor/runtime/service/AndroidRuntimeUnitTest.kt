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

import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.resource.tor.TorResources // TODO: REMOVE
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import kotlin.test.*

@OptIn(ExperimentalKmpTorApi::class)
class AndroidRuntimeUnitTest {

    private val config = TorServiceConfig.Builder {
        testUseBuildDirectory = true
    }

    @Test
    fun givenTorRuntime_whenNotAndroidRuntime_thenIsNotAndroidTorServiceFactory() {
        val environment = config.newEnvironment(
            dirName = "rt_unit_tests",
            installer = { installationDir ->
                TorResources(installationDir)
            },
            block = {
                defaultEventExecutor = OnEvent.Executor.Immediate
            }
        )

        val path = environment.workDirectory.path
        assertTrue(path.contains("kmp_tor_android_test"))
        assertTrue(path.contains("build"))
        assertEquals("work", environment.workDirectory.name)
        assertEquals("cache", environment.cacheDirectory.name)

        val lces = mutableListOf<Lifecycle.Event>()
        val runtime = TorRuntime.Builder(environment) {
            observerStatic(RuntimeEvent.LIFECYCLE) { lces.add(it) }
        }

        assertEquals("RealTorRuntime", runtime::class.simpleName)
        assertIsNot<TorRuntime.ServiceFactory>(runtime)

        val lce = lces.filter { it.className == "RealTorRuntime" }
        assertEquals(1, lce.size)
        assertEquals(Lifecycle.Event.Name.OnCreate, lce.first().name)

        // Not a service, so should not print the hashCode
        assertFalse(runtime.toString().contains('@'))
    }
}
