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

import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.internal.PortProxyIterator.Companion.iterator
import io.matthewnelson.kmp.tor.runtime.util.findAvailableAsync
import io.matthewnelson.kmp.tor.runtime.util.isAvailableAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalStdlibApi::class)
abstract class PortUtilBaseTest {

    protected abstract fun serverSocket(
        ipAddress: IPAddress,
        port: Int,
    ): AutoCloseable

    @Test
    fun givenPort_whenIPv4Unavailable_thenIsAvailableReturnsFalse() = runTest {
        val port = LocalHost.IPv4.serverSocket()
        assertFalse(port.isAvailableAsync(LocalHost.IPv4))
    }

    @Test
    fun givenPort_whenIPv6Unavailable_thenIsAvailableReturnsFalse() = runTest {
        val port = LocalHost.IPv6.serverSocket()
        assertFalse(port.isAvailableAsync(LocalHost.IPv6))
    }

    @Test
    fun givenFindAvailable_whenCoroutineCancelled_thenHandlesCancellationProperly() = runTest {
        val port = Port.Proxy.MIN.toPortProxy()
        val host = LocalHost.IPv4
        val limit = 500
        val i = port.iterator(limit)

        var count = 0
        // Make next ports unavailable to force findAvailable
        // to loop through all the values of PortProxyIterator
        while (i.hasNext()) {
            val next = i.next().toPortProxy()
            if (!next.isAvailableAsync(host)) continue
            host.serverSocket(next)
            count++
        }

        assertTrue(count > 0)

        var result: Port.Proxy? = null
        var throwable: Throwable? = null
        val job = launch(CoroutineExceptionHandler { _, t -> throwable = t }) {
            result = port.findAvailableAsync(limit + 50, host)
        }

        // Slight delay to ensure blocking code
        // is actually running
        withContext(Dispatchers.Default) {
            delay(7.milliseconds)
        }

        job.cancel()

        // Ensure any exceptions are propagated to our handler
        withContext(Dispatchers.Default) {
            delay(250.milliseconds)
        }

        // If it threw an IOException, that would be propagated
        // and this assertion would fail.
        //
        // This being null means:
        //  - No ports were available, so the function was _actually_
        //    running through all those values which have open ServerSockets
        //  - Cancellation popped it out of it's while loop (even for
        //    underlying blocking code on Jvm/Native)
        //  - Logic for determining which exception to throw based
        //    on CoroutineContext is correct.
        assertNull(throwable)
        assertNull(result)
    }

    private suspend fun LocalHost.serverSocket(
        port: Port.Proxy? = null
    ): Port.Proxy {
        val portProxy = port ?: Port.Proxy.MIN
            .toPortProxy()
            .findAvailableAsync(1_000, this)

        val socket = serverSocket(resolve(), portProxy.value)
        currentCoroutineContext().job.invokeOnCompletion {
            try {
                socket.close()
            } catch (_: Throwable) {}
        }
        withContext(Dispatchers.Default) {
            // Need to switch context here for an actual delay
            // b/c JS needs to establish the connection
            delay(10.milliseconds)
        }
        return portProxy
    }
}
