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
    actual val isDarwin: Boolean = false
    @InternalTorApi
    actual val isLinux: Boolean = false
    @InternalTorApi
    actual val isMingw: Boolean = true

    @InternalTorApi
    actual val hasControlUnixDomainSocketSupport: Boolean = false
}
