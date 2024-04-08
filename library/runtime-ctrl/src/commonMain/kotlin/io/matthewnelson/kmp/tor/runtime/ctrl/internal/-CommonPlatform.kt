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

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import kotlin.coroutines.cancellation.CancellationException

@Suppress("NOTHING_TO_INLINE")
@Throws(FileNotFoundException::class, UnsupportedOperationException::class)
internal inline fun File.checkUnixSockedSupport() {
    val path = this

    TorConfig.__ControlPort.Builder {
        asUnixSocket { file = path }
    }

    if (exists()) return
    throw FileNotFoundException(path.toString())
}

// Should only be invoked from OUTSIDE a lock lambda, and on
// a local instance of MutableList containing the jobs to cancel.
// as to not encounter ConcurrentModificationException.
@Throws(ConcurrentModificationException::class)
internal fun <T: QueuedJob> MutableList<T>.cancelAndClearAll(
    cause: CancellationException?,
    handler: UncaughtException.Handler,
) {
    if (isEmpty()) return

    handler.withSuppression {
        while (isNotEmpty()) {
            val job = removeFirst()
            tryCatch(job) { job.cancel(cause) }
        }
    }
}
