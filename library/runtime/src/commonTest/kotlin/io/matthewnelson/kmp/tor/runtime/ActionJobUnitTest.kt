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

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class ActionJobUnitTest {

    private val handler = UncaughtException.Handler.THROW
    private val tempQueue = TorCtrl.Factory(debugger = null, handler = handler).tempQueue()

    @Test
    fun givenStart_whenImmediate_thenIsExecuting() {
        val job = ActionJob.StartJob(OnSuccess.noOp(), OnFailure.noOp(), handler, immediateExecute = true)
        assertEquals(EnqueuedJob.State.Executing, job.state)

        // Should not throw exception when invoked again (by RealTorRuntime)
        job.executing()
    }

    @Test
    fun givenStart_whenAttachOldQueue_thenThrowsException() {
        val job = ActionJob.StartJob(OnSuccess.noOp(), OnFailure.noOp(), handler, immediateExecute = true)
        assertFailsWith<IllegalArgumentException> { job.attachOldQueue(tempQueue) }
    }

    @Test
    fun givenNonExecutingJob_whenAttachOldQueue_thenThrowsException() {
        val job = ActionJob.StopJob(OnSuccess.noOp(), OnFailure.noOp(), handler)
        assertFailsWith<IllegalStateException> { job.attachOldQueue(tempQueue) }
    }

    @Test
    fun givenAlreadyAttachedOldQueue_whenAttachOldQueue_thenThrowsException() {
        val job = ActionJob.StopJob(OnSuccess.noOp(), OnFailure.noOp(), handler)
        job.executing()
        job.attachOldQueue(tempQueue)
        assertFailsWith<IllegalStateException> { job.attachOldQueue(tempQueue) }
    }

    @Test
    fun givenOldQueueAttached_onCompletion_thenIsDestroyed() {
        val job = ActionJob.StopJob(OnSuccess.noOp(), OnFailure.noOp(), handler)
        job.executing()
        job.attachOldQueue(tempQueue)
        assertFalse(tempQueue.isDestroyed())
        job.completion()
        assertTrue(tempQueue.isDestroyed())
    }
}
