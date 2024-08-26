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
package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.internal.PortEphemeralIterator.Companion.iterator
import kotlin.test.*

class PortEphemeralIteratorUnitTest {

    @Test
    fun givenPortEphemeral_whenIterator_thenWorksAsExpected() {
        var i = Port.Ephemeral.MIN.toPortEphemeral().iterator(2)
        assertTrue(i.hasNext())
        assertEquals(1024, i.toPortEphemeral().value)
        i.next()
        assertEquals(1024, i.toPortEphemeral().value)
        assertTrue(i.hasNext())
        i.next()
        assertEquals(1025, i.toPortEphemeral().value)
        assertFalse(i.hasNext())
        assertFailsWith<NoSuchElementException> { i.next() }

        val p = Port.Ephemeral.MAX.toPortEphemeral()
        i = p.iterator(2)
        assertEquals(Port.Ephemeral.MAX, i.next())
        assertEquals(Port.Ephemeral.MAX, i.toPortEphemeral().value)
        assertEquals(Port.Ephemeral.MIN, i.next())
        assertEquals(Port.Ephemeral.MIN, i.toPortEphemeral().value)
        assertFalse(i.hasNext())

        assertFailsWith<IllegalArgumentException> { p.iterator(0) }
        assertFailsWith<IllegalArgumentException> { p.iterator(1_001) }
    }
}
