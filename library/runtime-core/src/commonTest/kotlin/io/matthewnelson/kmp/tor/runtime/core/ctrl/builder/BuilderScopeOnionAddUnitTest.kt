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
package io.matthewnelson.kmp.tor.runtime.core.ctrl.builder

import io.matthewnelson.kmp.tor.runtime.core.ctrl.builder.BuilderScopeOnionAdd.FlagsBuilder
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuilderScopeOnionAddUnitTest {

    @Test
    fun givenFlags_whenFalse_thenAreRemoved() {
        val flags = LinkedHashSet<String>()
        val allFlags = LinkedHashSet<String>()
        var i = 0

        listOf<Triple<String, FlagsBuilder.() -> Unit, FlagsBuilder.() -> Unit>>(
            Triple("Detach", { Detach = true }, { Detach = false }),
            Triple("DiscardPK", { DiscardPK = true }, { DiscardPK = false }),
            Triple("MaxStreamsCloseCircuit", { MaxStreamsCloseCircuit = true }, { MaxStreamsCloseCircuit = false }),
        ).forEach { (expected, enable, disable) ->
            i++
            FlagsBuilder.configure(allFlags) { enable() }

            FlagsBuilder.configure(flags) { enable() }
            assertEquals(1, flags.size)
            assertEquals(expected, flags.first())

            FlagsBuilder.configure(flags) { /* no action */ }
            assertEquals(1, flags.size)

            FlagsBuilder.configure(flags) { disable() }
            assertEquals(0, flags.size)
        }

        assertEquals(i, allFlags.size)
    }

    @Test
    fun givenAddExisting_whenCmdCreated_thenFlagDiscardPKIsPresentByDefault() {
        val cmd = TorCmd.Onion.Add.existing(ByteArray(64).toED25519_V3PrivateKey()) {}
        assertTrue(cmd.flags.contains("DiscardPK"))
    }

    @Test
    fun givenCreateNew_whenCmdCreated_thenFlagDiscardPKIsNotPresentByDefault() {
        val cmd = TorCmd.Onion.Add.new(ED25519_V3) {}
        assertFalse(cmd.flags.contains("DiscardPK"))
    }
}
