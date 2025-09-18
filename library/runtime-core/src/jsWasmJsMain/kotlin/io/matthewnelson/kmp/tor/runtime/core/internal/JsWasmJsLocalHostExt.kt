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

import io.matthewnelson.kmp.tor.runtime.core.internal.js.JsObject
import io.matthewnelson.kmp.tor.runtime.core.internal.js.getBoolean
import io.matthewnelson.kmp.tor.runtime.core.internal.js.getJsArray
import io.matthewnelson.kmp.tor.runtime.core.internal.js.getJsObject
import io.matthewnelson.kmp.tor.runtime.core.internal.js.getString
import io.matthewnelson.kmp.tor.runtime.core.internal.node.node_os
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.Companion.toIPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost

@Throws(Exception::class)
internal actual fun LocalHost.Companion.tryPlatformResolve(set: LinkedHashSet<IPAddress>) {
    val os = node_os
    try {
        val interfaces = os.networkInterfaces()
        val values = JsObject.values(interfaces)
        for (i in 0 until values.length) {
            val connections = values.getJsArray(i)
            for (j in 0 until connections.length) {
                val connection = connections.getJsObject(j)
                if (!connection.getBoolean("internal")) continue
                val address = connection.getString("address").toIPAddress()
                set.add(address)
            }
        }
    } catch (_: Throwable) {
        return
    }
}
