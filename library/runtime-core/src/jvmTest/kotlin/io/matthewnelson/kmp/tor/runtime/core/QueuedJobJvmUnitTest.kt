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
package io.matthewnelson.kmp.tor.runtime.core

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.QueuedJobUnitTest.TestJob
import io.matthewnelson.kmp.tor.runtime.core.util.awaitSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class QueuedJobJvmUnitTest {

    @Test
    fun givenAwaitSync_whenThreadInterrupted_thenJobIsCancelledIfAble() = runTest {
        var invocationFailure = 0
        val job = TestJob(onFailure = {
            invocationFailure++
            assertIs<CancellationException>(it)
        })

        var threw: Throwable? = null

        val thread = Runnable {
            try {
                job.awaitSync<Unit>(
                    success = { null },
                    failure = { null },
                    cancellation = null,
                )
            } catch (t: Throwable) {
                threw = t
            }
        }.execute()

        withContext(Dispatchers.IO) {
            delay(25.milliseconds)
            thread.interrupt()
            delay(50.milliseconds)
        }

        assertIs<CancellationException>(threw)
        assertIs<InterruptedException>(threw!!.cause)
        assertEquals(1, invocationFailure)
        assertEquals(QueuedJob.State.Cancelled, job.state)
    }

    @Test
    fun givenAwaitSync_whenJobExecutingAndThreadInterrupted_thenDoesNotInterrupt() = runTest {
        var invocationCancel = 0
        var invocationFailure = 0
        var success: Unit? = null
        val job = TestJob(
            onFailure = { invocationFailure++ },
            onSuccess = { success = it },
            cancellation = { invocationCancel++ },
        )

        // non-cancellable state
        job.executing()

        var threw: Throwable? = null
        var result: Unit? = null

        val thread = Runnable {
            try {
                result = job.awaitSync(
                    success = { success },
                    failure = { null },
                    cancellation = null,
                )
            } catch (t: Throwable) {
                threw = t
            }
        }.execute()

        withContext(Dispatchers.IO) {
            delay(25.milliseconds)
            thread.interrupt()
            delay(50.milliseconds)
            job.completion()
            delay(50.milliseconds)
        }

        assertNull(threw)
        assertNotNull(success)
        assertNotNull(result)
        assertEquals(0, invocationCancel)
        assertEquals(0, invocationFailure)
    }

    private fun Runnable.execute(): Thread {
        val t = Thread(this)
        t.isDaemon = true
        t.priority = Thread.MAX_PRIORITY
        t.start()
        return t
    }
}
