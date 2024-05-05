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

import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.TorCmdJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TorCmdInterceptorUnitTest {

    @Test
    fun givenIntercept_whenCorrectType_thenInvokesCallback() {
        var invocationIntercept = 0
        val interceptor = TorCmdInterceptor.intercept<TorCmd.Signal.Dump> { _, cmd ->
            invocationIntercept++
            cmd
        }

        interceptor.invoke(newJob(TorCmd.Authenticate()))
        assertEquals(0, invocationIntercept)

        interceptor.invoke(newJob(TorCmd.Signal.Dump))
        assertEquals(1, invocationIntercept)

        interceptor.invoke(newJob(TorCmd.Signal.Halt))
        assertEquals(1, invocationIntercept)

        interceptor.invoke(newJob(TorCmd.Signal.Dump))
        assertEquals(2, invocationIntercept)
    }

    @Test
    fun givenIntercept_whenJobNotExecuting_thenDoesNotInvokeCallback() {
        var invocationIntercept = 0
        val interceptor = TorCmdInterceptor.intercept<TorCmd.Signal.Dump> { _, cmd ->
            invocationIntercept++
            cmd
        }

        interceptor.invoke(newJob(TorCmd.Signal.Dump))
        assertEquals(1, invocationIntercept)

        interceptor.invoke(newJob(TorCmd.Signal.Dump, setExecuting = false))
        assertEquals(1, invocationIntercept)
    }

    @Test
    fun givenIntercept_whenReturnedCommandIsNotSameClassType_thenReturnsNull() {
        var invocationIntercept = 0

        val setEvents = TorCmd.SetEvents()
        val interceptor = TorCmdInterceptor.intercept<TorCmd.Unprivileged<*>> { _, _ ->
            invocationIntercept++
            setEvents
        }

        assertNull(interceptor.invoke(newJob(TorCmd.Signal.Dump)))
        assertEquals(1, invocationIntercept)

        assertNull(interceptor.invoke(newJob(setEvents)))
        assertEquals(2, invocationIntercept)

        assertNull(interceptor.invoke(newJob(TorCmd.Authenticate())))
        assertEquals(2, invocationIntercept)

        assertNotNull(interceptor.invoke(newJob(TorCmd.SetEvents(TorEvent.NOTICE))))
        assertEquals(3, invocationIntercept)
    }

    @Test
    fun givenIntercept_whenCommandTheSameInstance_thenReturnsNull() {
        var invocationIntercept = 0
        val interceptor = TorCmdInterceptor.intercept<TorCmd.Authenticate> { job, cmd ->
            if (invocationIntercept++ == 1) {
                TorCmd.Authenticate()
            } else {
                cmd
            }
        }

        val job = newJob(TorCmd.Authenticate())
        assertNull(interceptor.invoke(job))
        assertEquals(1, invocationIntercept)

        assertNotNull(interceptor.invoke(job))
        assertEquals(2, invocationIntercept)
    }

    private fun newJob(
        cmd: TorCmd<*>,
        setExecuting: Boolean = true,
    ): TorCmdJob<*> = TorCmdJob.of(
        cmd,
        OnSuccess.noOp(),
        OnFailure.noOp(),
        UncaughtException.Handler.THROW,
    ).apply { if (setExecuting) executing() }
}
