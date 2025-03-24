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

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.internal.ServerSocketProducer.Companion.toServerSocketProducer
import io.matthewnelson.kmp.tor.runtime.core.internal.PortEphemeralIterator.Companion.iterator
import io.matthewnelson.kmp.tor.runtime.core.internal.cancellationExceptionOr
import io.matthewnelson.kmp.tor.runtime.core.internal.isPortAvailable
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName

/**
 * Checks if the TCP port is available on [LocalHost] or not.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @see [isAvailableSync]
 * */
public actual suspend fun Port.isAvailableAsync(
    host: LocalHost,
): Boolean = withContext(Dispatchers.IO) {
    isAvailableSync(host)
}

/**
 * Finds an available TCP port on [LocalHost] starting with the current
 * [Port.Ephemeral.value] and iterating up [limit] times.
 *
 * If [Port.Ephemeral.MAX] is exceeded while iterating through ports and [limit]
 * has not been exhausted, the remaining checks will start from [Port.Ephemeral.MIN].
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @param [limit] the number of ports to scan. min: 1, max: 1_000
 * @see [findAvailableSync]
 * @throws [IllegalArgumentException] if [limit] is not between 1 and 1_000 (inclusive)
 * @throws [IOException] if no ports are available
 * @throws [CancellationException] if underlying coroutine was cancelled
 * */
public actual suspend fun Port.Ephemeral.findNextAvailableAsync(
    limit: Int,
    host: LocalHost,
): Port.Ephemeral = withContext(Dispatchers.IO) {
    findAvailableSync(limit, host, currentCoroutineContext())
}

/**
 * Checks if the TCP port is available on [LocalHost] or not.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @see [isAvailableAsync]
 * @throws [IOException] if the check fails (e.g. calling from Main thread on Android)
 * */
public fun Port.isAvailableSync(
    host: LocalHost,
): Boolean = host.resolve()
    .toServerSocketProducer()
    .isPortAvailable(value)

/**
 * Finds an available TCP port on [LocalHost] starting with the current
 * [Port.Ephemeral.value] and iterating up [limit] times.
 *
 * If [Port.Ephemeral.MAX] is exceeded while iterating through ports and [limit]
 * has not been exhausted, the remaining checks will start from [Port.Ephemeral.MIN].
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @param [limit] the number of ports to scan. min: 1, max: 1_000
 * @see [findNextAvailableAsync]
 * @throws [IllegalArgumentException] if [limit] is not between 1 and 1_000 (inclusive)
 * @throws [IOException] if the check fails (e.g. calling from Main thread on Android)
 * */
public fun Port.Ephemeral.findNextAvailableSync(
    limit: Int,
    host: LocalHost,
): Port.Ephemeral = findAvailableSync(limit, host, null)

@Throws(IOException::class, CancellationException::class)
private fun Port.Ephemeral.findAvailableSync(
    limit: Int,
    host: LocalHost,
    context: CoroutineContext?,
): Port.Ephemeral {
    val iterator = iterator(limit)
    val ipAddress = host.resolve()
    val serverSocketProducer = ipAddress.toServerSocketProducer()

    try {
        while (context?.isActive != false && iterator.hasNext()) {
            if (!serverSocketProducer.isPortAvailable(iterator.next())) continue
            return iterator.toPortEphemeral()
        }
    } catch (t: Throwable) {
        throw context.cancellationExceptionOr { t.wrapIOException() }
    }

    throw context.cancellationExceptionOr { iterator.unavailableException(ipAddress) }
}
