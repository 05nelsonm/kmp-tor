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

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.Companion.toIPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import java.net.InetAddress

@Throws(Exception::class)
internal actual fun LocalHost.Companion.tryPlatformResolve(set: LinkedHashSet<IPAddress>) {
    val addresses = InetAddress.getAllByName("localhost")
    addresses.mapTo(set) { it.hostAddress.toIPAddress() }
}
