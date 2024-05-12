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
package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.AbstractTorCtrlUnitTest.TestCtrl
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.TorCmdJob
import kotlin.test.*

class TempTorCmdQueueUnitTest {

    private lateinit var ctrl: TestCtrl
    private lateinit var queue: TempTorCmdQueue

    @BeforeTest
    fun setup() {
        ctrl = TestCtrl()
        queue = TempTorCmdQueue.of(UncaughtException.Handler.THROW)
    }

    @AfterTest
    fun tearDown() {
        ctrl.destroy()
        queue.destroy()
    }

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
        assertEquals(EnqueuedJob.State.Error, job.state)
    }

    @Test
    fun givenQueuedJobs_whenNoAttached_thenInterruptsOnDestroy() {
        val job = queue.enqueue(TorCmd.Signal.Dump, { t -> assertIs<InterruptedException>(t) }, {})
        queue.destroy()
        assertEquals(EnqueuedJob.State.Error, job.state)
        assertTrue(queue.isDestroyed())
    }

    @Test
    fun givenDestroyed_whenAttach_thenThrowsIllegalStateException() {
        queue.destroy()
        assertFailsWith<IllegalStateException> { queue.attach(ctrl) }
    }

    @Test
    fun givenDestroyed_whenEnqueueNoAttached_thenReturnsErrorJob() {
        queue.destroy()
        assertIsNot<TorCmdJob<Reply.Success.OK>>(queue.enqueue(TorCmd.Signal.Dump, {}, {}))
    }
}
