/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.controller.common.internal

import io.matthewnelson.kmp.tor.common.address.IPAddress
import io.matthewnelson.kmp.tor.common.address.IPAddressV4
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi

// TODO: Fill out
actual object PlatformUtil {
    @InternalTorApi
    actual fun localhostAddress(): IPAddress = IPAddressV4("127.0.0.1")

    @InternalTorApi
    actual val isDarwin: Boolean by lazy {
        platform == "darwin"
    }
    @InternalTorApi
    actual val isLinux: Boolean by lazy {
        platform == "linux" || platform == "android"
    }
    @InternalTorApi
    actual val isMingw: Boolean by lazy {
        platform == "win32"
    }

    @InternalTorApi
    actual val hasControlUnixDomainSocketSupport: Boolean = false
}

/**
 * Returns:
 *  - aix
 *  - android
 *  - darwin
 *  - freebsd
 *  - linux
 *  - openbsd
 *  - sunos
 *  - win32
 * */
private val platform: String
    get() {
        return os?.platform() as? String ?: "linux"
    }

private val os: dynamic
    get() {
        return try {
            js("require('os')")
        } catch (_: Throwable) {
            null
        }
    }
