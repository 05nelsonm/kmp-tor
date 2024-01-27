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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.ctrl.api.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.OSHost
import io.matthewnelson.kmp.tor.core.resource.OSInfo
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.Companion.toIPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.Companion.toIPAddressOrNull
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost

@OptIn(InternalKmpTorApi::class)
internal actual val UnixSocketsNotSupportedMessage: String? by lazy {
    val host = OSInfo.INSTANCE.osHost
    if (host is OSHost.Windows) {
        return@lazy "Tor does not support Unix Sockets on Windows"
    }

    if (SysPathSep != '/') {
        "Unsupported OSHost[$host]"
    } else {
        null
    }
}

internal actual val ProcessID: Int? get() = process_pid

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
internal actual fun LocalHost.Companion.resolveAllTo(set: LinkedHashSet<IPAddress>) {
    tryOsNetworkInterfaces(set)

    // Try some shell commands
    tryParseIfConfig(set)

    // last resort. Read from /etc/hosts
    tryParseEtcHosts(set)
}

internal fun LocalHost.Companion.tryOsNetworkInterfaces(set: LinkedHashSet<IPAddress>) {
    try {
        objectValues(os_networkInterfaces()).forEach { values ->
            values.forEach values@ { entry ->
                if (entry.internal as Boolean) {
                    set.add((entry.address as String).toIPAddress())
                }
            }
        }
    } catch (_: Throwable) {
        return
    }
}

internal fun LocalHost.Companion.tryParseIfConfig(set: LinkedHashSet<IPAddress>) {
    if (!IsUnixLikeHost) return
    if (set.hasIPv4IPv6) return

    val ifConfig = try {
        val buffer = child_process_execSync("ifconfig")
        @OptIn(DelicateFileApi::class)
        Buffer.wrap(buffer).toUtf8().trimIndent()
    } catch (_: Throwable) {
        return
    }

    val configs = mutableListOf<MutableList<String>>()
    ifConfig.lines().forEach { line ->
        if (line.isBlank()) return@forEach
        if (!line.startsWith(' ')) {
            configs.add(0, mutableListOf(line.trim()))
            return@forEach
        }
        configs.first().add(line.trim())
    }

    configs.reversed().forEach { config ->
        if (config.isEmpty()) return@forEach

        val i = config.iterator()
        val flags = i.next().substringAfter('<')
            .substringBefore('>')
            .split(',')
        if (!flags.contains("LOOPBACK")) return@forEach
        if (!flags.contains("UP")) return@forEach

        // look for inet and inet6
        while (i.hasNext()) {
            val line = i.next()
            if (!line.startsWith("inet")) continue
            val address = line.substringAfter(' ')
                .substringBefore(' ')
                .toIPAddressOrNull()
                ?: continue
            set.add(address)
        }
    }
}

internal fun LocalHost.Companion.tryParseEtcHosts(set: LinkedHashSet<IPAddress>) {
    if (!IsUnixLikeHost) return
    if (set.hasIPv4IPv6) return

    val etcHosts = "/etc/hosts".toFile()
    if (!etcHosts.exists()) return

    val lines = try {
        etcHosts.readUtf8().lines()
    } catch (_: Throwable) {
        return
    }

    lines.forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isBlank()) return@forEach
        if (trimmed.startsWith('#')) return@forEach
        if (!trimmed.contains("localhost")) return@forEach

        val sb = StringBuilder()
        val i = trimmed.iterator()
        while (i.hasNext()) {
            val c = i.next()
            if (c == ' ' || c == '\r' || c == '\t') break
            sb.append(c)
        }

        val address = sb.toString()
            .toIPAddressOrNull()
            ?: return@forEach

        set.add(address)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun objectValues(jsObject: dynamic): Array<Array<dynamic>> {
    return js("Object").values(jsObject).unsafeCast<Array<Array<dynamic>>>()
}

@Suppress("NOTHING_TO_INLINE")
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
