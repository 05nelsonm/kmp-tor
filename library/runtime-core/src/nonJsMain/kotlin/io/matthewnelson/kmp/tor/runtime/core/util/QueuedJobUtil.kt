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
@file:JvmName("QueuedJobUtil")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.time.Duration.Companion.milliseconds

@InternalKmpTorApi
public inline fun <Response: Any> QueuedJob.awaitSync(
    success: () -> Response?,
    failure: () -> Throwable?,
    noinline cancellation: (() -> CancellationException?)?,
): Response {
    var callback = cancellation

    while (true) {
        success()?.let { return it }
        failure()?.let { throw it }

        Blocking.threadSleep(25.milliseconds)

        // No possibility of cancellation. Skip.
        if (callback == null) continue

        val exception = try {
            callback() ?: continue
        } catch (t: Throwable) {
            if (t is CancellationException) t else CancellationException(t)
        }

        // de-reference callback as to not invoke it anymore
        // in the event that job is unable to be cancelled (e.g.
        // is in State.Executing), will just continue to loop
        // until execution completes.
        callback = null
        cancel(exception)
    }
}
