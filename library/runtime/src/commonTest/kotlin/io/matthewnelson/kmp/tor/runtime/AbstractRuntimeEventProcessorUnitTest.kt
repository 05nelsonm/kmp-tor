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

import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorEvent
import io.matthewnelson.kmp.tor.runtime.internal.AbstractRuntimeEventProcessor
import kotlin.test.*

class AbstractRuntimeEventProcessorUnitTest {

    private class TestProcessor: AbstractRuntimeEventProcessor("static", emptySet(), emptySet()) {
        val sizeRuntime: Int get() = withRuntimeObservers { size }
        val sizeTor: Int get() = withObservers { size }

        fun <R: Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }
        fun destroy() { onDestroy() }
    }

    private val processor = TestProcessor()

    @Test
    fun givenObserver_whenAddRemove_thenIsAsExpected() {
        val observer = RuntimeEvent.LOG.DEBUG.observer {}
        processor.add(observer)
        assertEquals(1, processor.sizeRuntime)
        processor.add(observer)
        assertEquals(1, processor.sizeRuntime)
        processor.remove(observer)
        assertEquals(0, processor.sizeRuntime)
    }

    @Test
    fun givenObservers_whenRemoveAllByEvent_thenAreRemoved() {
        var invocations = 0
        val o1 = RuntimeEvent.LOG.DEBUG.observer { invocations++ }
        val o2 = RuntimeEvent.LOG.INFO.observer {}
        val o3 = RuntimeEvent.LOG.INFO.observer {}
        processor.add(o1, o2, o3, o3)
        assertEquals(3, processor.sizeRuntime)

        processor.removeAll(RuntimeEvent.LOG.INFO)
        assertEquals(1, processor.sizeRuntime)

        processor.notify(RuntimeEvent.LOG.DEBUG, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenObservers_whenRemoveMultiple_thenAreRemoved() {
        var invocations = 0
        val o1 = RuntimeEvent.LOG.DEBUG.observer { invocations++ }
        val o2 = RuntimeEvent.LOG.INFO.observer {}
        val o3 = RuntimeEvent.LOG.INFO.observer {}
        processor.add(o1, o2, o3)
        assertEquals(3, processor.sizeRuntime)

        processor.remove(o2, o3)
        assertEquals(1, processor.sizeRuntime)

        processor.notify(RuntimeEvent.LOG.DEBUG, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenTaggedObserver_whenRemoveByTag_thenAreRemoved() {
        var invocations = 0
        val o1 = RuntimeEvent.LOG.DEBUG.observer("test1") { invocations++ }
        val o2 = RuntimeEvent.LOG.INFO.observer("test2") {}
        val o3 = RuntimeEvent.LOG.WARN.observer("test2") {}
        processor.add(o1, o1, o2, o2, o3, o3)
        assertEquals(3, processor.sizeRuntime)

        processor.removeAll("test2")
        assertEquals(1, processor.sizeRuntime)

        // Is the proper tagged observer removed
        processor.notify(RuntimeEvent.LOG.DEBUG, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenBlankTag_whenObserver_thenTagIsNull() {
        assertNull(RuntimeEvent.Observer("  ", RuntimeEvent.LOG.DEBUG) { }.tag)
    }

    @Test
    fun givenStaticTag_whenRemove_thenDoesNothing() {
        processor.add(RuntimeEvent.LOG.DEBUG.observer("static") {})

        val nonStaticObserver = RuntimeEvent.LOG.DEBUG.observer("non-static") {}
        processor.add(nonStaticObserver)

        // should do nothing
        processor.removeAll("static")
        assertEquals(2, processor.sizeRuntime)

        // Should only remove the non-static observer
        processor.removeAll(RuntimeEvent.LOG.DEBUG)
        assertEquals(1, processor.sizeRuntime)

        // Should only remove the non-static observer
        processor.add(nonStaticObserver)
        assertEquals(2, processor.sizeRuntime)
        processor.removeAll(RuntimeEvent.LOG.DEBUG, RuntimeEvent.LOG.WARN)
        assertEquals(1, processor.sizeRuntime)

        // Should not remove the static observer
        processor.add(nonStaticObserver)
        assertEquals(2, processor.sizeRuntime)
        processor.clearObservers()
        assertEquals(1, processor.sizeRuntime)
    }

    @Test
    fun givenStaticObservers_whenOnDestroy_thenEvictsAll() {
        val observer = RuntimeEvent.LOG.DEBUG.observer("static") {}
        processor.add(observer)
        processor.add(TorEvent.BW.observer("static") {})

        processor.clearObservers()
        assertEquals(1, processor.sizeRuntime)
        assertEquals(1, processor.sizeTor)

        // Should also clear out static TorEvent observers
        processor.destroy()
        assertEquals(0, processor.sizeRuntime)
        assertEquals(0, processor.sizeTor)

        processor.add(observer)
        assertEquals(0, processor.sizeRuntime)
    }
}
