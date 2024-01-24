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

import io.matthewnelson.kmp.file.SysPathSep
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.OSHost
import io.matthewnelson.kmp.tor.core.resource.OSInfo
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.V4.Companion.toIPAddressV4
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.V6.Companion.toIPAddressV6
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import java.net.Inet4Address
import java.net.Inet6Address

@OptIn(InternalKmpTorApi::class)
internal actual val UnixSocketsNotSupportedMessage: String? by lazy {
    // Android support via android.net.LocalSocket since API 1
    if (OSInfo.INSTANCE.isAndroidRuntime()) return@lazy null

    val host = OSInfo.INSTANCE.osHost
    if (host is OSHost.Windows) {
        return@lazy "Tor does not support Unix Sockets on Windows"
    }

    if (SysPathSep != '/') {
        return@lazy "Unsupported OSHost[$host]"
    }

    // Check if Java 16+
    try {
        Class.forName("java.net.UnixDomainSocketAddress")
            ?: throw NullPointerException()

        null
    } catch (_: Throwable) {
        "Unix Sockets are not supported for Java 15 or below"
    }
}

internal actual val ProcessID: Int? get() {
    @OptIn(InternalKmpTorApi::class)
    return if (OSInfo.INSTANCE.isAndroidRuntime()) {
        AndroidPID
    } else {
        try {
            java.lang.management.ManagementFactory
                .getRuntimeMXBean()
                .name
                .split('@')[0]
                .toInt()
        } catch (_: Throwable) {
            null
        }
    }
}

private val AndroidPID: Int? by lazy {
    try {
        Class.forName("android.os.Process")
            ?.getMethod("myPid")
            ?.invoke(null) as? Int
    } catch (_: Throwable) {
        null
    }
}

@Throws(Exception::class)
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun LocalHost.platformResolveIPv4(): IPAddress.V4 {
    return Inet4Address.getByName(value).hostAddress.toIPAddressV4()
}

@Throws(Exception::class)
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun LocalHost.platformResolveIPv6(): IPAddress.V6 {
    return Inet6Address.getByName(value).hostAddress.toIPAddressV6()
}
