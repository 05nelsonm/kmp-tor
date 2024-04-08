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
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.core.util.findAvailableAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class ProcessorUnitTest {

    @Test
    fun givenCommands_whenMultiple_thenSingleProcessorUtilized() = runTest {
        val debugLogs = mutableListOf<String>()
        val lock = SynchronizedObject()

        val factory = TorCtrl.Factory(
            debugger = { synchronized(lock) { debugLogs.add(it) } },
            handler = UncaughtException.Handler.THROW,
        )
        val host = LocalHost.IPv4.resolve()
        val port = 9055.toPortProxy().findAvailableAsync(1_000, LocalHost.IPv4)
        val address = ProxyAddress(host, port)
        val process = TestUtils.startTor(address.toString())

        var threw: Throwable? = null
        var invocationSuccess = 0
        try {
            val ctrl = factory.connectAsync(address)

            ctrl.enqueue(TorCmd.Authenticate(TestUtils.AUTH_PASS), {}, { invocationSuccess++ })
            ctrl.enqueue(TorCmd.SetEvents(TorEvent.entries), {}, { invocationSuccess++ })
            ctrl.enqueue(TorCmd.Signal.Heartbeat, {}, { invocationSuccess++ })

            // Suspends test until non-suspending complete
            ctrl.executeAsync(TorCmd.Signal.Dump)
            invocationSuccess++

            // Ensure that another processor coroutine started
            ctrl.executeAsync(TorCmd.Signal.ClearDnsCache)
            invocationSuccess++
        } catch (t: Throwable) {
            threw = t
        } finally {
            process.destroy()
        }

        withContext(Dispatchers.Default) { delay(250.milliseconds) }

        threw?.let { throw it }

        // All commands for our test executed successfully
        assertEquals(5, invocationSuccess++)

        // Ensure that given our flurry of commands, a single processor
        // coroutine was started to handle them all.
        synchronized(lock) {
            val processorStarts = debugLogs.mapNotNull {
//                println(it)
                if (it.contains("Processor Started")) it else null
            }

            assertEquals(2, processorStarts.size)
        }
    }
}
