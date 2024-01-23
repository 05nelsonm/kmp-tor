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
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

class TorRuntimeEnvironmentUnitTest {

    @Test
    fun givenSameWorkDir_whenEnvironmentBuilder_thenReturnsSameInstance() {
        val work = "".toFile().absoluteFile
        val torResource = object : ResourceInstaller<ResourceInstaller.Paths.Tor>(work) {
            override fun install(): Paths.Tor { fail() }
        }

        val env1 = TorRuntime.Environment.Builder(work, work.resolve("cache")) { torResource }
        val env2 = TorRuntime.Environment.Builder(work, work.resolve("cache2")) { torResource }
        assertEquals(env1, env2)

        val env3 = TorRuntime.Environment.Builder(work.resolve("work"), work.resolve("cache")) { torResource }
        assertNotEquals(env1, env3)
    }
}
