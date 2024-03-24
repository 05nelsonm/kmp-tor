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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class TorJobUnitTest {

    @Test
    fun givenInstantiation_whenInitialState_thenIsEnqueued() {
        assertEquals(QueuedJob.State.Enqueued, TestJob().state)
    }

    @Test
    fun givenCancellation_whenCalled_thenLeavesJobUnusable() {
        var invocationFail = 0
        var invocationCancel = 0

        val job = TestJob(
            cancellation = { t ->
                invocationCancel++
                assertIs<IllegalArgumentException>(t)
            },
            onFailure = { t ->
                invocationFail++
                assertIs<CancellationException>(t)
            }
        )

        assertTrue(job.isActive)
        job.cancel(IllegalArgumentException())
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)
        assertEquals(QueuedJob.State.Cancelled, job.state)
        assertFalse(job.isActive)

        job.cancel(Throwable())
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)

        job.error(Throwable())
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)

        assertFailsWith<IllegalStateException> { job.executing() }
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)

        assertFailsWith<IllegalStateException> { job.completion {  } }
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)
    }

    @Test
    fun givenStateExecuting_whenCancel_thenIgnores() {
        val job = TestJob()

        job.executing()
        assertEquals(QueuedJob.State.Executing, job.state)
        assertTrue(job.isActive)

        job.cancel(null)
        assertEquals(QueuedJob.State.Executing, job.state)
        assertTrue(job.isActive)

        job.completion {  }
        assertEquals(QueuedJob.State.Completed, job.state)
        assertFalse(job.isActive)
    }

    private class TestJob(
        name: String = "",
        private val cancellation: (cause: Throwable?) -> Unit = {},
        onFailure: ItBlock<Throwable>? = null,
    ): QueuedJob(name, onFailure) {
        override fun onCancellation(cause: Throwable?) {
            cancellation(cause)
        }

        public fun error(cause: Throwable) { onError(cause) }
        @Throws(IllegalStateException::class)
        public fun executing() { onExecuting() }
        @Throws(IllegalStateException::class)
        public fun <T: Any?> completion(block: () -> T): T = onCompletion(block)
    }
}
