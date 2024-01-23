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

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import org.kotlincrypto.hash.sha2.SHA256

@OptIn(InternalKmpTorApi::class)
@Throws(IllegalStateException::class)
internal actual fun TorRuntime.ServiceFactory.Companion.serviceRuntimeOrNull(
    block: () -> TorRuntime.ServiceFactory,
): TorRuntime? = null

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun ByteArray.sha256(): String {
    return SHA256()
        .digest(this)
        .encodeToString(Base16)
}
