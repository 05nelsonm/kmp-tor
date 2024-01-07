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
import io.matthewnelson.kmp.tor.runtime.api.TorEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalKmpTorApi::class)
class AbstractTorEventProcessorUnitTest {

    private class TestProcessor: AbstractTorEventProcessor(emptySet()) {
        val size: Int get() = withObservers { size }
        fun notify(event: TorEvent, output: String) { notifyObservers(event, output) }
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
    fun givenObservers_whenRemoveByEvent_thenAreRemoved() {
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
    fun givenObservers_whenRemoveAll_thenAreRemoved() {
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
}
