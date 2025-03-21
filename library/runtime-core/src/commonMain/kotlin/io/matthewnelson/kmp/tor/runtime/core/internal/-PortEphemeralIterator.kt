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
package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
import kotlin.jvm.JvmSynthetic

internal class PortEphemeralIterator private constructor(
    private val port: Port.Ephemeral,
    private var limit: Int,
): Iterator<Int> {

    private val start = limit
    private var lastPort: Int? = null
    private var nextPort = port.value

    init {
        require(limit in 1..1_000) { "limit must be between 1 to 1_000 (inclusive)" }
    }

    override fun hasNext(): Boolean = limit > 0

    override fun next(): Int {
        if (!hasNext()) throw NoSuchElementException()
        val p = nextPort
        lastPort = p
        nextPort = if (p == Port.Ephemeral.MAX) Port.Ephemeral.MIN else p + 1
        limit--
        return p
    }

    internal fun toPortEphemeral(): Port.Ephemeral = lastPort?.toPortEphemeral() ?: port

    internal fun unavailableException(ipAddress: IPAddress): IOException {
        val top = port.value + start - limit - 1
        val ranges = if (top <= Port.Ephemeral.MAX) {
            "(${port.value} - $top)"
        } else {
            val bottom = top - Port.Ephemeral.MAX + Port.Ephemeral.MIN
            "(${port.value} - ${Port.Ephemeral.MAX}) and (${Port.Ephemeral.MIN} - $bottom)"
        }

        return IOException("Failed to find available port for ${ipAddress.canonicalHostName()} $ranges")
    }

    internal companion object {

        @JvmSynthetic
        @Throws(IllegalArgumentException::class)
        internal fun Port.Ephemeral.iterator(
            limit: Int,
        ): PortEphemeralIterator = PortEphemeralIterator(this, limit)
    }
}
