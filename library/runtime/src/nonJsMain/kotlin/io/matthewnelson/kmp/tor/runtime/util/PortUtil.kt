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
@file:JvmName("PortUtil")

package io.matthewnelson.kmp.tor.runtime.util

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port
import io.matthewnelson.kmp.tor.runtime.internal.InetAddressWrapper.Companion.toInetAddressWrapper
import io.matthewnelson.kmp.tor.runtime.internal.PortProxyIterator.Companion.iterator
import io.matthewnelson.kmp.tor.runtime.internal.cancellationExceptionOr
import io.matthewnelson.kmp.tor.runtime.internal.isPortAvailable
import io.matthewnelson.kmp.tor.runtime.internal.isTorRuntime
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

/**
 * Checks if the TCP port is available on [LocalHost] or not.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @throws [IOException] if [LocalHost.resolve] fails
 * @throws [CancellationException] if underlying coroutine was cancelled
 * */
@Throws(IOException::class, CancellationException::class)
public actual suspend fun Port.isAvailableAsync(
    host: LocalHost,
): Boolean = if (currentCoroutineContext().isTorRuntime) {
    isAvailable(host)
} else {
    withContext(Dispatchers.IO) {
        isAvailable(host)
    }
}

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
 * @throws [IOException] if [LocalHost.resolve] fails
 * @throws [CancellationException] if underlying coroutine was cancelled
 * */
@Throws(IOException::class, CancellationException::class)
public actual suspend fun Port.Proxy.findAvailableAsync(
    limit: Int,
    host: LocalHost,
): Port.Proxy {
    val ctx = currentCoroutineContext()
    return if (ctx.isTorRuntime) {
        findAvailable(limit, host, ctx)
    } else {
        withContext(Dispatchers.IO) {
            findAvailable(limit, host, currentCoroutineContext())
        }
    }
}

/**
 * Checks if the TCP port is available on [LocalHost] or not.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @throws [IOException] if the check fails (e.g. calling from Main thread on Android)
 * */
@Throws(IOException::class)
public fun Port.isAvailable(
    host: LocalHost,
): Boolean = host.resolve()
    .toInetAddressWrapper()
    .isPortAvailable(value)

/**
 * Finds an available TCP port on [LocalHost] starting with the current
 * [Port.Proxy.value] and iterating up [limit] times.
 *
 * If [Port.Proxy.MAX] is exceeded while iterating through ports and [limit]
 * has not been exhausted, the remaining checks will start from [Port.Proxy.MIN].
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @param [limit] the number of ports to scan. min: 1, max: 1_000
 * @throws [IllegalArgumentException] if [limit] is not between 1 and 1_000 (inclusive)
 * @throws [IOException] if the check fails (e.g. calling from Main thread on Android)
 * */
@Throws(IOException::class)
public fun Port.Proxy.findAvailable(
    limit: Int,
    host: LocalHost,
): Port.Proxy = findAvailable(limit, host, null)

@Throws(IOException::class, CancellationException::class)
private fun Port.Proxy.findAvailable(
    limit: Int,
    host: LocalHost,
    context: CoroutineContext?,
): Port.Proxy {
    val i = iterator(limit)
    val ipAddress = host.resolve()
    val inetAddress = ipAddress.toInetAddressWrapper()

    try {
        while (context?.isActive != false && i.hasNext()) {
            if (!inetAddress.isPortAvailable(i.next())) continue
            return i.toPortProxy()
        }
    } catch (t: Throwable) {
        throw context.cancellationExceptionOr { t.wrapIOException() }
    }

    throw context.cancellationExceptionOr { i.unavailableException(ipAddress) }
}
