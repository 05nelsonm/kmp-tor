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

import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlin.test.Test
import kotlin.test.assertEquals

class AbstractTorCmdQueueUnitTest {

    private class TestQueue: AbstractTorCmdQueue(null, emptySet(), UncaughtException.Handler.THROW) {

        var invocationStart = 0
            private set

        override fun startProcessor() { invocationStart++ }
        override fun destroy() { onDestroy() }

        @Suppress("UNCHECKED_CAST")
        fun processAll() {
            var job: TorCmdJob<Unit>? = dequeueNextOrNull() as? TorCmdJob<Unit>

            while (job != null) {
                assertEquals(QueuedJob.State.Executing, job.state)
                job.completion(Unit)
                job = dequeueNextOrNull() as? TorCmdJob<Unit>
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
}
