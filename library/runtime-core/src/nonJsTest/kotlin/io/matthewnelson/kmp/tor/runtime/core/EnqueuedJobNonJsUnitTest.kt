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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJobUnitTest.TestJob
import io.matthewnelson.kmp.tor.runtime.core.util.awaitSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@OptIn(InternalKmpTorApi::class)
class EnqueuedJobNonJsUnitTest {

    @Test
    fun givenAwaitSync_whenCancellationLambdaThrows_thenIsWrappedInCancellationException() {
        var invocationCancel = 0
        var invocationFailure = 0
        val job = TestJob(onFailure = {
            invocationFailure++
            assertIs<CancellationException>(it)
        })
        val mark = TimeSource.Monotonic.markNow()

        var threw: Throwable? = null

        try {
            job.awaitSync<Unit>(
                success = { null },
                failure = { null },
                cancellation = { if (++invocationCancel > 2) throw IOException() else null }
            )
        } catch (t: Throwable) {
            threw = t
        }

        assertEquals(1, invocationFailure)
        assertEquals(3, invocationCancel)
        assertIs<CancellationException>(threw)
        assertIs<IOException>(threw.cause)
        assertEquals(EnqueuedJob.State.Cancelled, job.state)

        // should have blocked
        assertTrue(mark.elapsedNow() > 15.milliseconds)
    }

    @Test
    fun givenAwaitSync_whenJobAlreadyCancelled_thenThrowsCancellationExceptionImmediately() {
        val job = TestJob()
        job.cancel(CancellationException(IOException()))

        var invocationSuccess = 0
        var invocationFailure = 0

        try {
            job.awaitSync<Unit>(
                success = { invocationSuccess++; null },
                failure = { invocationFailure++; null },
                cancellation = null,
            )
            fail()
        } catch (e: CancellationException) {
            assertIs<IOException>(e.cause)
        }

        // Should not have invoked success callback at all
        // (while loop did not execute, nor did check after
        // because state was not Success)
        assertEquals(0, invocationSuccess)

        // Should have pulled cancellation exception from job
        assertEquals(0, invocationFailure)
    }

    @Test
    fun givenAwaitSync_whenJobCompletionButSuccessNull_thenThrowsIllegalStateException() = runTest {
        val job = TestJob()

        launch(Dispatchers.IO) {
            job.executing()
            delay(20.milliseconds)
            job.completion()
        }

        var invocationFailure = 0
        var invocationSuccess = 0

        try {
            job.awaitSync<Unit>(
                success = { invocationSuccess++; null },
                failure = { invocationFailure++; null },
                cancellation = null
            )
            fail()
        } catch (e: IllegalStateException) {
            // pass
        }

        assertTrue(invocationFailure > 0)
        assertTrue(invocationSuccess > 0)
    }

    @Test
    fun givenAwaitSync_whenJobCompletion_thenReturnsSuccessfully() = runTest {
        var success: Unit? = null
        val job = TestJob(onSuccess = { success = it })

        launch(Dispatchers.IO) {
            job.executing()
            delay(20.milliseconds)
            job.completion()
        }

        var invocationFailure = 0
        var invocationSuccess = 0

        job.awaitSync(
            success = { invocationSuccess++; success },
            failure = { invocationFailure++; null },
            cancellation = null
        )

        assertTrue(invocationFailure > 0)
        assertTrue(invocationSuccess > 0)
    }
}
