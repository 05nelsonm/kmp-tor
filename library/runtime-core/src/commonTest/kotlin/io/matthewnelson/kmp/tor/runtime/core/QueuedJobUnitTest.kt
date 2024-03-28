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
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class QueuedJobUnitTest {

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
                assertNull(t)
            },
            onFailure = { t ->
                invocationFail++
                assertIs<CancellationException>(t)
            }
        )

        assertTrue(job.isActive)
        assertTrue(job.cancel(null))
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)
        assertEquals(QueuedJob.State.Cancelled, job.state)
        assertFalse(job.isActive)

        assertFalse(job.cancel(CancellationException()))
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)

        job.error(Throwable())
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)

        assertFailsWith<IllegalStateException> { job.executing() }
        assertEquals(1, invocationCancel)
        assertEquals(1, invocationFail)

        job.completion()
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

        job.completion()
        assertEquals(QueuedJob.State.Success, job.state)
        assertFalse(job.isActive)
    }

    @Test
    fun givenSuccess_whenIsCompleting_thenJobOperatesAsExpected() {
        var job: TestJob? = null
        var invocationCompletion = 0
        var invocationSuccess = 0
        var invocationFailure = 0

        val exceptions = mutableListOf<UncaughtException>()
        val handler = UncaughtException.Handler { exceptions.add(it) }

        job = TestJob(
            onFailure = {
                invocationFailure++
            },
            onSuccess = {
                invocationSuccess++
                assertNotEquals(QueuedJob.State.Success, job!!.state)
                assertTrue(job!!.isActive)
                assertTrue(job!!.isCompleting)

                // Should still be able to add completion invocations
                val disposable = job!!.invokeOnCompletion {
                    invocationCompletion++

                    assertTrue(job!!.isCompleting)

                    // Should be caught by handler
                    fail()
                }
                assertNotEquals(Disposable.NOOP, disposable)

                // isCompleting is true right here so the following
                // invocations should all be ignored, b/c some
                // other completion function is being executed.
                assertFalse(job!!.cancel(null))
                job!!.error(Throwable())
                assertFailsWith<IllegalStateException> { job!!.executing() }

                // In case completion _actually_ triggers,
                // this assertion will inhibit recursive
                // execution
                assertEquals(1, invocationSuccess)
                job!!.completion()

                // Should be caught by handler and still complete
                throw IOException()
            },
            handler = handler,
        )

        job.invokeOnCompletion {
            assertNull(it)
            invocationCompletion++
        }

        job.completion()
        assertEquals(2, invocationCompletion)
        assertEquals(1, invocationSuccess)
        assertEquals(0, invocationFailure)
        assertEquals(1, exceptions.size)
        assertIs<IOException>(exceptions.first().cause)

        assertEquals(QueuedJob.State.Success, job.state)
    }

    @Test
    fun givenInvokeOnCompletion_whenDisposed_thenDoesNotExecute() {
        var invocations = 0
        val job = TestJob()
        job.invokeOnCompletion { invocations++ }
        val disposable = job.invokeOnCompletion { invocations++ }
        disposable()
        job.cancel(null)
        assertEquals(1, invocations)
    }

    @Test
    fun givenInvokeOnCompletion_whenCancelled_thenIsInvoked() {
        var invocationFailure = false
        val job = TestJob(onFailure = { invocationFailure = true })

        var invocationCompletion = false
        job.invokeOnCompletion {
            invocationCompletion = true
            // Ensure cancellation was dispatched before
            // invoking all completion callbacks
            assertTrue(invocationFailure)

            // completed by cancellation
            assertNotNull(it)
            assertTrue(job.isCompleting)
        }

        job.cancel(null)
        assertFalse(job.isCompleting)
        assertTrue(invocationCompletion)
    }

    @Test
    fun givenInvokeOnCompletion_whenCompleted_thenIsInvoked() {
        var invocationSuccess = false
        val job = TestJob(onSuccess = { invocationSuccess = true })

        var invocationCompletion = false
        job.invokeOnCompletion {
            invocationCompletion = true
            // Ensure success was dispatched before
            // invoking all completion callbacks
            assertTrue(invocationSuccess)
        }

        job.completion()
        assertTrue(invocationCompletion)
    }

    @Test
    fun givenInvokeOnCompletion_whenErrored_thenIsInvoked() {
        var invocationFailure = 0
        val job = TestJob(onFailure = { invocationFailure++ })

        var invocationCompletion = 0
        val cb = ItBlock<CancellationException?> {
            invocationCompletion++
            // Ensure error was dispatched before
            // invoking all completion callbacks
            assertEquals(1, invocationFailure)

            // completion by error, not by cancellation
            assertNull(it)
        }

        // Ensure same callback cannot be added
        // more than once.
        job.invokeOnCompletion(cb)

        // Should return Disposable.NOOP
        job.invokeOnCompletion(cb)()
        job.invokeOnCompletion(cb)()

        job.error(Throwable())
        assertEquals(1, invocationCompletion)
    }

    @Test
    fun givenInvokeOnCompletion_whenJobCompleted_thenInvokesImmediately() {
        val job = TestJob()
        job.completion()
        var invocationCompletion = false
        job.invokeOnCompletion { invocationCompletion = true }
        assertTrue(invocationCompletion)
    }

    @Test
    fun givenInvokeOnCompletion_whenThrows_thenIsDelegatedToHandler() {
        val exceptions = mutableListOf<UncaughtException>()
        val handler = UncaughtException.Handler { t -> exceptions.add(t) }
        val job = TestJob(handler = handler)
        job.invokeOnCompletion { fail() }

        job.completion()
        assertEquals(1, exceptions.size)

        // Check that immediate invocation utilizes
        // UncaughtException.Handler.THROW and not the
        // one passed to TestJob (do not leak handler after
        // completion).
        assertFailsWith<UncaughtException>{ job.invokeOnCompletion { fail() } }
        assertEquals(1, exceptions.size)
    }

    public class TestJob(
        name: String = "",
        private val cancellation: (cause: CancellationException?) -> Unit = {},
        onFailure: OnFailure = OnFailure {},
        private val onSuccess: OnSuccess<Unit>? = null,
        handler: UncaughtException.Handler = UncaughtException.Handler.THROW,
    ): QueuedJob(name, onFailure, handler) {
        override fun onCancellation(cause: CancellationException?) {
            cancellation(cause)
        }

        public fun error(cause: Throwable) { onError(cause) {} }
        @Throws(IllegalStateException::class)
        public fun executing() { onExecuting() }
        public fun completion() { onCompletion(Unit, withLock = { onSuccess }) }
    }
}
