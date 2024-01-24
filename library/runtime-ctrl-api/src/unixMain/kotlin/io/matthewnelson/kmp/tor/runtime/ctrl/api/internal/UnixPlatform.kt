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

import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import platform.posix.getpid

internal actual val UnixSocketsNotSupportedMessage: String? = null

internal actual val IsUnixLikeHost: Boolean get() = true

internal actual val IsAndroidHost: Boolean get() = false

internal actual val ProcessID: Int? get() = getpid()

@Throws(Exception::class)
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun LocalHost.platformResolveIPv4(): IPAddress.V4 {
    TODO()
}

@Throws(Exception::class)
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun LocalHost.platformResolveIPv6(): IPAddress.V6 {
    TODO()
}
