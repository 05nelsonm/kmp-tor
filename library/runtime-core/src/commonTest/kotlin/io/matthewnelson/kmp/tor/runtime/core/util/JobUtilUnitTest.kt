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
package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJobUnitTest
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@Suppress("DEPRECATION_ERROR")
@OptIn(InternalKmpTorApi::class)
class JobUtilUnitTest {

    data object TestArgument: EnqueuedJob.Argument

    @Test
    fun givenAwaitAsync_whenJobExecuting_thenIsNonCancellable() = runTest {
        var testJob: EnqueuedJobUnitTest.TestJob? = null

        var coroutineCompletion: Unit? = null
        var coroutineError: Throwable? = null
        var coroutineInvocationCompletion = false

        var testJobInvocationCompletion = false

        val testLatch = Job(currentCoroutineContext().job)

        val coroutine = launch(CoroutineExceptionHandler { _, t -> coroutineError = t }) {
            coroutineCompletion = TestArgument.awaitAsync { _, onFailure, onSuccess ->
                EnqueuedJobUnitTest.TestJob(onFailure = onFailure, onSuccess = onSuccess)
                    .also { testJob = it }
            }

            // Keep alive to check assertions
            withContext(NonCancellable) { testLatch.join() }
        }

        coroutine.invokeOnCompletion {
            coroutineInvocationCompletion = true
        }

        withContext(Dispatchers.Default) {
            while (testJob == null) {
                delay(5.milliseconds)
            }
        }

        // non-cancellable
        testJob!!.executing()

        testJob!!.invokeOnCompletion {
            testJobInvocationCompletion = true
        }

        // begin coroutine cancellation
        coroutine.cancel()

        withContext(Dispatchers.Default) { delay(25.milliseconds) }

        try {
            assertTrue(testJob!!.isActive)
            assertFalse(testJob!!.isCompleting)
            assertFalse(coroutineInvocationCompletion)
            assertFalse(testJobInvocationCompletion)
        } catch (t: Throwable) {
            testLatch.cancel()
            throw t
        } finally {
            withContext(NonCancellable + Dispatchers.Default) {
                testJob!!.completion()
            }
        }

        withContext(Dispatchers.Default) { delay(25.milliseconds) }

        try {
            assertFalse(testJob!!.isActive)
            assertTrue(testJobInvocationCompletion)

            // All invoke on completion callbacks were all run
            assertFalse(testJob!!.isCompleting)

            // Coroutine should still be active until
            // testLatch completes
            assertFalse(coroutineInvocationCompletion)
            assertNull(coroutineError)

            // awaitAsync ignored cancellation and returned a value
            assertNotNull(coroutineCompletion)
        } finally {
            testLatch.complete()
        }

        coroutine.join()
    }

    @Test
    fun givenAwaitAsync_whenJobNotExecuting_thenIsCancelledWhenCoroutineIsCancelled() = runTest {
        var testJob: EnqueuedJobUnitTest.TestJob? = null

        var coroutineCompletion: Unit? = null
        var coroutineError: Throwable? = null
        var coroutineInvocationCompletion = false

        var testJobInvocationCompletion = false

        val coroutine = launch(CoroutineExceptionHandler { _, t -> coroutineError = t }) {
            coroutineCompletion = TestArgument.awaitAsync { _, onFailure, onSuccess ->
                EnqueuedJobUnitTest.TestJob(onFailure = onFailure, onSuccess = onSuccess)
                    .also { testJob = it }
            }
        }

        coroutine.invokeOnCompletion {
            coroutineInvocationCompletion = true
        }

        withContext(Dispatchers.Default) {
            while (testJob == null) {
                delay(5.milliseconds)
            }
        }

//        // non-cancellable
//        testJob!!.executing()

        testJob!!.invokeOnCompletion {
            testJobInvocationCompletion = true
        }

        // begin coroutine cancellation
        coroutine.cancel()

        withContext(Dispatchers.Default) { delay(25.milliseconds) }

        try {
            assertTrue(testJob!!.isCancelled)
            assertTrue(coroutine.isCancelled)
            assertTrue(coroutineInvocationCompletion)
            assertTrue(testJobInvocationCompletion)

            // Was CancellationException
            assertNull(coroutineError)

            // awaitAsync threw CancellationException
            assertNull(coroutineCompletion)

            assertTrue(coroutine.isCompleted)
        } finally {
            testJob!!.completion()
        }

        coroutine.join()
    }

    @Test
    fun givenAwaitAsync_whenJobError_thenThrowsTheError() = runTest {
        var testJob: EnqueuedJobUnitTest.TestJob? = null

        var coroutineCompletion: Unit? = null
        var coroutineError: InterruptedException? = null
        var coroutineInvocationCompletion = false

        var testJobInvocationCompletion = false

        val testLatch = Job(currentCoroutineContext().job)

        val coroutine = launch {
            try {
                coroutineCompletion = TestArgument.awaitAsync { _, onFailure, onSuccess ->
                    EnqueuedJobUnitTest.TestJob(onFailure = onFailure, onSuccess = onSuccess)
                        .also { testJob = it }
                }
            } catch (e: InterruptedException) {
                coroutineError = e
            }

            // Keep alive to check assertions
            withContext(NonCancellable) { testLatch.join() }
        }

        coroutine.invokeOnCompletion {
            coroutineInvocationCompletion = true
        }

        withContext(Dispatchers.Default) {
            while (testJob == null) {
                delay(5.milliseconds)
            }
        }

        // non-cancellable
        testJob!!.executing()

        testJob!!.invokeOnCompletion {
            testJobInvocationCompletion = true
        }

        // begin coroutine cancellation
        coroutine.cancel()

        withContext(Dispatchers.Default) { delay(25.milliseconds) }

        try {
            assertTrue(testJob!!.isActive)
            assertFalse(testJob!!.isCompleting)
            assertFalse(coroutineInvocationCompletion)
            assertFalse(testJobInvocationCompletion)
        } catch (t: Throwable) {
            testLatch.cancel()
            throw t
        } finally {
            withContext(NonCancellable + Dispatchers.Default) {
                testJob!!.error(InterruptedException())
            }
        }

        withContext(Dispatchers.Default) { delay(25.milliseconds) }

        try {
            assertFalse(testJob!!.isActive)
            assertTrue(testJobInvocationCompletion)

            // All invoke on completion callbacks were all run
            assertFalse(testJob!!.isCompleting)

            // Coroutine should still be active until
            // testLatch completes
            assertFalse(coroutineInvocationCompletion)
            assertFalse(coroutine.isCompleted)


            // awaitAsync threw exception instead of returning
            assertNull(coroutineCompletion)
            assertNotNull(coroutineError)
        } finally {
            testLatch.complete()
        }

        coroutine.join()
    }
}
