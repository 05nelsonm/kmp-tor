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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.internal.process.TorDaemon
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.TimeSource

@Suppress("NOTHING_TO_INLINE")
internal expect inline fun TorRuntime.Environment.newRuntimeDispatcher(): CoroutineDispatcher

@Throws(Throwable::class)
internal expect fun File.setDirectoryPermissions()

// TODO: https://github.com/05nelsonm/kmp-process/issues/108
@Throws(Throwable::class)
internal expect fun TorDaemon.kill(pid: Int)

// No matter the Delay implementation (Coroutines Test library)
// Will delay the specified duration using a TimeSource.
internal suspend fun timedDelay(duration: Duration) {
    if (duration <= Duration.ZERO) return

    var remainder = duration
    val start = TimeSource.Monotonic.markNow()

    while (remainder > Duration.ZERO) {
        delay(remainder)
        remainder = duration - start.elapsedNow()
    }
}
