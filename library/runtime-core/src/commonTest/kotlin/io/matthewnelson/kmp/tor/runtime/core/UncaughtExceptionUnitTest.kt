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

import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import kotlin.test.*

class UncaughtExceptionUnitTest {

    @Test
    fun givenHandler_whenEmbeddedWithSuppressionCalls_thenReturnsSameInstance() {
        UncaughtException.Handler.THROW.withSuppression {
            val handler1 = this
            withSuppression {
                val handler2 = this
                tryCatch("") {
                    assertEquals(handler1, handler2)
                }
            }
        }
    }

    @Test
    fun givenHandler_whenWithSuppression_thenAddsMultipleExceptions() {
        val exceptions = mutableListOf<UncaughtException>()
        UncaughtException.Handler { exceptions.add(it) }.withSuppression {
            repeat(3) { i -> tryCatch(i) { throw IllegalStateException("$i") } }
        }

        assertEquals(1, exceptions.size)
        assertEquals(2, exceptions.first().suppressedExceptions.size)
    }
}
