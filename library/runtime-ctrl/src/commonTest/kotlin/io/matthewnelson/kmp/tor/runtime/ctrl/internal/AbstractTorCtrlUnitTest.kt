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
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*

class AbstractTorCtrlUnitTest {

    internal class TestCtrl(
        handler: UncaughtException.Handler = UncaughtException.Handler.THROW
    ): AbstractTorCtrl(null, emptySet(), OnEvent.Executor.Immediate, handler) {
        override fun startProcessor() {
            // TODO("Not yet implemented")
        }

        override fun destroy() { onDestroy() }
    }

    @Test
    fun givenOnDestroy_whenMultipleExceptions_thenAreSuppressed() {
        var invocationHandler = 0
        val handler = UncaughtException.Handler {
            invocationHandler++
            throw it
        }

        val ctrl = TestCtrl(handler)
        val job = ctrl.enqueue(TorCmd.Signal.Dump, onFailure = { throw it }) {}

        var invocationDestroy = 0
        ctrl.invokeOnDestroy {
            invocationDestroy++
            assertTrue(it.isDestroyed())

            // interrupt should occur before completion handles
            assertEquals(QueuedJob.State.Error, job.state)

            // check that destroy callbacks variable has
            // been de-referenced and cannot add anymore
            var immediate = false
            assertEquals(Disposable.noOp(), ctrl.invokeOnDestroy { immediate = true })
            assertTrue(immediate)

            // multiple exceptions suppressed into single
            throw IOException()
        }
        ctrl.invokeOnDestroy { invocationDestroy++ }

        try {
            ctrl.destroy()
        } catch (e: UncaughtException) {
            // pass
            assertIs<InterruptedException>(e.cause)

            val suppressed = e.suppressedExceptions
            assertEquals(1, suppressed.size)
            assertIs<UncaughtException>(suppressed.first())
            assertIs<IOException>(suppressed.first().cause)
        }

        assertEquals(2, invocationDestroy)
        assertEquals(3, invocationHandler)

        // check handler does not leak on immediate
        // invocation and Handler.THROW instance is
        // utilized.
        assertFailsWith<UncaughtException> {
            ctrl.invokeOnDestroy { throw IOException() }
        }
        assertEquals(3, invocationHandler)
    }

    @Test
    fun givenInvokeOnDestroy_whenDisposed_thenIsRemoved() {
        val ctrl = TestCtrl()

        var invocationDestroy = 0
        ctrl.invokeOnDestroy { invocationDestroy++ }
        ctrl.invokeOnDestroy { invocationDestroy++ }.invoke()
        ctrl.destroy()
        assertEquals(1, invocationDestroy)
    }

    @Test
    fun givenInvokeOnDestroy_whenSameInstance_thenIsNotAdded() {
        val ctrl = TestCtrl()

        var invocationDestroy = 0
        val cb = ItBlock<TorCtrl> { invocationDestroy++ }
        val d1 = ctrl.invokeOnDestroy(cb)
        assertNotEquals(Disposable.noOp(), d1)

        val d2 = ctrl.invokeOnDestroy(cb)
        assertEquals(Disposable.noOp(), d2)

        ctrl.destroy()
        assertEquals(1, invocationDestroy)

        // posterity, nothing should happen like
        // exceptions or anything...
        d1.invoke()
    }
}
