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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Action.Companion.executeAsync
import io.matthewnelson.kmp.tor.runtime.TestUtils.testEnv
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class TorRuntimeUnitTest {

    @Test
    fun givenStartedState_whenFailure_thenStopEnqueued() = runTest {
        val lockJob = SynchronizedObject()
        val jobs = mutableListOf<ActionJob>()

        val runtime = TorRuntime.Builder(testEnv("rt_start_fail")) {
            observerStatic(RuntimeEvent.EXECUTE.ACTION) { job ->
                synchronized(lockJob) { jobs.add(job) }
            }

//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.LOG.INFO) { println(it) }
//            observerStatic(RuntimeEvent.LOG.WARN) { println(it) }
//            observerStatic(RuntimeEvent.LOG.PROCESS) { println(it) }

            config { environment ->
                environment.torrcFile.writeUtf8("""
                    # Startup fail simulation
                    DNSPort -1
                """.trimIndent())
            }
        }

        runtime.environment().debug = true

        listOf(
            Action.StartDaemon,
            Action.RestartDaemon,
        ).forEach { action ->
            assertFailsWith<IOException> { runtime.executeAsync(action) }

            withContext(Dispatchers.Default) { delay(50.milliseconds) }
            synchronized(lockJob) {
                assertEquals(1, jobs.count { it is ActionJob.StopJob })
                jobs.clear()
            }

            // Ensure that cmd queue is unavailable
            try {
                runtime.executeAsync(TorCmd.Signal.NewNym)
                fail()
            } catch (e: IllegalStateException) {
                assertEquals("Tor is stopped or stopping", e.message)
            }
        }
    }
}
