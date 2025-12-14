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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.readUtf8
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.Companion.toIPAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import kotlin.collections.removeLast as kRemoveLast

@Throws(Exception::class)
internal expect fun LocalHost.Companion.tryPlatformResolve(set: LinkedHashSet<IPAddress>)

internal fun LocalHost.Companion.tryParsingIfConfig(set: LinkedHashSet<IPAddress>) {
    if (!IsUnixLikeHost) return
    if (IsAndroidHost) return
    if (IsDarwinMobile) return
    if (set.hasIPv4IPv6) return

    val out = try {
        Process.Builder(command = "ifconfig")
            .stdin(Stdio.Null)
            .createOutput()
            .stdout
            .trimIndent()
    } catch (_: IOException) {
        return
    }

    if (out.isBlank()) return

    val configs = mutableListOf<MutableList<String>>()
    out.lines().forEach { line ->
        if (line.isBlank()) return@forEach
        if (!line.first().isWhitespace()) {
            configs.add(0, mutableListOf(line.trim()))
            return@forEach
        }
        configs.firstOrNull()?.add(line.trim())
    }

    while (configs.isNotEmpty()) {
        val config = configs.kRemoveLast()
        if (config.isEmpty()) continue

        val i = config.iterator()
        val flags = i.next().substringAfter('<')
            .substringBefore('>')
            .split(',')

        if (!flags.contains("LOOPBACK")) continue
        if (!flags.contains("UP")) continue

        // look for inet and inet6
        while (i.hasNext()) {
            val line = i.next()
            if (!line.startsWith("inet")) continue

            val sb = StringBuilder()
            val ci = line.iterator()
            var space = false
            while (ci.hasNext()) {
                val c = ci.next()
                if (c.isWhitespace()) {
                    if (space && sb.isNotEmpty()) break
                    space = true
                    continue
                }
                if (!space) continue
                sb.append(c)
            }

            val string = sb.toString()
            if (string.contains('%')) continue

            val address = string.toIPAddressOrNull() ?: continue

            set.add(address)
        }
    }
}

internal fun LocalHost.Companion.tryParsingEtcHosts(set: LinkedHashSet<IPAddress>) {
    if (!IsUnixLikeHost) return
    if (set.hasIPv4IPv6) return

    val lines = try {
        "/etc/hosts".toFile().readUtf8().lines()
    } catch (_: Throwable) {
        return
    }

    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach
        if (trimmed.startsWith('#')) return@forEach
        if (!trimmed.contains("localhost")) return@forEach

        val sb = StringBuilder()
        val args = mutableListOf<String>()

        val i = trimmed.iterator()
        while (i.hasNext()) {
            val c = i.next()
            if (c.isWhitespace()) {
                if (sb.isEmpty()) continue
                args.add(sb.toString())
                sb.clear()
                continue
            }
            sb.append(c)
        }

        if (sb.isNotEmpty()) args.add(sb.toString())
        // Check for EXACT domain 'localhost'
        if (!args.contains("localhost")) return@forEach

        val address = args.first()
            .toIPAddressOrNull()
            ?: return@forEach

        set.add(address)
    }
}

private inline val LinkedHashSet<IPAddress>.hasIPv4IPv6: Boolean get() {
    var hasIPv4 = false
    var hasIPv6 = false
    forEach { address ->
        when (address) {
            is IPAddress.V4 -> hasIPv4 = true
            is IPAddress.V6 -> hasIPv6 = true
        }
    }
    return hasIPv4 && hasIPv6
}
