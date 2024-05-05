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
package io.matthewnelson.kmp.tor.runtime.mobile

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonSync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonSync
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class AndroidServiceFactoryTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun givenTorRuntime_whenAndroidRuntime_thenIsAndroidServiceFactory() {
        val environment = app.createTorRuntimeEnvironment { dir -> TorResources(dir) }

        val lces = mutableListOf<Lifecycle.Event>()
        val factory = TorRuntime.Builder(environment) {
            observerStatic(RuntimeEvent.LIFECYCLE) { lces.add(it) }
        }

        assertEquals("AndroidServiceFactory", factory::class.simpleName)
        val lce = lces.filter { it.className == "AndroidServiceFactory" }
        assertEquals(1, lce.size)
        assertEquals(Lifecycle.Event.Name.OnCreate, lce.first().name)

        // Not a service, so should not print the hashCode
        assertFalse(factory.toString().contains('@'))
    }

    @Test
    fun givenTorService_whenRuntimeDestroyed_thenServiceIsDestroyed() {
        val environment = app.createTorRuntimeEnvironment { dir -> TorResources(dir) }
        environment.debug = true

        val lces = mutableListOf<Lifecycle.Event>()
        val factory = TorRuntime.Builder(environment) {
            observerStatic(RuntimeEvent.LIFECYCLE) {
//                println(it)
                synchronized(lces) { lces.add(it) }
            }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
        }

        factory.startDaemonSync().stopDaemonSync()

        lces.assertContains("TorService", Lifecycle.Event.Name.OnUnbind)
        lces.assertContains("TorService", Lifecycle.Event.Name.OnDestroy)
    }

    @Test
    fun givenTorService_whenMultipleRuntime_thenServiceIsDestroyedWhenLastRuntimeDestroyed() {
        val env1 = app.createTorRuntimeEnvironment { dir -> TorResources(dir) }
        val env2 = app.createTorRuntimeEnvironment(dirName = "torservice2") { dir -> TorResources(dir) }
        env1.debug = true
        env2.debug = true

        val lces1 = mutableListOf<Lifecycle.Event>()
        val factory1 = TorRuntime.Builder(env1) {
            observerStatic(RuntimeEvent.LIFECYCLE) {
//                println(it)
                synchronized(lces1) { lces1.add(it) }
            }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
        }

        val lces2 = mutableListOf<Lifecycle.Event>()
        val factory2 = TorRuntime.Builder(env2) {
            observerStatic(RuntimeEvent.LIFECYCLE) {
//                println(it)
                synchronized(lces2) { lces2.add(it) }
            }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
        }

        factory1.startDaemonSync()
        factory2.startDaemonSync()

        factory1.stopDaemonSync()
        lces1.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnDestroy)
        lces1.assertContains("TorService", Lifecycle.Event.Name.OnUnbind)

        lces1.assertDoesNotContain("TorService", Lifecycle.Event.Name.OnDestroy)
        lces2.assertDoesNotContain("TorService", Lifecycle.Event.Name.OnDestroy)

        factory2.stopDaemonSync()
        lces1.assertContains("TorService", Lifecycle.Event.Name.OnDestroy)

        lces2.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnDestroy)
        lces2.assertContains("TorService", Lifecycle.Event.Name.OnUnbind)
        lces2.assertContains("TorService", Lifecycle.Event.Name.OnDestroy)
    }

    private fun List<Lifecycle.Event>.assertDoesNotContain(className: String, name: Lifecycle.Event.Name) {
        var error: AssertionError? = null
        try {
            assertContains(className, name)
            error = AssertionError("LCEs contained $name for $className")
        } catch (_: AssertionError) {
            // pass
        }

        error?.let { throw it }
    }

    private fun List<Lifecycle.Event>.assertContains(className: String, name: Lifecycle.Event.Name) {
        for (lce in this) {
            if (lce.className != className) continue
            if (lce.name == name) return
        }

        fail("LCEs did not contain $name for $className")
    }
}
