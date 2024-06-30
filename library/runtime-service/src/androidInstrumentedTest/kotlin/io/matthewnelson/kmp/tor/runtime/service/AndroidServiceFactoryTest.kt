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

import android.os.Build
import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonSync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonSync
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class AndroidServiceFactoryTest {

    private val config = TorServiceConfig.Builder {}

    private fun newEnvironment(dirName: String): TorRuntime.Environment {
        return config.newEnvironment(
            dirName = dirName,
            installer = { dir -> TorResources(dir) },
            block = {
                defaultEventExecutor = OnEvent.Executor.Immediate
            }
        )
    }

    @Test
    fun givenTorRuntime_whenAndroidRuntime_thenIsAndroidServiceFactory() {
        val environment = newEnvironment("sf_is_instance")

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
        if (Build.VERSION.SDK_INT < 21) {
            println("Skipping...")
            return
        }

        val environment = newEnvironment("sf_single")
        environment.debug = true

        var assertionErrorInvocation = 0
        var hasThrown = 0
        val lces = mutableListOf<Lifecycle.Event>()
        val factory = TorRuntime.Builder(environment) {
            observerStatic(RuntimeEvent.LIFECYCLE) {
//                println(it)
                synchronized(lces) { lces.add(it) }
            }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
            observerStatic(RuntimeEvent.ERROR) { t ->
                if (t is UncaughtException) {
                    if (t.cause is AssertionError) {
                        assertionErrorInvocation++
                        return@observerStatic
                    }
                    throw t
                }
            }
            observerStatic(RuntimeEvent.STATE, OnEvent.Executor.Main) { state ->
                // Should not cause crash
                when (hasThrown++) {
                    0 -> throw AssertionError("TESTING")
                    1 -> throw CancellationException("TESTING")
                    else -> println(state)
                }
            }
        }

        try {
            factory.startDaemonSync().stopDaemonSync()

            Blocking.threadSleep(50.milliseconds)

            synchronized(lces) {
                lces.assertContains("TorService", Lifecycle.Event.Name.OnUnbind)
                lces.assertContains("TorService", Lifecycle.Event.Name.OnDestroy)
            }

            assertEquals(1, assertionErrorInvocation)
        } catch (t: Throwable) {
            factory.enqueue(Action.StopDaemon, {}, {})
            throw t
        }
    }

    @Test
    fun givenTorService_whenMultipleRuntime_thenServiceIsDestroyedWhenLastRuntimeDestroyed() {
        if (Build.VERSION.SDK_INT < 21) {
            println("Skipping...")
            return
        }

        val env1 = newEnvironment("sf_multi1")
        val env2 = newEnvironment("sf_multi2")
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

        try {
            factory1.startDaemonSync()
            factory2.startDaemonSync()

            factory1.stopDaemonSync()

            Blocking.threadSleep(50.milliseconds)

            synchronized(lces1) {
                lces1.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnDestroy)
                lces1.assertContains("TorService", Lifecycle.Event.Name.OnUnbind)

                lces1.assertDoesNotContain("TorService", Lifecycle.Event.Name.OnDestroy)
            }
            synchronized(lces2) {
                lces2.assertDoesNotContain("TorService", Lifecycle.Event.Name.OnDestroy)
            }

            factory2.stopDaemonSync()

            Blocking.threadSleep(50.milliseconds)

            synchronized(lces1) {
                lces1.assertContains("TorService", Lifecycle.Event.Name.OnDestroy)
            }

            synchronized(lces2) {
                lces2.assertContains("RealTorRuntime", Lifecycle.Event.Name.OnDestroy)
                lces2.assertContains("TorService", Lifecycle.Event.Name.OnUnbind)
                lces2.assertContains("TorService", Lifecycle.Event.Name.OnDestroy)
            }
        } catch (t: Throwable) {
            factory1.enqueue(Action.StopDaemon, {}, {})
            factory2.enqueue(Action.StopDaemon, {}, {})
            throw t
        }
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
