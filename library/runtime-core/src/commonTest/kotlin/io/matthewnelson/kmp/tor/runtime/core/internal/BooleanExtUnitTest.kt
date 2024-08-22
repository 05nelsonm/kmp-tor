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
@file:Suppress("PrivatePropertyName")

package io.matthewnelson.kmp.tor.runtime.core.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BooleanExtUnitTest {

    private val flags = LinkedHashSet<String>(1, 1.0f)
    private val NULL: Boolean? get() = null

    @Test
    fun givenConfigureFlags_whenConditionTrue_thenAddsFlag() {
        true.configure(flags, "TRUE")
        assertEquals(1, flags.size)
        assertTrue(flags.contains("TRUE"))
    }

    @Test
    fun givenConfigureFlags_whenConditionFalse_thenRemovesFlag() {
        // setup
        true.configure(flags, "FALSE")
        assertEquals(1, flags.size)
        assertTrue(flags.contains("FALSE"))

        false.configure(flags, "FALSE")
        assertTrue(flags.isEmpty())
    }

    @Test
    fun givenConfigureFlags_whenConditionNull_thenDoesNothing() {
        true.configure(flags, "NULL")
        assertEquals(1, flags.size)
        assertTrue(flags.contains("NULL"))

        assertNull(NULL)
        NULL.configure(flags, "NULL")
        assertEquals(1, flags.size)
        assertTrue(flags.contains("NULL"))
    }
}
