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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.internal.PortProxyIterator.Companion.iterator
import kotlin.test.*

class PortProxyIteratorUnitTest {

    @Test
    fun givenPortProxy_whenIterator_thenWorksAsExpected() {
        var i = Port.Proxy.MIN.toPortProxy().iterator(2)
        assertTrue(i.hasNext())
        assertEquals(1024, i.toPortProxy().value)
        i.next()
        assertEquals(1024, i.toPortProxy().value)
        assertTrue(i.hasNext())
        i.next()
        assertEquals(1025, i.toPortProxy().value)
        assertFalse(i.hasNext())
        assertFailsWith<NoSuchElementException> { i.next() }

        val p = Port.Proxy.MAX.toPortProxy()
        i = p.iterator(2)
        assertEquals(Port.Proxy.MAX, i.next())
        assertEquals(Port.Proxy.MAX, i.toPortProxy().value)
        assertEquals(Port.Proxy.MIN, i.next())
        assertEquals(Port.Proxy.MIN, i.toPortProxy().value)
        assertFalse(i.hasNext())

        assertFailsWith<IllegalArgumentException> { p.iterator(0) }
        assertFailsWith<IllegalArgumentException> { p.iterator(1_001) }
    }
}
