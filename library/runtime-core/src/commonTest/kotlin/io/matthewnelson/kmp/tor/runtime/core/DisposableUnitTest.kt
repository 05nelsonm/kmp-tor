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

class DisposableUnitTest {

    @Test
    fun givenOnce_whenIsOnce_thenThrows() {
        assertFailsWith<IllegalArgumentException> {
            Disposable.Once.of(Disposable.Once.of {})
        }
    }

    @Test
    fun givenOnce_whenIsNOOP_thenThrows() {
        assertFailsWith<IllegalArgumentException> {
            Disposable.Once.of(Disposable.noOp())
        }
    }

    @Test
    fun givenOnce_whenConcurrent_thenDisposesOnce() {
        var invocationExecute = 0
        val once = Disposable.Once.of(concurrent = true) { invocationExecute++ }
        once.dispose()
        once.dispose()
        assertEquals(1, invocationExecute)
    }

    @Test
    fun givenOnce_whenNonConcurrent_thenDisposesOnce() {
        var invocationExecute = 0
        val once = Disposable.Once.of { invocationExecute++ }
        once.dispose()
        once.dispose()
        assertEquals(1, invocationExecute)
    }
}
