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
package io.matthewnelson.kmp.tor.runtime.core.builder

import kotlin.test.Test
import kotlin.test.assertEquals

class UnixFlagBuilderUnitTest {

    @Test
    fun givenRelaxDirModeCheck_whenNotController_thenDoesNotAdd() {
        val flags = mutableSetOf<String>()

        UnixFlagBuilder.configure(isControl = false, flags) { RelaxDirModeCheck = true }
        assertEquals(0, flags.size)
    }

    @Test
    fun givenRelaxDirModeCheck_whenController_thenDoesAdd() {
        val flags = mutableSetOf<String>()

        UnixFlagBuilder.configure(isControl = true, flags) { RelaxDirModeCheck = true }
        assertEquals(1, flags.size)
    }

    @Test
    fun givenFlags_whenFalse_thenAreRemoved() {
        val flags = mutableSetOf<String>()
        val allFlags = mutableSetOf<String>()

        var i = 0
        listOf<Triple<String, UnixFlagBuilder.() -> Unit, UnixFlagBuilder.() -> Unit>>(
            Triple("GroupWritable", { GroupWritable = true }, { GroupWritable = false }),
            Triple("WorldWritable", { WorldWritable = true }, { WorldWritable = false }),
            Triple("RelaxDirModeCheck", { RelaxDirModeCheck = true }, { RelaxDirModeCheck = false }),
        ).forEach { (expected, enable, disable) ->
            i++
            UnixFlagBuilder.configure(isControl = true, allFlags) { enable() }

            UnixFlagBuilder.configure(isControl = true, flags) { enable() }
            assertEquals(1, flags.size)
            assertEquals(expected, flags.first())

            UnixFlagBuilder.configure(isControl = true, flags) { /* no action */ }
            assertEquals(1, flags.size)

            UnixFlagBuilder.configure(isControl = true, flags) { disable() }
            assertEquals(0, flags.size)
        }

        assertEquals(i, allFlags.size)
    }
}
