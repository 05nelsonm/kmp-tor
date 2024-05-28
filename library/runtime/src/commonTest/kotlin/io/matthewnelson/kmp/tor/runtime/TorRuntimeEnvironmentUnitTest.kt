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

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

@OptIn(ExperimentalKmpTorApi::class)
class TorRuntimeEnvironmentUnitTest {

    private fun torResource(dir: File) = object : ResourceInstaller<ResourceInstaller.Paths.Tor>(dir) {
        override fun install(): Paths.Tor { fail() }
    }

    @Test
    fun givenSameWorkDir_whenEnvironmentBuilder_thenReturnsSameInstance() {
        val work = "".toFile().absoluteFile.resolve("env_instance")

        val env1 = TorRuntime.Environment.Builder(work, work.resolve("cache")) { torResource(it) }
        val env2 = TorRuntime.Environment.Builder(work, work.resolve("cache2")) { torResource(it) }
        assertEquals(env1, env2)
        assertEquals(64, env1.fid.length)

        val env3 = TorRuntime.Environment.Builder(work.resolve("work"), work.resolve("cache")) { torResource(it) }
        assertNotEquals(env1, env3)

        val innerOuterWork = work.resolve("lambda")
        var envInner: TorRuntime.Environment? = null
        val envOuter = TorRuntime.Environment.Builder(
            innerOuterWork,
            innerOuterWork.resolve("outer"),
            { torResource(it) },
        ) {
            // Should be the expected instance
            envInner = TorRuntime.Environment.Builder(
                innerOuterWork,
                innerOuterWork.resolve("inner"),
                { torResource(it) },
            ) {

            }
        }

        assertEquals(innerOuterWork.resolve("inner"), envInner?.cacheDirectory)
        assertEquals(innerOuterWork.resolve("inner"), envOuter.cacheDirectory)
    }

    @Test
    fun givenProcessEnv_whenHOME_thenIsAlwaysWorkDirectory() {
        val workDir = "".toFile().absoluteFile.resolve("env_processENV")
        val env = TorRuntime.Environment.Builder(workDir, workDir.resolve("cache"), { torResource(it) }) {
            processEnv["HOME"] = "something"
        }
        assertEquals(env.processEnv["HOME"], workDir.path)
    }
}
