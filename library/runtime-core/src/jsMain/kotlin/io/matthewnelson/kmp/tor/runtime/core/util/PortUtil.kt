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
@file:Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.internal.*
import io.matthewnelson.kmp.tor.runtime.core.internal.PortProxyIterator.Companion.iterator
import io.matthewnelson.kmp.tor.runtime.core.internal.net_createServer
import io.matthewnelson.kmp.tor.runtime.core.internal.onError
import kotlinx.coroutines.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Checks if the TCP port is available on [LocalHost] or not.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @throws [IOException] if [LocalHost.resolve] fails
 * @throws [CancellationException] if underlying coroutine was cancelled
 * */
// @Throws(IOException::class, CancellationException::class)
public actual suspend fun Port.isAvailableAsync(
    host: LocalHost,
): Boolean = host.resolve()
    .isPortAvailableOrNull(value, timeout = (3 * 42).milliseconds)
    ?: false

/**
 * Finds an available TCP port on [LocalHost] starting with the current
 * [Port.Proxy.value] and iterating up [limit] times.
 *
 * If [Port.Proxy.MAX] is exceeded while iterating through ports and [limit]
 * has not been exhausted, the remaining checks will start from [Port.Proxy.MIN].
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @param [limit] the number of ports to scan. min: 1, max: 1_000
 * @throws [IllegalArgumentException] if [limit] is not between 1 and 1_000 (inclusive)
 * @throws [IOException] if [LocalHost.resolve] fails, or no ports are available
 * @throws [CancellationException] if underlying coroutine was cancelled
 * */
// @Throws(IOException::class, CancellationException::class)
public actual suspend fun Port.Proxy.findAvailableAsync(
    limit: Int,
    host: LocalHost,
): Port.Proxy {
    val i = iterator(limit)
    val ipAddress = host.resolve()

    val ctx = currentCoroutineContext()
    while (ctx.isActive && i.hasNext()) {
        val isAvailable = ipAddress.isPortAvailableOrNull(i.next())
        if (isAvailable != true) continue

        return i.toPortProxy()
    }

    throw ctx.cancellationExceptionOr { i.unavailableException(ipAddress) }
}

// @Throws(CancellationException::class)
private suspend fun IPAddress.isPortAvailableOrNull(
    port: Int,
    timeout: Duration = 42.milliseconds
): Boolean? {
    val timeMark = TimeSource.Monotonic.markNow()
    val ctx = currentCoroutineContext()
    val latch = Job(ctx[Job])
    val closure = Job(ctx[Job])
    val ipAddress = value

    var isAvailable: Boolean? = null

    try {
        val server = net_createServer { it.destroy(); it.unref();  Unit }

        latch.invokeOnCompletion {
            server.close()
            server.unref()
        }

        server.onError {
            isAvailable = false
            latch.complete()
        }

        @OptIn(InternalProcessApi::class)
        server.on("close") {
            closure.complete()
        }

        val options = js("{}")
        options["port"] = port
        options["host"] = ipAddress
        options["backlog"] = 1

        server.listen(options) {
            isAvailable = true
            latch.complete()
        }

        while (
            ctx.isActive
            && latch.isActive
            && isAvailable == null
        ) {
            delay(5.milliseconds)
            if (timeMark.elapsedNow() > timeout) break
        }
    } finally {
        latch.complete()
    }

    if (isAvailable == true) {
        // Await for server to close before returning true
        val closureMark = TimeSource.Monotonic.markNow()

        while (closure.isActive) {
            delay(5.milliseconds)
            if (closureMark.elapsedNow() < timeout) continue
            closure.complete()
            return null
        }
    } else {
        closure.complete()
    }

    return isAvailable
}
