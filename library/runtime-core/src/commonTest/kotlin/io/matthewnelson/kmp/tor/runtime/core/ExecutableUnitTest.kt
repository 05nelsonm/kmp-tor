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
package io.matthewnelson.kmp.tor.runtime.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExecutableUnitTest {

    @Test
    fun givenExecuteOnce_whenIsOnce_thenThrows() {
        assertFailsWith<IllegalArgumentException> {
            Executable.Once.of(Executable.Once.of {})
        }
    }

    @Test
    fun givenExecuteOnce_whenIsNOOP_thenThrows() {
        assertFailsWith<IllegalArgumentException> {
            Executable.Once.of(Executable.noOp())
        }
    }

    @Test
    fun givenExecuteOnce_whenConcurrent_thenExecutesOnce() {
        var invocationExecute = 0
        val once = Executable.Once.of(concurrent = true) { invocationExecute++ }
        once.execute()
        once.execute()
        assertEquals(1, invocationExecute)
    }

    @Test
    fun givenExecuteOnce_whenNonConcurrent_thenExecutesOnce() {
        var invocationExecute = 0
        val once = Executable.Once.of { invocationExecute++ }
        once.execute()
        once.execute()
        assertEquals(1, invocationExecute)
    }
}
