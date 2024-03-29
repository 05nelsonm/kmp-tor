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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TempTorCmdQueue
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.AbstractTorCtrlUnitTest.TestCtrl
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class TempTorCmdQueueUnitTest {

    private val ctrl = TestCtrl()
    private val queue = TempTorCmdQueue(UncaughtException.Handler.THROW)

    @Test
    fun givenEnqueuedJob_whenAttach_thenIsTransferred() {
        var invocationFailure = 0
        queue.enqueue(TorCmd.Signal.Dump, { invocationFailure++ }, {})

        queue.attach(ctrl)
        assertNotNull(queue.connection)
        queue.destroy()
        assertNotNull(queue.connection)

        assertEquals(0, invocationFailure)
        ctrl.destroy()
        assertEquals(1, invocationFailure)
    }

    @Test
    fun givenAttachedCtrl_whenCtrlDestroyed_thenQueueIsDestroyed() {
        queue.attach(ctrl)
        ctrl.destroy()
        assertTrue(queue.isDestroyed())
    }

    @Test
    fun givenDestroyed_whenAttachedCtrlNotDestroyed_thenDelegatesQueueingToCtrl() {
        queue.attach(ctrl)
        assertNotNull(queue.connection)
        queue.destroy()

        // ctrl is attached and not destroyed
        assertFalse(queue.isDestroyed())

        // should not throw IllegalStateException because attached
        // connection is not destroyed.
        val job = queue.enqueue(TorCmd.Signal.Dump, {}, {})
        ctrl.destroy()
        assertEquals(QueuedJob.State.Cancelled, job.state)
    }

    @Test
    fun givenQueuedJobs_whenNoAttached_thenCancelsOnDestroy() {
        val job = queue.enqueue(TorCmd.Signal.Dump, {}, {})
        queue.destroy()
        assertEquals(QueuedJob.State.Cancelled, job.state)
        assertTrue(queue.isDestroyed())
    }

    @Test
    fun givenDestroyed_whenAttach_thenThrowsIllegalStateException() {
        queue.destroy()
        assertFailsWith<IllegalStateException> { queue.attach(ctrl) }
    }
}
