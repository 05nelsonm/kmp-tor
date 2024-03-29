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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class AbstractTorCmdQueueUnitTest {

    private class TestQueue(
        handler: UncaughtException.Handler = UncaughtException.Handler.THROW,
    ): AbstractTorCmdQueue(null, emptySet(), handler) {

        var invocationStart = 0
            private set

        override fun startProcessor() { invocationStart++ }
        override fun destroy() { onDestroy() }

        @Suppress("UNCHECKED_CAST")
        fun dequeueNext(): TorCmdJob<Unit>? = dequeueNextOrNull() as? TorCmdJob<Unit>

        fun processAll() {
            var job: TorCmdJob<Unit>? = dequeueNext()

            while (job != null) {
                assertEquals(QueuedJob.State.Executing, job.state)
                job.completion(Unit)
                job = dequeueNext()
            }
        }
    }

    @Test
    fun givenShutdownOrHalt_whenOtherCommands_thenAllAreCancelled() {
        listOf(
            TorCmd.Signal.Halt,
            TorCmd.Signal.Shutdown,
        ).forEach { signal ->
            val queue = TestQueue()
            var invocationSuccess = 0
            var invocationFailure = 0

            val onFailure = OnFailure { invocationFailure++ }
            val onSuccess = OnSuccess<Unit> { invocationSuccess++ }

            val jobs = mutableListOf<QueuedJob>()

            repeat(5) {
                val job = queue.enqueue(
                    TorCmd.Signal.Dump,
                    onFailure,
                    onSuccess,
                )
                jobs.add(job)
            }

            jobs.forEach { job -> assertEquals(QueuedJob.State.Enqueued, job.state) }

            assertEquals(jobs.size, queue.invocationStart)

            val signalJob = queue.enqueue(signal, onFailure, onSuccess)
            assertEquals(0, invocationSuccess)
            assertEquals(jobs.size + 1, queue.invocationStart)

            // Should not have cancelled yet b/c test processor is not a thing
            jobs.forEach { job -> assertEquals(QueuedJob.State.Enqueued, job.state) }
            assertEquals(0, invocationFailure)

            queue.processAll()
            assertEquals(1, invocationSuccess)
            assertEquals(QueuedJob.State.Success, signalJob.state)

            // cancellations are performed when dequeueNextOrNull is called
            // which, in the test, is when processAll is invoked
            jobs.forEach { job -> assertEquals(QueuedJob.State.Cancelled, job.state) }
            assertEquals(jobs.size, invocationFailure)
        }
    }

    @Test
    fun givenTemporaryQueue_whenTransferred_thenTransfersUnprivilegedCommands() {
        val tempQueue = ArrayList<TorCmdJob<*>>(5)
        var invocationSuccess = 0
        var invocationFailure = 0

        val onFailure = OnFailure { invocationFailure++ }
        val onSuccess = OnSuccess<Unit> { invocationSuccess++ }

        repeat(4) {
            val job = TorCmdJob.of(
                TorCmd.Signal.Dump,
                onSuccess,
                onFailure,
                UncaughtException.Handler.THROW
            )
            tempQueue.add(job)
        }

        // TorCmd.Privileged type
        tempQueue.add(TorCmdJob.of(
            TorCmd.Ownership.Take,
            onSuccess,
            onFailure,
            UncaughtException.Handler.THROW
        ))

        val queue = TestQueue()
        queue.transferAllUnprivileged(tempQueue)
        assertEquals(1, tempQueue.size)
        assertEquals(1, queue.invocationStart)

        // Verify they were transferred
        queue.processAll()
        assertEquals(4, invocationSuccess)
        assertEquals(0, invocationFailure)
    }

    @Test
    fun givenOnDestroy_whenQueuedJobs_thenAllAreCancelled() {
        val queue = TestQueue()
        var invocationSuccess = 0
        var invocationFailure = 0

        val onFailure = OnFailure {
            invocationFailure++
            assertIs<CancellationException>(it)

            assertFailsWith<IllegalStateException> {
                queue.enqueue(TorCmd.Signal.Dump, {}, {})
            }

            // Verify exception suppression functionality such that
            // all the things still execute and then propagate single
            // exception (with suppressed exceptions) to handler.
            throw IOException()
        }
        val onSuccess = OnSuccess<Unit> { invocationSuccess++ }

        // Issuance of Halt will transfer all current queue
        // jobs to a cancellation queue which is handled on
        // next invocation of dequeueNextOrNull. In this case,
        // we are not invoking that but are checking that
        // those still get cancelled when onDestroy happens
        val commands = listOf(
            TorCmd.Signal.Dump,
            TorCmd.Signal.Dump,
            TorCmd.Signal.Halt,
            TorCmd.Signal.Dump,
            TorCmd.Signal.Halt,
            TorCmd.Signal.Dump,
            TorCmd.Signal.Dump,
        )
        commands.forEach { command ->
            when (command) {
                is TorCmd.Privileged<Unit> -> queue.enqueue(command, onFailure, onSuccess)
                is TorCmd.Unprivileged<Unit> -> queue.enqueue(command, onFailure, onSuccess)
            }
        }

        try {
            queue.destroy()
            fail()
        } catch (e: UncaughtException) {
            // pass
            assertIs<IOException>(e.cause)

            val suppressed = e.suppressedExceptions
            assertEquals(commands.size - 1, suppressed.size)
            suppressed.forEach { t ->
                assertIs<UncaughtException>(t)
                assertIs<IOException>(t.cause)
            }
        }

        assertTrue(queue.isDestroyed())

        // This indicates that the 2 separate queues, both of
        // which encountered exceptions, were still handled
        // and the exception thrown after.
        assertEquals(commands.size, invocationFailure)
        assertEquals(0, invocationSuccess)

        // Unprivileged
        assertFailsWith<IllegalStateException> { queue.enqueue(TorCmd.Signal.Dump, onFailure, onSuccess) }
        // Privileged
        assertFailsWith<IllegalStateException> { queue.enqueue(TorCmd.Signal.Halt, onFailure, onSuccess) }
    }
}
