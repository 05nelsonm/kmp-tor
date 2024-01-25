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
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.Companion.toIPAddress
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
internal actual fun LocalHost.resolveAll(): Set<IPAddress> {
    val interfacesValues = objectValues(os_networkInterfaces())

    val set = LinkedHashSet<IPAddress>(2, 1.0F)
    interfacesValues.forEach { values ->
        values.forEach values@ { entry ->
            if (!(entry.internal as Boolean)) return@values
            set.add((entry.address as String).toIPAddress())
        }
    }

    return set
}

@Suppress("NOTHING_TO_INLINE")
private inline fun objectValues(jsObject: dynamic): Array<Array<dynamic>> {
    return js("Object").values(jsObject).unsafeCast<Array<Array<dynamic>>>()
}
