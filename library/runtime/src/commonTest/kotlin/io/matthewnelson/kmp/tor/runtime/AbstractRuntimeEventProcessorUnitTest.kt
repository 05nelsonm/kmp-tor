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

import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.internal.AbstractRuntimeEventProcessor
import kotlin.test.*

class AbstractRuntimeEventProcessorUnitTest {

    private companion object {
        private const val STATIC_TAG = "TAG_STATIC_1234"
    }

    private class TestProcessor: AbstractRuntimeEventProcessor(STATIC_TAG, emptySet(), emptySet()) {
        val size: Int get() = registered()
        fun <R: Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }
        fun notify(event: TorEvent, output: String) { event.notifyObservers(output) }
        fun destroy() { onDestroy() }
    }

    private val processor = TestProcessor()

    @Test
    fun givenObserver_whenAddRemove_thenIsAsExpected() {
        val observer = RuntimeEvent.LOG.DEBUG.observer {}
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
        val o1 = RuntimeEvent.LOG.DEBUG.observer { invocations++ }
        val o2 = RuntimeEvent.LOG.INFO.observer {}
        val o3 = RuntimeEvent.LOG.INFO.observer {}
        processor.add(o1, o2, o3, o3)
        assertEquals(3, processor.size)

        processor.removeAll(RuntimeEvent.LOG.INFO)
        assertEquals(1, processor.size)

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
        assertEquals(3, processor.size)

        processor.remove(o2, o3)
        assertEquals(1, processor.size)

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
        assertEquals(3, processor.size)

        processor.removeAll("test2")
        assertEquals(1, processor.size)

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
        processor.add(RuntimeEvent.LOG.DEBUG.observer(STATIC_TAG) {})

        val nonStaticObserver = RuntimeEvent.LOG.DEBUG.observer("non-static") {}
        processor.add(nonStaticObserver)

        // should do nothing
        processor.removeAll(STATIC_TAG)
        assertEquals(2, processor.size)

        // Should only remove the non-static observer
        processor.removeAll(RuntimeEvent.LOG.DEBUG)
        assertEquals(1, processor.size)

        // Should only remove the non-static observer
        processor.add(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.removeAll(RuntimeEvent.LOG.DEBUG, RuntimeEvent.LOG.WARN)
        assertEquals(1, processor.size)

        // Should not remove the static observer
        processor.add(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.clearObservers()
        assertEquals(1, processor.size)
    }

    @Test
    fun givenStaticObservers_whenOnDestroy_thenEvictsAll() {
        val observer = RuntimeEvent.LOG.DEBUG.observer(STATIC_TAG) {}
        processor.add(observer)
        processor.add(TorEvent.BW.observer(STATIC_TAG) {})

        processor.clearObservers()
        assertEquals(2, processor.size)

        // Should also clear out static TorEvent observers
        processor.destroy()
        assertEquals(0, processor.size)

        processor.add(observer)
        assertEquals(0, processor.size)
    }

    @Test
    fun givenUncaughtException_whenRedirectedToLogError_thenIsAsExpected() {
        processor.add(RuntimeEvent.LOG.DEBUG.observer(STATIC_TAG) { throw IllegalStateException() })
        processor.add(TorEvent.INFO.observer(STATIC_TAG) { throw IllegalStateException() })

        var invocations = 0
        processor.add(RuntimeEvent.LOG.ERROR.observer { t ->
            invocations++
            assertIs<UncaughtException>(t)
            assertIs<IllegalStateException>(t.cause)

            // ensure observer.toString (which is utilized
            // as context) does not leak the static tag value.
            assertFalse(t.message.contains(STATIC_TAG))
            assertTrue(t.message.contains("tag=STATIC"))

            // should be swallowed
            throw t
        })

        processor.notify(RuntimeEvent.LOG.DEBUG, "something")
        assertEquals(1, invocations)

        processor.notify(TorEvent.INFO, "something")
        assertEquals(2, invocations)
    }
}
