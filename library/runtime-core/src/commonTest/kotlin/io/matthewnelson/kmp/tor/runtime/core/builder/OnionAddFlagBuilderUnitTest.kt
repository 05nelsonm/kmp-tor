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

import io.matthewnelson.kmp.tor.runtime.core.builder.OnionAddBuilder.FlagBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class OnionAddFlagBuilderUnitTest {

    @Test
    fun givenFlags_whenFalse_thenAreRemoved() {
        val flags = mutableSetOf<String>()
        val allFlags = mutableSetOf<String>()
        var i = 0

        listOf<Triple<String, FlagBuilder.() -> Unit, FlagBuilder.() -> Unit>>(
            Triple("Detach", { Detach = true }, { Detach = false }),
            Triple("DiscardPK", { DiscardPK = true }, { DiscardPK = false }),
            Triple("MaxStreamsCloseCircuit", { MaxStreamsCloseCircuit = true }, { MaxStreamsCloseCircuit = false }),
        ).forEach { (expected, enable, disable) ->
            i++
            FlagBuilder.configure(allFlags) { enable() }

            FlagBuilder.configure(flags) { enable() }
            assertEquals(1, flags.size)
            assertEquals(expected, flags.first())

            FlagBuilder.configure(flags) { /* no action */ }
            assertEquals(1, flags.size)

            FlagBuilder.configure(flags) { disable() }
            assertEquals(0, flags.size)
        }

        assertEquals(i, allFlags.size)
    }
}
