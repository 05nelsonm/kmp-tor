/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet
import io.matthewnelson.kmp.tor.helper.TorTestHelper
import io.matthewnelson.kmp.tor.manager.common.exceptions.InterruptedException
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TorManagerIntegrationTest: TorTestHelper() {

    @Test
    fun givenMultipleControllerActions_whenStartStopOrRestarted_actionsAreInterrupted() = runBlocking {

        // queueing start here will wait for TorTestHelper start to finish, and will immediately
        // return success as it's already started.
        manager.start()

        // Restarting will give us enough time to load up commands to
        // be executed _after_ restart completes
        manager.restartQuietly()

        val getVersion = TorControlInfoGet.KeyWord.Status.Version.Current()
        val jobs = ArrayList<Job>(5)
        var failures = 0
        var successes = 0
        repeat(5) { index ->
            launch {
                val result = manager.infoGet(getVersion)
                result.onSuccess {
                    // Don't fail, as the first one may make it through before being interrupted
                    println("Controller Action $index was processed when it should have been interrupted")
                    successes++
                }
                result.onFailure { ex ->
                    assertTrue(ex is InterruptedException)
                    println("Job$index: ${ex.message}")
                    failures++
                }
            }.let { job ->
                jobs.add(job)
            }
        }

        delay(50L)

        // Cancellation of callers job should not
        // produce any results
        jobs[3].cancelAndJoin()

        manager.stop().getOrThrow()

        for (job in jobs) {
            job.cancelAndJoin()
        }

        assertEquals(5 - 1, failures)
        assertEquals(0, successes)

        Unit
    }
}
