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
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.sizeOf
import platform.osx.sockaddr_un
import platform.posix.sa_family_tVar
import platform.posix.u_charVar

internal actual val AFUnixSunPathSize: Int get() {
    // struct  sockaddr_un {
    //	 unsigned char   sun_len;        /* sockaddr len including null */
    //	 sa_family_t     sun_family;     /* [XSI] AF_UNIX */
    //	 char            sun_path[104];  /* [XSI] path name (gag) */
    // };
    @OptIn(ExperimentalForeignApi::class)
    return (sizeOf<sockaddr_un>() -  sizeOf<u_charVar>() - sizeOf<sa_family_tVar>()).toInt()
}
