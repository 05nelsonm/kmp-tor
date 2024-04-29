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

import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun TorRuntime.Environment.newRuntimeDispatcher(): CoroutineDispatcher {
    val threadNo = AtomicLong()
    val executor = Executors.newSingleThreadExecutor { runnable ->
        val t = Thread(runnable, "Tor-$fid-${threadNo.incrementAndGet()}")
        t.isDaemon = true
        t.priority = Thread.MAX_PRIORITY
        t
    }
    return executor.asCoroutineDispatcher()
}
