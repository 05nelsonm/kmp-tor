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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.tor.runtime.core.internal.sockaddr_un
import kotlinx.cinterop.*
import platform.posix.sockaddr
import platform.posix.socklen_t
import platform.posix.strcpy

@OptIn(ExperimentalForeignApi::class)
internal actual inline fun File.socketAddress(
    family: UShort,
    block: (CValuesRef<sockaddr>, len: socklen_t) -> Unit,
) {
    cValue<sockaddr_un> {
        strcpy(sun_path, path)
        sun_family = family.convert()

        block(ptr.reinterpret(), sizeOf<sockaddr_un>().convert())
    }
}
