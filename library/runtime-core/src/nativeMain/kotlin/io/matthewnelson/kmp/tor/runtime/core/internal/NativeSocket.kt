/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:Suppress("NOTHING_TO_INLINE", "FunctionName", "VariableInitializerIsRedundant")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import platform.posix.EINTR
import platform.posix.errno

@InternalKmpTorApi
public fun kmptor_socket(domain: Int, type: Int, protocol: Int): Int {
    var ret = -1
    do {
        ret = platform_kmptor_socket(domain, type, protocol)
    } while (ret == -1 && errno == EINTR)
    return ret
}

@InternalKmpTorApi
public expect fun kmptor_socket_close(sockfd: Int): Int

internal expect inline fun platform_kmptor_socket(domain: Int, type: Int, protocol: Int): Int
