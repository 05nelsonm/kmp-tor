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

import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LifecycleUnitTest {

    @Test
    fun givenDestroy_whenIsCompleting_thenIsDestroyedTrue() {
        val lifecycle = Lifecycle.of(UncaughtException.Handler.THROW)

        var invocationCompletion = 0
        lifecycle.invokeOnCompletion {
            invocationCompletion++
            assertTrue(lifecycle.isDestroyed())
        }

        assertFalse(lifecycle.isDestroyed())
        lifecycle.destroy()
        assertTrue(lifecycle.isDestroyed())
        assertEquals(1, invocationCompletion)
    }
}
