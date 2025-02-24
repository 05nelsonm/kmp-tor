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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import kotlin.coroutines.cancellation.CancellationException

/**
 * Checks if the TCP port is available on [LocalHost] or not.
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.isAvailableSync]
 * */
public expect suspend fun Port.isAvailableAsync(
    host: LocalHost,
): Boolean

/**
 * Finds an available TCP port on [LocalHost] starting with the current
 * [Port.Ephemeral.value] and iterating up [limit] times.
 *
 * If [Port.Ephemeral.MAX] is exceeded while iterating through ports and [limit]
 * has not been exhausted, the remaining checks will start from [Port.Ephemeral.MIN].
 *
 * @param [host] either [LocalHost.IPv4] or [LocalHost.IPv6]
 * @param [limit] the number of ports to scan. min: 1, max: 1_000
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.findAvailableSync]
 * @throws [IllegalArgumentException] if [limit] is not between 1 and 1_000 (inclusive)
 * @throws [IOException] if no ports are available
 * @throws [CancellationException] if underlying coroutine was cancelled
 * */
public expect suspend fun Port.Ephemeral.findNextAvailableAsync(
    limit: Int,
    host: LocalHost,
): Port.Ephemeral
