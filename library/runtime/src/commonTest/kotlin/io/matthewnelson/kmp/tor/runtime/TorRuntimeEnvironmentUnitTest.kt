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

// TODO: RE-IMPLEMENT
//import io.matthewnelson.kmp.file.*
//import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
//import io.matthewnelson.kmp.tor.common.api.ResourceLoader
//import io.matthewnelson.kmp.tor.runtime.test.LOADER
//import kotlin.test.*
//
//class TorRuntimeEnvironmentUnitTest {
//
//    @Test
//    fun givenResourceLoaderNoExec_whenEnvironmentBuilder_thenReturnsSameInstance() {
//        if (LOADER is ResourceLoader.Tor.Exec) {
//            println("Skipping...")
//            return
//        }
//
//        val rootDir = "".toFile().absoluteFile.resolve("env_instance_noexec")
//
//        val env1 = TorRuntime.Environment.Builder(rootDir.resolve("work1"), rootDir.resolve("cache1")) { LOADER }
//        val env2 = TorRuntime.Environment.Builder(rootDir.resolve("work2"), rootDir.resolve("cache2")) { LOADER }
//        assertEquals(env1, env2)
//    }
//
//    @Test
//    fun givenSameWorkDir_whenEnvironmentBuilder_thenReturnsSameInstance() {
//        if (LOADER is ResourceLoader.Tor.NoExec) {
//            println("Skipping...")
//            return
//        }
//
//        val rootDir = "".toFile().absoluteFile.resolve("env_instance")
//
//        val env1 = TorRuntime.Environment.Builder(rootDir.resolve("work"), rootDir.resolve("cache")) { LOADER }
//        val env2 = TorRuntime.Environment.Builder(rootDir.resolve("work"), rootDir.resolve("cache2")) { LOADER }
//        assertEquals(env1, env2)
//        assertEquals(64, env1.fid.length)
//
//        val env3 = TorRuntime.Environment.Builder(rootDir.resolve("work3"), rootDir.resolve("cache3")) { LOADER }
//        assertNotEquals(env1, env3)
//
//        val innerOuterWork = rootDir.resolve("lambda")
//        var envInner: TorRuntime.Environment? = null
//        val envOuter = TorRuntime.Environment.Builder(
//            innerOuterWork,
//            innerOuterWork.resolve("outer"),
//            { LOADER },
//        ) {
//            // Should be the expected instance
//            envInner = TorRuntime.Environment.Builder(
//                innerOuterWork,
//                innerOuterWork.resolve("inner"),
//                { LOADER },
//            ) {
//
//            }
//        }
//
//        assertEquals(innerOuterWork.resolve("inner"), envInner?.cacheDirectory)
//        assertEquals(innerOuterWork.resolve("inner"), envOuter.cacheDirectory)
//    }
//
//    @Test
//    fun givenMultipleEnvironments_whenSameInstallationDir_thenUsesSameResourceInstaller() {
//        if (LOADER is ResourceLoader.Tor.NoExec) {
//            println("Skipping...")
//            return
//        }
//
//        val rootDir = "".toFile().absoluteFile.resolve("env_installer")
//        val env1 = TorRuntime.Environment.Builder(
//            rootDir.resolve("work1"),
//            rootDir.resolve("cache1"),
//            loader = { LOADER }
//        ) {
//            resourceDir = rootDir
//        }
//        val env2 = TorRuntime.Environment.Builder(
//            rootDir.resolve("work2"),
//            rootDir.resolve("cache2"),
//            loader = { LOADER }
//        ) {
//            resourceDir = rootDir
//        }
//
//        assertNotEquals(env1, env2)
//        assertEquals(env1.loader, env2.loader)
//    }
//
//    @Test
//    fun givenBuilder_whenWorkDirectorySameAsCache_thenThrowsException() {
//        assertFailsWith<IllegalArgumentException> {
//            TorRuntime.Environment.Builder("".toFile(), "".toFile()) { LOADER }
//        }
//    }
//}
