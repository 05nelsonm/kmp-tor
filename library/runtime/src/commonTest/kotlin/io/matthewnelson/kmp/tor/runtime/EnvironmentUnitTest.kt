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

import io.matthewnelson.kmp.file.absoluteFile
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.runtime.test.runTorTest
import io.matthewnelson.kmp.tor.runtime.test.testLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class EnvironmentUnitTest {

    @Test
    fun givenResourceLoaderNoExec_whenEnvironmentBuilder_thenReturnsSameInstance() = runTorTest { runtime ->
        if (runtime.isLoaderExec) {
            println("Skipping...")
            return@runTorTest
        }

        val env = runtime.environment()
        val env2 = TorRuntime.Environment.Builder(
            workDirectory = (env.workDirectory.path + "2").toFile(),
            cacheDirectory = (env.cacheDirectory.path + "2").toFile(),
            loader = ::testLoader,
        )

        assertEquals(env, env2)
    }

    @Test
    fun givenSameWorkDir_whenEnvironmentBuilder_thenReturnsSameInstance() = runTorTest { runtime ->
        if (!runtime.isLoaderExec) {
            println("Skipping...")
            return@runTorTest
        }

        val rootDir = "".toFile().absoluteFile.resolve("env_instance")

        val env1 = TorRuntime.Environment.Builder(rootDir.resolve("work"), rootDir.resolve("cache"), ::testLoader)
        val env2 = TorRuntime.Environment.Builder(rootDir.resolve("work"), rootDir.resolve("cache2"), ::testLoader)
        assertEquals(env1, env2)
        assertEquals(64, env1.fid.length)

        val env3 = TorRuntime.Environment.Builder(rootDir.resolve("work3"), rootDir.resolve("cache3"), ::testLoader)
        assertNotEquals(env1, env3)

        val innerOuterWork = rootDir.resolve("lambda")
        var envInner: TorRuntime.Environment? = null
        val envOuter = TorRuntime.Environment.Builder(
            innerOuterWork,
            innerOuterWork.resolve("outer"),
            ::testLoader,
        ) {

            // Should be the expected instance
            envInner = TorRuntime.Environment.Builder(
                innerOuterWork,
                innerOuterWork.resolve("inner"),
                ::testLoader,
            ) {

            }
        }

        assertEquals(innerOuterWork.resolve("inner"), envInner?.cacheDirectory)
        assertEquals(innerOuterWork.resolve("inner"), envOuter.cacheDirectory)
    }

    @Test
    fun givenBuilder_whenWorkDirectorySameAsCache_thenThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            TorRuntime.Environment.Builder("".toFile(), "".toFile(), ::testLoader)
        }
    }
}
