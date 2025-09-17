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
package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.internal.PortEphemeralIterator.Companion.iterator
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class PortUtilBaseTest {

    protected open val isNodeJs: Boolean = false

    protected abstract suspend fun openServerSocket(
        ipAddress: IPAddress,
        port: Int,
    ): Closeable

    @Test
    fun givenPort_whenIPv4Unavailable_thenIsAvailableReturnsFalse() = runTest {
        val (socket, port) = LocalHost.IPv4.holdServerSocket()
        assertFalse(port.isAvailableAsync(LocalHost.IPv4))
        socket.close()
        withContext(Dispatchers.Default) { delay(350.milliseconds) }
        assertTrue(port.isAvailableAsync(LocalHost.IPv4))
    }

    @Test
    fun givenPort_whenIPv6Unavailable_thenIsAvailableReturnsFalse() = runTest {
        val (socket, port) = LocalHost.IPv6.holdServerSocket()
        assertFalse(port.isAvailableAsync(LocalHost.IPv6))
        socket.close()
        withContext(Dispatchers.Default) { delay(350.milliseconds) }
        assertTrue(port.isAvailableAsync(LocalHost.IPv6))
    }

    @Test
    fun givenFindNextAvailable_whenCoroutineCancelled_thenHandlesCancellationProperly() = runTest(timeout = 120.seconds) {
        if (isNodeJs) {
            // Only needed to test blocking code to ensure
            // context is checked to trigger cancellation
            println("Skipping...")
            return@runTest
        }

        val port = Port.Ephemeral.MIN.toPortEphemeral()
        val host = LocalHost.IPv4
        val limit = 750
        val i = port.iterator(limit)

        var count = 0
        // Make next ports unavailable to force findNextAvailable
        // to loop through all the values of PortProxyIterator
        while (i.hasNext()) {
            val next = i.next().toPortEphemeral()
            if (!next.isAvailableAsync(host)) continue
            host.holdServerSocket(next)
            count++
        }

        assertTrue(count > 0)

        var result: Port.Ephemeral? = null
        var throwable: Throwable? = null
        val latch = Job()
        val job = launch(CoroutineExceptionHandler { _, t -> throwable = t }) {
            latch.complete()
            result = port.findNextAvailableAsync(limit + 50, host)
        }

        latch.join()
        delay(1.milliseconds)
        job.cancel()

        // Ensure any exceptions/results are propagated
        withContext(Dispatchers.Default) {
            delay(2_500.milliseconds)
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

    private suspend fun LocalHost.holdServerSocket(
        port: Port.Ephemeral? = null
    ): Pair<Closeable, Port.Ephemeral> {
        val portEphemeral = port ?: Port.Ephemeral.MIN
            .toPortEphemeral()
            .findNextAvailableAsync(1_000, this)

        val socket = openServerSocket(resolve(), portEphemeral.value)
        currentCoroutineContext().job.invokeOnCompletion {
            try {
                socket.close()
            } catch (_: Throwable) {}
        }
        return socket to portEphemeral
    }
}
