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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.builder

import kotlin.test.Test
import kotlin.test.assertEquals

class IsolationFlagBuilderUnitTest {

    @Test
    fun givenSessionGroup_whenNegative_thenRemoves() {
        val flags = mutableSetOf<String>()

        IsolationFlagBuilder.configure(flags) { SessionGroup(5) }
        assertEquals("SessionGroup=5", flags.first())

        IsolationFlagBuilder.configure(flags) { SessionGroup(4) }
        assertEquals(1, flags.size)
        assertEquals("SessionGroup=4", flags.first())

        IsolationFlagBuilder.configure(flags) { SessionGroup(-1) }
        assertEquals(0, flags.size)
    }

    @Test
    fun givenFlags_whenFalse_thenAreRemoved() {
        val flags = mutableSetOf<String>()
        val allFlags = mutableSetOf<String>()

        var i = 0
        listOf<Triple<String, IsolationFlagBuilder.() -> Unit, IsolationFlagBuilder.() -> Unit>>(
            Triple("IsolateClientAddr", { IsolateClientAddr = true }, { IsolateClientAddr = false }),
            Triple("IsolateSOCKSAuth", { IsolateSOCKSAuth = true }, { IsolateSOCKSAuth = false }),
            Triple("IsolateClientProtocol", { IsolateClientProtocol = true }, { IsolateClientProtocol = false }),
            Triple("IsolateDestPort", { IsolateDestPort = true }, { IsolateDestPort = false }),
            Triple("IsolateDestAddr", { IsolateDestAddr = true }, { IsolateDestAddr = false }),
            Triple("KeepAliveIsolateSOCKSAuth", { KeepAliveIsolateSOCKSAuth = true }, { KeepAliveIsolateSOCKSAuth = false }),
        ).forEach { (expected, enable, disable) ->
            i++
            IsolationFlagBuilder.configure(allFlags) { enable() }

            IsolationFlagBuilder.configure(flags) { enable() }
            assertEquals(1, flags.size)
            assertEquals(expected, flags.first())

            IsolationFlagBuilder.configure(flags) { /* no action */ }
            assertEquals(1, flags.size)

            IsolationFlagBuilder.configure(flags) { disable() }
            assertEquals(0, flags.size)
        }

        assertEquals(i, allFlags.size)
    }
}
