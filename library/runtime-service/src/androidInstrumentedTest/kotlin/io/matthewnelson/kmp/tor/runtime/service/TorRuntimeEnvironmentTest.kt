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
package io.matthewnelson.kmp.tor.runtime.service

import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TorRuntimeEnvironmentTest {

    private val config = TorServiceConfig.Builder {}

    @Test
    fun givenContext_whenDefaultDirname_thenIsAsExpected() {
        val environment = config.newEnvironment(ResourceLoaderTorExec::getOrCreate)
        assertEquals("app_torservice", environment.workDirectory.name)
        assertEquals("torservice", environment.cacheDirectory.name)
    }

    @Test
    fun givenContext_whenDefaultDirnameWithConfigurationBlock_thenIsAsExpected() {
        val environment = config.newEnvironment(ResourceLoaderTorExec::getOrCreate)
        assertEquals("app_torservice", environment.workDirectory.name)
        assertEquals("torservice", environment.cacheDirectory.name)
    }

    @Test
    fun givenContext_whenBlankDirName_thenIsAsExpected() {
        val environment = config.newEnvironment(dirName = "    ", ResourceLoaderTorExec::getOrCreate)
        assertEquals("app_torservice", environment.workDirectory.name)
        assertEquals("torservice", environment.cacheDirectory.name)
    }

    @Test
    fun givenDispatchersMainAvailable_whenDefaultExecutor_thenIsExecutorMain() {
        config.newEnvironment(ResourceLoaderTorExec::getOrCreate) {
            assertIs<OnEvent.Executor.Main>(defaultEventExecutor)
        }
    }
}
