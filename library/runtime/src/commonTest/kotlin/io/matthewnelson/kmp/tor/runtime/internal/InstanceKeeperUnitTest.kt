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

import kotlin.test.Test
import kotlin.test.assertEquals

class InstanceKeeperUnitTest {

    private class TestKeeper: InstanceKeeper<Int, String>() {

        fun getOrCreate(
            key: Int,
            block: (others: List<Pair<Int, String>>) -> String,
        ): String = getOrCreateInstance(key, block)
    }

    @Test
    fun givenKey_whenQueriedWithExistingKey_thenReturnsExistingInstance() {
        val k = TestKeeper()

        k.getOrCreate(5) { "5" }
        assertEquals("5", k.getOrCreate(5) { "555" })
    }

    @Test
    fun givenOtherInstances_whenOtherInstanceReturned_thenIsNotStoredWithNewKey() {
        val k = TestKeeper()

        k.getOrCreate(5) { "5" }

        val result = k.getOrCreate(6) { others ->
            others.find { it.second == "5" }?.second ?: throw IllegalStateException()
        }

        assertEquals("5", result)
        assertEquals("6", k.getOrCreate(6) { "6" })
    }
}
