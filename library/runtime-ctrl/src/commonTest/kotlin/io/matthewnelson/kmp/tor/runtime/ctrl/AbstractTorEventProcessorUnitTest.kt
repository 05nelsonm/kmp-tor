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
package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class AbstractTorEventProcessorUnitTest {

    private class TestProcessor: AbstractTorEventProcessor("static", emptySet()) {
        val size: Int get() = registered()
        fun notify(event: TorEvent, output: String) { event.notifyObservers(output) }
        fun destroy() { onDestroy() }
        fun <T> noOpSet(): MutableSet<T> = noOpMutableSet()
    }

    private val processor = TestProcessor()

    @Test
    fun givenObserver_whenAddRemove_thenIsAsExpected() {
        val observer = TorEvent.CIRC.observer {}
        processor.add(observer)
        assertEquals(1, processor.size)
        processor.add(observer)
        assertEquals(1, processor.size)
        processor.remove(observer)
        assertEquals(0, processor.size)
    }

    @Test
    fun givenObservers_whenRemoveAllByEvent_thenAreRemoved() {
        var invocations = 0
        val o1 = TorEvent.CIRC.observer { invocations++ }
        val o2 = TorEvent.BW.observer {}
        val o3 = TorEvent.BW.observer {}
        processor.add(o1, o2, o3, o3)
        assertEquals(3, processor.size)

        processor.removeAll(TorEvent.BW)
        assertEquals(1, processor.size)

        processor.notify(TorEvent.CIRC, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenObservers_whenRemoveMultiple_thenAreRemoved() {
        var invocations = 0
        val o1 = TorEvent.CIRC.observer { invocations++ }
        val o2 = TorEvent.BW.observer {}
        val o3 = TorEvent.BW.observer {}
        processor.add(o1, o2, o3)
        assertEquals(3, processor.size)

        processor.remove(o2, o3)
        assertEquals(1, processor.size)

        processor.notify(TorEvent.CIRC, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenTaggedObserver_whenRemoveByTag_thenAreRemoved() {
        var invocations = 0
        val o1 = TorEvent.CIRC.observer("test1") { invocations++ }
        val o2 = TorEvent.BW.observer("test2") {}
        val o3 = TorEvent.HS_DESC.observer("test2") {}
        processor.add(o1, o1, o2, o2, o3, o3)
        assertEquals(3, processor.size)

        processor.removeAll("test2")
        assertEquals(1, processor.size)

        // Is the proper tagged observer removed
        processor.notify(TorEvent.CIRC, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenBlankTag_whenObserver_thenTagIsNull() {
        assertNull(TorEvent.Observer("  ", TorEvent.CIRC) { }.tag)
    }

    @Test
    fun givenStaticTag_whenRemove_thenDoesNothing() {
        processor.add(TorEvent.BW.observer("static") {})

        val nonStaticObserver = TorEvent.BW.observer("non-static") {}
        processor.add(nonStaticObserver)

        // should do nothing
        processor.removeAll("static")
        assertEquals(2, processor.size)

        // Should only remove the non-static observer
        processor.removeAll(TorEvent.BW)
        assertEquals(1, processor.size)

        // Should only remove the non-static observer
        processor.add(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.removeAll(TorEvent.BW, TorEvent.ADDRMAP)
        assertEquals(1, processor.size)

        // Should not remove the static observer
        processor.add(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.clearObservers()
        assertEquals(1, processor.size)
    }

    @Test
    fun givenStaticObservers_whenOnDestroy_thenEvictsAll() {
        val observer = TorEvent.BW.observer("static") {}
        processor.add(observer)
        assertEquals(1, processor.size)

        processor.clearObservers()
        assertEquals(1, processor.size)

        processor.destroy()
        assertEquals(0, processor.size)

        processor.add(observer)
        assertEquals(0, processor.size)
    }

    @Test
    fun givenNoOpMutableSet_whenModified_thenDoesNothing() {
        val set = processor.noOpSet<Any>()
        assertEquals(0, set.size)
        assertEquals(0, set.apply { add("") }.size)
        assertFalse(set.add(""))
        assertTrue(set.addAll(emptyList()))
        assertFalse(set.addAll(listOf("", "a")))
        assertTrue(set.retainAll(emptySet()))
        assertFalse(set.retainAll(listOf("", "a")))
        assertTrue(set.removeAll(emptySet()))
        assertFalse(set.removeAll(listOf("", "a")))
        assertFalse(set.remove("a"))
        assertTrue(set.isEmpty())
        assertFalse(set.contains(""))
        assertTrue(set.containsAll(emptySet()))
        assertFalse(set.containsAll(listOf("", "a")))

        // does nothing
        set.clear()

        val iterator = set.iterator()
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        assertFailsWith<IllegalStateException> { iterator.remove() }
    }
}
