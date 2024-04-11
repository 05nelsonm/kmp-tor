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
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class AbstractTorEventProcessorUnitTest {

    private class TestProcessor(
        handler: UncaughtException.Handler = UncaughtException.Handler.THROW
    ): AbstractTorEventProcessor("static", emptySet(), OnEvent.Executor.Unconfined) {
        override val handler = HandlerWithContext(handler)
        val size: Int get() = registered()
        fun notify(event: TorEvent, output: String) { event.notifyObservers(output) }
        fun destroy() { onDestroy() }
        fun <T: Any> noOpSet(): MutableSet<T> = noOpMutableSet()
    }

    private val processor = TestProcessor()

    @Test
    fun givenObserver_whenAddRemove_thenIsAsExpected() {
        val observer = TorEvent.CIRC.observer {}
        processor.subscribe(observer)
        assertEquals(1, processor.size)
        processor.subscribe(observer)
        assertEquals(1, processor.size)
        processor.unsubscribe(observer)
        assertEquals(0, processor.size)
    }

    @Test
    fun givenObservers_whenNotified_thenIsOutsideOfLock() {
        var observer: TorEvent.Observer? = null
        observer = TorEvent.CIRC.observer {
            // If observers are notified while holding
            // the processor's lock, this would lock up
            // b/c removal also obtains the lock to modify
            // the Set
            processor.unsubscribe(observer!!)
        }
        processor.subscribe(observer)
        assertEquals(1, processor.size)
        processor.notify(observer.event, "")
        assertEquals(0, processor.size)
    }

    @Test
    fun givenObservers_whenRemoveAllByEvent_thenAreRemoved() {
        var invocations = 0
        val o1 = TorEvent.CIRC.observer { invocations++ }
        val o2 = TorEvent.BW.observer {}
        val o3 = TorEvent.BW.observer {}
        processor.subscribe(o1, o2, o3, o3)
        assertEquals(3, processor.size)

        processor.unsubscribeAll(TorEvent.BW)
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
        processor.subscribe(o1, o2, o3)
        assertEquals(3, processor.size)

        processor.unsubscribe(o2, o3)
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
        processor.subscribe(o1, o1, o2, o2, o3, o3)
        assertEquals(3, processor.size)

        processor.unsubscribeAll("test2")
        assertEquals(1, processor.size)

        // Is the proper tagged observer removed
        processor.notify(TorEvent.CIRC, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenBlankTag_whenObserver_thenTagIsNull() {
        assertNull(TorEvent.Observer(TorEvent.CIRC, "  ", null) { }.tag)
    }

    @Test
    fun givenStaticTag_whenRemove_thenDoesNothing() {
        processor.subscribe(TorEvent.BW.observer("static") {})

        val nonStaticObserver = TorEvent.BW.observer("non-static") {}
        processor.subscribe(nonStaticObserver)

        // should do nothing
        processor.unsubscribeAll("static")
        assertEquals(2, processor.size)

        // Should only remove the non-static observer
        processor.unsubscribeAll(TorEvent.BW)
        assertEquals(1, processor.size)

        // Should only remove the non-static observer
        processor.subscribe(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.unsubscribeAll(TorEvent.BW, TorEvent.ADDRMAP)
        assertEquals(1, processor.size)

        // Should not remove the static observer
        processor.subscribe(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.clearObservers()
        assertEquals(1, processor.size)
    }

    @Test
    fun givenStaticObservers_whenOnDestroy_thenEvictsAll() {
        val observer = TorEvent.BW.observer("static") {}
        processor.subscribe(observer)
        assertEquals(1, processor.size)

        processor.clearObservers()
        assertEquals(1, processor.size)

        processor.destroy()
        assertEquals(0, processor.size)

        processor.subscribe(observer)
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

    @Test
    fun givenHandler_whenPassedAsCoroutineContext_thenObserverNameContextIsPassed() = runTest {
        val exceptions = mutableListOf<UncaughtException>()
        val processor = TestProcessor(handler = { exceptions.add(it) })

        val expectedTag = "Expected Tag"
        var invocationEvent = 0
        val latch = Job()
        processor.subscribe(TorEvent.BW.observer(
            tag = expectedTag,
            executor = { handler, _ ->
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch(handler) { throw IllegalStateException() }
                    .invokeOnCompletion { latch.cancel() }
            },
            onEvent = { invocationEvent++ }
        ))
        processor.notify(TorEvent.BW, "")
        latch.join()
        assertEquals(1, exceptions.size)
        assertEquals(0, invocationEvent)
        assertTrue(exceptions.first().context.contains(expectedTag))
    }
}
