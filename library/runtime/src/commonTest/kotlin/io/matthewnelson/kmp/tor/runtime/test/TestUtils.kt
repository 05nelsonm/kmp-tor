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
package io.matthewnelson.kmp.tor.runtime.test

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import kotlinx.coroutines.*
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

object TestUtils {

    fun testEnv(
        dirName: String,
        block: ThisBlock<TorRuntime.Environment.Builder> = ThisBlock {}
    ): TorRuntime.Environment = TorRuntime.Environment.Builder(
        workDirectory = SysTempDir.resolve("kmp_tor_test/$dirName/work"),
        cacheDirectory = SysTempDir.resolve("kmp_tor_test/$dirName/cache"),
        installer = { dir -> TorResources(dir) },
        block = block
    ).also { it.debug = true }

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
