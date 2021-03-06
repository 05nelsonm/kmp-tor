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
package io.matthewnelson.kmp.tor.ext.callback.manager

import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet
import io.matthewnelson.kmp.tor.ext.callback.common.Task
import io.matthewnelson.kmp.tor.helpers.TorTestHelper
import io.matthewnelson.kmp.tor.manager.common.exceptions.InterruptedException
import kotlinx.coroutines.*
import kotlin.coroutines.resumeWithException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class CallbackTorManagerIntegrationTest: TorTestHelper() {

    @Test
    fun givenMultipleControllerActions_whenStartStopOrRestarted_actionsAreInterrupted() = runBlocking {
        val torManager = CallbackTorManager(manager) { t ->
            t.printStackTrace()
        }


        suspendCancellableCoroutine<Any?> { continuation ->
            torManager.start(
                { t ->
                    continuation.resumeWithException(t)
                },
                { success ->
                    continuation.resume(success, null)
                }
            )
        }

        torManager.restartQuietly()

        val getVersion = TorControlInfoGet.KeyWord.Status.Version.Current()
        val tasks = ArrayList<Task>(5)
        val jobs = ArrayList<Job>(5)
        var failures = 0
        repeat(5) { index ->
            launch {
                suspendCancellableCoroutine<String> { continuation ->
                    torManager.infoGet(
                        getVersion,
                        { t ->
                            assertTrue(t is InterruptedException)
                            println("Job$index: ${t.message}")
                            failures++
                            continuation.cancel()
                        },
                        { success ->
                            // Don't fail, as the first one may make it through before being interrupted
                            println("Controller Action $index was processed when it should have been interrupted")
                            continuation.resume(success, null)
                        }
                    ).let { task ->
                        tasks.add(task)
                    }
                }
            }.let { job ->
                jobs.add(job)
            }
        }



        delay(50L)

        // Cancellation of the task should not
        // produce any results
        tasks[3].cancel()

        suspendCancellableCoroutine<Any?> { continuation ->
            torManager.stop(
                { t ->
                    continuation.resumeWithException(t)
                },
                { success ->
                    continuation.resume(success, null)
                }
            )
        }

        for (job in jobs) {
            job.cancelAndJoin()
        }

        for (task in tasks) {
            assertFalse(task.isActive)
        }

        assertTrue(failures > 0)
        Unit
    }
}
