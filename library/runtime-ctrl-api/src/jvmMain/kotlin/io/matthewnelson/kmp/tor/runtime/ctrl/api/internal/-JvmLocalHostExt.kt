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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.internal

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.waitFor
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.Companion.toIPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import java.net.InetAddress
import kotlin.time.Duration.Companion.milliseconds

@Throws(Exception::class)
internal actual fun LocalHost.Companion.tryPlatformResolve(set: LinkedHashSet<IPAddress>) {
    val addresses = InetAddress.getAllByName("localhost")
    addresses.mapTo(set) { it.hostAddress.toIPAddress() }
}

internal actual fun LocalHost.Companion.execIfConfig(): String {
    return try {
        val p = Runtime.getRuntime().exec(arrayOf("ifconfig"))
        @OptIn(InternalKmpTorApi::class)
        p.waitFor(150.milliseconds)
        p.inputStream.reader().readText()
    } catch (_: Throwable) {
        ""
    }
}
