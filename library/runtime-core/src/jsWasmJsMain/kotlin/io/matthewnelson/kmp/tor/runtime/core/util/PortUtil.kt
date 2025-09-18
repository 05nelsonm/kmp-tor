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

import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.internal.PortEphemeralIterator.Companion.iterator
import io.matthewnelson.kmp.tor.runtime.core.internal.cancellationExceptionOr
import io.matthewnelson.kmp.tor.runtime.core.internal.js.JsObject
import io.matthewnelson.kmp.tor.runtime.core.internal.js.new
import io.matthewnelson.kmp.tor.runtime.core.internal.js.set
import io.matthewnelson.kmp.tor.runtime.core.internal.node.node_net
import io.matthewnelson.kmp.tor.runtime.core.internal.node.onClose
import io.matthewnelson.kmp.tor.runtime.core.internal.node.onError
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Checks if the TCP port is available on [LocalHost] or not.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @throws [UnsupportedOperationException] On Kotlin/JS-Browser
 * */
public actual suspend fun Port.isAvailableAsync(
    host: LocalHost,
): Boolean = host.resolve()
    .isPortAvailableOrNull(value, timeout = (3 * 42).milliseconds)
    ?: false

/**
 * Finds an available TCP port on [LocalHost] starting with the current
 * [Port.Ephemeral] value and iterating up [limit] times.
 *
 * If [Port.Ephemeral.MAX] is exceeded while iterating through ports and [limit]
 * has not been exhausted, the remaining checks will start from [Port.Ephemeral.MIN].
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @param [limit] the number of ports to scan. min: 1, max: 1_000
 * @throws [IllegalArgumentException] if [limit] is not between 1 and 1_000 (inclusive)
 * @throws [IOException] if no ports are available
 * @throws [CancellationException] if underlying coroutine was cancelled
 * @throws [UnsupportedOperationException] On Kotlin/JS-Browser
 * */
public actual suspend fun Port.Ephemeral.findNextAvailableAsync(
    limit: Int,
    host: LocalHost,
): Port.Ephemeral {
    val i = iterator(limit)
    val ipAddress = host.resolve()

    val ctx = currentCoroutineContext()
    while (ctx.isActive && i.hasNext()) {
        val isAvailable = ipAddress.isPortAvailableOrNull(i.next())
        if (isAvailable != true) continue

        return i.toPortEphemeral()
    }

    throw ctx.cancellationExceptionOr { i.unavailableException(ipAddress) }
}

@Throws(CancellationException::class, UnsupportedOperationException::class)
private suspend fun IPAddress.isPortAvailableOrNull(
    port: Int,
    timeout: Duration = 42.milliseconds,
): Boolean? {
    val net = node_net
    val timeMark = TimeSource.Monotonic.markNow()
    val ctx = currentCoroutineContext()
    val latch = Job(ctx[Job])
    val closure = Job(ctx[Job])

    var isAvailable: Boolean? = null

    @OptIn(DelicateFileApi::class, InternalKmpTorApi::class)
    try {
        val server = jsExternTryCatch {
            net.createServer { socket ->
                socket.destroy()
                socket.unref()
            }
        }

        latch.invokeOnCompletion {
            jsExternTryCatch {
                server.close()
                server.unref()
            }
        }

        server.onError {
            isAvailable = false
            latch.complete()
        }
        server.onClose {
            closure.complete()
        }

        val options = JsObject.new()
        options["port"] = port
        options["host"] = value
        options["backlog"] = 1

        jsExternTryCatch {
            server.listen(options) {
                isAvailable = true
                latch.complete()
            }
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
