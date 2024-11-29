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

import io.matthewnelson.kmp.file.ANDROID
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.OSHost
import io.matthewnelson.kmp.tor.common.core.OSInfo
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun TorRuntime.Environment.newRuntimeDispatcher(): CoroutineDispatcher {
    val threadNo = AtomicLong()
    val name = "Tor[$fidEllipses]"
    val executor = Executors.newSingleThreadExecutor { runnable ->
        val t = Thread(runnable, "$name-${threadNo.incrementAndGet()}")
        t.isDaemon = true
        t.priority = Thread.MAX_PRIORITY
        t
    }
    return executor.asCoroutineDispatcher()
}

@Throws(Throwable::class)
internal actual fun File.setDirectoryPermissions() {
    @OptIn(InternalKmpTorApi::class)
    if (OSInfo.INSTANCE.osHost is OSHost.Windows) return
    if (SysDirSep == '\\') return

    val hasNioPath = ANDROID.SDK_INT?.let { it >= 26 } ?: true

    if (!hasNioPath) {
        setReadable(false, /* ownerOnly */ false)
        setWritable(false, /* ownerOnly */ false)
        setExecutable(false, /* ownerOnly */ false)

        setReadable(true, /* ownerOnly */ true)
        setWritable(true, /* ownerOnly */ true)
        setExecutable(true, /* ownerOnly */ true)
        return
    }

    try {
        @Suppress("NewApi")
        java.nio.file.Files.setPosixFilePermissions(
            kotlin.io.path.Path(path),
            mutableSetOf(
                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
            )
        )
    } catch (_: UnsupportedOperationException) {}
}
