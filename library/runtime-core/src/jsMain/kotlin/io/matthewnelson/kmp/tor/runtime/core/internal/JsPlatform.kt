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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.OSHost
import io.matthewnelson.kmp.tor.core.resource.OSInfo
import io.matthewnelson.kmp.tor.runtime.core.OnEvent

@Suppress("ACTUAL_WITHOUT_EXPECT")
internal actual typealias ExecutorMainInternal = OnEvent.Executor.Unconfined

@OptIn(InternalKmpTorApi::class)
internal actual val UnixSocketsNotSupportedMessage: String? by lazy {
    val host = OSInfo.INSTANCE.osHost
    if (host is OSHost.Windows) {
        return@lazy "Tor does not support Unix Sockets on Windows"
    }

    if (SysDirSep != '/') {
        "Unsupported OSHost[$host]"
    } else {
        null
    }
}

@Suppress("NOTHING_TO_INLINE")
@OptIn(InternalProcessApi::class)
internal inline fun net_Server.onError(
    noinline callback: (err: dynamic) -> Unit,
) {
    on("error", callback)
}
