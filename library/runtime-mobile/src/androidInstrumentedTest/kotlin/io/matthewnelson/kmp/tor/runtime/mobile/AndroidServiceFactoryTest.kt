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
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonSync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonSync
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlin.test.*

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
            val lock = Any()
            observerStatic(RuntimeEvent.LIFECYCLE) {
//                println(it)
                synchronized(lock) { lces.add(it) }
            }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
        }

        factory.startDaemonSync().stopDaemonSync()

        var containsUnbind = false
        var containsDestroy = false
        for (event in lces) {
            if (event.className != "TorService") continue

            if (event.name == Lifecycle.Event.Name.OnUnbind) {
                containsUnbind = true
            }
            if (event.name == Lifecycle.Event.Name.OnDestroy) {
                containsDestroy = true
            }
        }
        assertTrue(containsUnbind)
        assertTrue(containsDestroy)
    }
}
