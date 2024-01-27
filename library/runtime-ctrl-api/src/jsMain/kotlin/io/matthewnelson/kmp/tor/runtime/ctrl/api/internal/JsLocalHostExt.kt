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

import io.matthewnelson.kmp.file.Buffer
import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress.Companion.toIPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost

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

internal actual fun LocalHost.Companion.execIfConfig(): String {
    return try {
        val buffer = child_process_execSync("ifconfig")
        @OptIn(DelicateFileApi::class)
        Buffer.wrap(buffer).toUtf8()
    } catch (_: Throwable) {
        ""
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun objectValues(jsObject: dynamic): Array<Array<dynamic>> {
    return js("Object").values(jsObject).unsafeCast<Array<Array<dynamic>>>()
}
