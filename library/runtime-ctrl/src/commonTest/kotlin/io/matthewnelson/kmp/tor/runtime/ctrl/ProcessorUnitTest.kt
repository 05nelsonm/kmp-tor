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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.core.util.findNextAvailableAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class ProcessorUnitTest {

    @Test
    fun givenCommands_whenMultiple_thenSingleProcessorUtilized() = runTest {
        val lockStarts = SynchronizedObject()
        val lockIntercept = SynchronizedObject()
        val lockSuccess = SynchronizedObject()

        var processorStarts = 0
        var invocationIntercept = 0
        var invocationSuccess = 0

        val factory = TorCtrl.Factory(
            interceptors = setOf(TorCmdInterceptor.intercept<TorCmd.Signal.Heartbeat> { _, cmd ->
                synchronized(lockIntercept) { invocationIntercept++ }
                cmd
            }),
            debugger = { log ->
//                println(log)
                if (log.contains("Processor Started")) {
                    synchronized(lockStarts) { processorStarts++ }
                }
            },
            handler = UncaughtException.Handler.THROW,
        )
        val host = LocalHost.IPv4.resolve()
        val port = 9355.toPortEphemeral().findNextAvailableAsync(1_000, LocalHost.IPv4)
        val address = IPSocketAddress(host, port)

        withContext(Dispatchers.Default) { delay(350.milliseconds) }

        val process = TestUtils.startTor(address.toString())

        var threw: Throwable? = null

        try {
            val ctrl = factory.connectAsync(address)

            val onFailure = OnFailure { threw = it }
            val onSuccess = OnSuccess<Reply.Success.OK> { synchronized(lockSuccess) { invocationSuccess++ } }

            ctrl.enqueue(TorCmd.Authenticate(TestUtils.AUTH_PASS), onFailure, onSuccess)
            ctrl.enqueue(TorCmd.SetEvents(TorEvent.entries()), onFailure, onSuccess)

            repeat(100) {
                ctrl.enqueue(TorCmd.Signal.Heartbeat, onFailure, onSuccess)
            }

            // Suspends test until non-suspending complete
            ctrl.executeAsync(TorCmd.Signal.Dump)
            synchronized(lockSuccess) { invocationSuccess++ }

            // Ensure that another processor coroutine started
            ctrl.executeAsync(TorCmd.Signal.ClearDnsCache)
            synchronized(lockSuccess) { invocationSuccess++ }
        } catch (t: Throwable) {
            threw = t
        } finally {
            process.destroy()
        }

        withContext(Dispatchers.Default) { delay(350.milliseconds) }

        threw?.let { throw it }

        // All commands for our test executed successfully
        assertEquals(104, invocationSuccess)
        assertEquals(100, invocationIntercept)

        // Ensure that given our flurry of commands, a single processor
        // coroutine was started to handle them all.
        synchronized(lockStarts) {
            synchronized(lockSuccess) {
                // Test logic is sound
                assertTrue(processorStarts > 0)

                // Simply need to know if the processor handled multiple
                // commands when they were available, and other startProcessor
                // calls were ignored (b/c was already looping). Cannot utilize
                // a hard number because test will be flaky.
                assertTrue(processorStarts < invocationSuccess)
            }
        }
    }
}
