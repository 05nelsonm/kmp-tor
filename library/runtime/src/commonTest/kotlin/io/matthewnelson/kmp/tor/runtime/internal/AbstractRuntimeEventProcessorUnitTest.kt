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

import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AbstractRuntimeEventProcessorUnitTest {

    private companion object {
        private const val STATIC_TAG = "TAG_STATIC_1234"
    }

    private class TestProcessor(
        override val isService: Boolean = false,
    ): AbstractRuntimeEventProcessor(STATIC_TAG, emptySet(), OnEvent.Executor.Immediate, emptySet()) {
        var _debug: Boolean = true
        override val debug: Boolean get() = _debug
        val size: Int get() = registered()
        fun <R: Any> notify(event: RuntimeEvent<R>, output: R) { event.notifyObservers(output) }
        fun notify(event: TorEvent, output: String) { event.notifyObservers(output) }
        fun destroy() { onDestroy() }
    }

    private val processor = TestProcessor()

    @Test
    fun givenObserver_whenAddRemove_thenIsAsExpected() {
        val observer = RuntimeEvent.LOG.DEBUG.observer {}
        processor.subscribe(observer)
        assertEquals(1, processor.size)
        processor.subscribe(observer)
        assertEquals(1, processor.size)
        processor.unsubscribe(observer)
        assertEquals(0, processor.size)
    }

    @Test
    fun givenObservers_whenNotified_thenIsOutsideOfLock() {
        var observer: RuntimeEvent.Observer<String>? = null
        observer = RuntimeEvent.LOG.DEBUG.observer {
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
        val o1 = RuntimeEvent.LOG.DEBUG.observer { invocations++ }
        val o2 = RuntimeEvent.LOG.INFO.observer {}
        val o3 = RuntimeEvent.LOG.INFO.observer {}
        processor.subscribe(o1, o2, o3, o3)
        assertEquals(3, processor.size)

        processor.unsubscribeAll(RuntimeEvent.LOG.INFO)
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
        processor.subscribe(o1, o2, o3)
        assertEquals(3, processor.size)

        processor.unsubscribe(o2, o3)
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
        processor.subscribe(o1, o1, o2, o2, o3, o3)
        assertEquals(3, processor.size)

        processor.unsubscribeAll("test2")
        assertEquals(1, processor.size)

        // Is the proper tagged observer removed
        processor.notify(RuntimeEvent.LOG.DEBUG, "out")
        assertEquals(1, invocations)
    }

    @Test
    fun givenBlankTag_whenObserver_thenTagIsNull() {
        assertNull(RuntimeEvent.Observer(RuntimeEvent.LOG.DEBUG, "  ", null) { }.tag)
    }

    @Test
    fun givenStaticTag_whenRemove_thenDoesNothing() {
        processor.subscribe(RuntimeEvent.LOG.DEBUG.observer(STATIC_TAG) {})

        val nonStaticObserver = RuntimeEvent.LOG.DEBUG.observer("non-static") {}
        processor.subscribe(nonStaticObserver)

        // should do nothing
        processor.unsubscribeAll(STATIC_TAG)
        assertEquals(2, processor.size)

        // Should only remove the non-static observer
        processor.unsubscribeAll(RuntimeEvent.LOG.DEBUG)
        assertEquals(1, processor.size)

        // Should only remove the non-static observer
        processor.subscribe(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.unsubscribeAll(RuntimeEvent.LOG.DEBUG, RuntimeEvent.LOG.WARN)
        assertEquals(1, processor.size)

        // Should not remove the static observer
        processor.subscribe(nonStaticObserver)
        assertEquals(2, processor.size)
        processor.clearObservers()
        assertEquals(1, processor.size)
    }

    @Test
    fun givenStaticObservers_whenOnDestroy_thenEvictsAll() {
        val observer = RuntimeEvent.LOG.DEBUG.observer(STATIC_TAG) {}
        processor.subscribe(observer)
        processor.subscribe(TorEvent.BW.observer(STATIC_TAG) {})

        processor.clearObservers()
        assertEquals(2, processor.size)

        // Should also clear out static TorEvent observers
        processor.destroy()
        assertEquals(0, processor.size)

        processor.subscribe(observer)
        assertEquals(0, processor.size)
    }

    @Test
    fun givenUncaughtException_whenRedirectedToLogError_thenIsAsExpected() {
        processor.subscribe(RuntimeEvent.LOG.DEBUG.observer(STATIC_TAG) { throw IllegalStateException() })
        processor.subscribe(TorEvent.INFO.observer(STATIC_TAG) { throw IllegalStateException() })

        var invocations = 0
        processor.subscribe(RuntimeEvent.ERROR.observer { t ->
            invocations++
            assertIs<UncaughtException>(t)
            assertIs<IllegalStateException>(t.cause)

            // ensure observer.toString (which is utilized
            // as context) does not leak the static tag value.
            assertFalse(t.message.contains(STATIC_TAG))
            assertTrue(t.message.contains("tag=STATIC"))
        })

        processor.notify(RuntimeEvent.LOG.DEBUG, "something")
        assertEquals(1, invocations)

        processor.notify(TorEvent.INFO, "something")
        assertEquals(2, invocations)
    }

    @Test
    fun givenDebug_whenToggled_thenDispatchesAsExpected() {
        var invocations = 0
        val observer = RuntimeEvent.LOG.DEBUG.observer { invocations++ }
        processor.subscribe(observer)

        processor.notify(observer.event, "")
        assertEquals(1, invocations)

        processor._debug = false
        processor.notify(observer.event, "")
        assertEquals(1, invocations)

        processor._debug = true
        processor.notify(observer.event, "")
        assertEquals(2, invocations)
    }

    @Test
    fun givenHandler_whenPassedAsCoroutineContext_thenObserverNameContextIsPassed() = runTest {
        val exceptions = mutableListOf<Throwable>()

        val expectedTag = "Expected Tag"
        var invocationEvent = 0
        val latch = Job()
        processor.subscribe(RuntimeEvent.ERROR.observer { exceptions.add(it) })
        processor.subscribe(RuntimeEvent.LOG.DEBUG.observer(
            tag = expectedTag,
            executor = { handler, _ ->
                @OptIn(DelicateCoroutinesApi::class)
                GlobalScope.launch(handler) { throw IllegalStateException() }
                    .invokeOnCompletion { latch.cancel() }
            },
            onEvent = { invocationEvent++ }
        ))
        processor.notify(RuntimeEvent.LOG.DEBUG, "")
        latch.join()
        assertEquals(1, exceptions.size)
        assertEquals(0, invocationEvent)
        assertIs<UncaughtException>(exceptions.first())
        assertTrue(exceptions.first().message!!.contains(expectedTag))
    }

    @Test
    fun givenNoErrorObserver_whenUncaughtException_thenThrows() {
        processor.subscribe(RuntimeEvent.LOG.DEBUG.observer { throw IllegalStateException() })

        assertFailsWith<UncaughtException> { processor.notify(RuntimeEvent.LOG.DEBUG, "") }
    }

    @Test
    fun givenMultipleErrorObservers_whenThrows_thenUncaughtExceptionsStacked() {
        var invocationO1 = 0
        var invocationO2 = 0
        var invocationO3 = 0
        val o1 = RuntimeEvent.ERROR.observer { invocationO1++ }
        val o2 = RuntimeEvent.ERROR.observer { invocationO2++; throw it }
        val o3 = RuntimeEvent.ERROR.observer { invocationO3++ }
        processor.subscribe(o1, o2, o3)

        assertFailsWith<UncaughtException> {
            processor.notify(RuntimeEvent.ERROR, IllegalStateException())
        }

        assertEquals(1, invocationO1)
        assertEquals(1, invocationO2)
        assertEquals(1, invocationO3)
    }

    @Test
    fun givenIsService_whenDestroyed_thenStaticObserversNotRemoved() {
        var invocationDebug = 0
        val processor = TestProcessor(isService = true)
        processor.subscribe(RuntimeEvent.LOG.DEBUG.observer {})
        processor.subscribe(RuntimeEvent.LOG.DEBUG.observer(STATIC_TAG) { invocationDebug++ })
        processor.subscribe(TorEvent.INFO.observer {})
        assertEquals(3, processor.size)
        processor.destroy()

        // All non-static observers removed
        assertEquals(1, processor.size)

        // Attempts to add new observers ignored
        processor.subscribe(RuntimeEvent.LOG.DEBUG.observer {})
        assertEquals(1, processor.size)

        // notify still works
        assertEquals(0, invocationDebug)
        processor.notify(RuntimeEvent.LOG.DEBUG, "")
        assertEquals(1, invocationDebug)
    }
}
