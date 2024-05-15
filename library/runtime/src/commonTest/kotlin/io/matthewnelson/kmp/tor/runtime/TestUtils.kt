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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import kotlinx.coroutines.*
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

object TestUtils {

    suspend fun <T: Action.Processor> T.ensureStoppedOnTestCompletion(): T {
        currentCoroutineContext().job.invokeOnCompletion {
            enqueue(Action.StopDaemon, {}, {})
        }

        withContext(Dispatchers.Default) { delay(100.milliseconds) }

        return this
    }

    fun List<Lifecycle.Event>.assertDoesNotContain(
        className: String,
        name: Lifecycle.Event.Name,
        fid: FileID? = null,
    ) {
        var error: AssertionError? = null
        try {
            assertContains(className, name, fid)
            error = AssertionError("LCEs contained $name for $className${fid?.let { "[fid=${it.fidEllipses}]" } ?: ""}")
        } catch (_: AssertionError) {
            // pass
        }

        error?.let { throw it }
    }

    fun List<Lifecycle.Event>.assertContains(
        className: String,
        name: Lifecycle.Event.Name,
        fid: FileID? = null,
    ) {
        for (lce in this) {
            if (lce.className != className) continue
            if (fid != null) {
                if (lce.fid != fid.fidEllipses) continue
            }

            if (lce.name == name) return
        }

        fail("LCEs did not contain $name for $className${fid?.let { "[fid=${it.fidEllipses}]" } ?: ""}")
    }
}
