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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.toFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EnvironmentKeyUnitTest {

    private val key = EnvironmentKey("work".toFile(), "cache".toFile())

    @Test
    fun givenKey_whenSameWorkDirDifferentCacheDir_thenIsEqual() {
        val copy = key.copy(cache = "cache2".toFile())
        assertEquals(key.work, copy.work)
        assertNotEquals(key.cache, copy.cache)
        assertEquals(key, copy)
    }

    @Test
    fun givenKey_whenSameCacheDirDifferentWorkDir_thenIsEqual() {
        val copy = key.copy(work = "work2".toFile())
        assertEquals(key.cache, copy.cache)
        assertNotEquals(key.work, copy.work)
        assertEquals(key, copy)
    }

    @Test
    fun givenKey_whenCacheDirWorkDirDifferent_thenIsNotEqual() {
        val other = EnvironmentKey("work2".toFile(), "cache2".toFile())
        assertNotEquals(key.work, other.work)
        assertNotEquals(key.cache, other.cache)
        assertNotEquals(key, other)
    }

    private fun EnvironmentKey.copy(work: File = this.work, cache: File = this.cache): EnvironmentKey {
        return EnvironmentKey(work, cache)
    }
}
