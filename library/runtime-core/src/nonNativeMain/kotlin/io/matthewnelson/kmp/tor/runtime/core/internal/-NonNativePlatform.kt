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

import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.OSHost
import io.matthewnelson.kmp.tor.common.core.OSInfo
import kotlin.jvm.JvmSynthetic

@OptIn(InternalKmpTorApi::class)
internal actual val AFUnixPathBufSize: Int get() {
    return when (OSInfo.INSTANCE.osHost) {
        // sockaddr_un.sun_path buffer size as defined in sys/un.h
        is OSHost.FreeBSD,
        is OSHost.MacOS -> 104

        // UNIX_PATH_MAX as defined in afunix.h
        is OSHost.Windows -> 108

        // sockaddr_un.sun_path buffer size as defined in sys/un.h
        is OSHost.Linux -> 108

        // Unknown (assuming Non-BSD)
        else -> 108
    }
}

@get:JvmSynthetic
@OptIn(InternalKmpTorApi::class)
internal actual val IsUnixLikeHost: Boolean get() {
    return when (OSInfo.INSTANCE.osHost) {
        is OSHost.FreeBSD,
        is OSHost.Linux,
        is OSHost.MacOS -> true
        is OSHost.Windows -> false
        else -> SysDirSep == '/'
    }
}

@get:JvmSynthetic
@OptIn(InternalKmpTorApi::class)
internal actual val IsAndroidHost: Boolean get() {
    // Could be Java running on Android via Termux if JVM
    return OSInfo.INSTANCE.osHost is OSHost.Linux.Android
}

internal actual val IsDarwinMobile: Boolean get() = false
