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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.OSInfo
import io.matthewnelson.kmp.tor.runtime.TorRuntime

@JvmSynthetic
@OptIn(InternalKmpTorApi::class)
@Throws(IllegalStateException::class)
internal actual fun TorRuntime.ServiceFactory.Companion.serviceRuntimeOrNull(
    block: () -> TorRuntime.ServiceFactory,
): TorRuntime? {
    val create = AndroidTorRuntimeCreate ?: return null
    return create.invoke(null, block()) as TorRuntime
}

@OptIn(InternalKmpTorApi::class)
private val AndroidTorRuntimeCreate by lazy {
    if (!OSInfo.INSTANCE.isAndroidRuntime()) return@lazy null

    try {
        Class
            .forName("io.matthewnelson.kmp.tor.runtime.mobile.TorService\$AndroidTorRuntime")
            ?.getMethod("create", TorRuntime.ServiceFactory::class.java)
    } catch (_: Throwable) {
        null
    }
}
