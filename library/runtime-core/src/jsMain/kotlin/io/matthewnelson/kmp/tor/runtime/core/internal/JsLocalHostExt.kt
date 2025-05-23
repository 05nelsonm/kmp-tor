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

@Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")
internal actual fun LocalHost.Companion.tryPlatformResolve(set: LinkedHashSet<IPAddress>) {
    try {
        objectValues(os_networkInterfaces()).forEach { values ->
            values.forEach { entry ->
                if (entry.internal as Boolean) {
                    set.add((entry.address as String).toIPAddress())
                }
            }
        }
    } catch (_: Throwable) {
        return
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun objectValues(jsObject: dynamic): Array<Array<dynamic>> {
    return js("Object").values(jsObject).unsafeCast<Array<Array<dynamic>>>()
}
