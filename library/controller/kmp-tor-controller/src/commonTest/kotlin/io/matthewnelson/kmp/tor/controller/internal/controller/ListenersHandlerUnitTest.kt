/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.controller.internal.controller

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(InternalTorApi::class)
class ListenersHandlerUnitTest {

    private val listeners = ListenersHandler.newInstance(initialCapacity = 2) {}

    private class TestListener: TorEvent.SealedListener {
        var lastSingleLineEvent: TorEvent.Type.SingleLineEvent? = null
        override fun onEvent(event: TorEvent.Type.SingleLineEvent, output: String) {
            lastSingleLineEvent = event
        }
        var lastMultiLineEvent: TorEvent.Type.MultiLineEvent? = null
        override fun onEvent(event: TorEvent.Type.MultiLineEvent, output: List<String>) {
            lastMultiLineEvent = event
        }
    }

    @Test
    fun givenListener_whenNotAlreadyAdded_returnsTrueWhenAdded() {
        val actual = listeners.addListener(TestListener())
        assertTrue(actual)
    }

    @Test
    fun givenListener_whenAlreadyAdded_returnsFalseWhenAdded() {
        val listener = TestListener()
        listeners.addListener(listener)
        val actual = listeners.addListener(listener)
        assertFalse(actual)
    }

    @Test
    fun givenListener_whenAlreadyAdded_returnsTrueWhenRemoved() {
        val listener = TestListener()
        listeners.addListener(listener)
        val actual = listeners.removeListener(listener)
        assertTrue(actual)
    }

    @Test
    fun givenListener_whenNotAlreadyAdded_returnsFalseWhenRemoved() {
        val actual = listeners.removeListener(TestListener())
        assertFalse(actual)
    }

    @Test
    fun givenListenerHandler_whenIsEmptyCalled_returnsExpected() {
        assertTrue(listeners.isEmpty)
        val listener = TestListener()
        listeners.addListener(listener)
        assertFalse(listeners.isEmpty)
        listeners.removeListener(listener)
        assertTrue(listeners.isEmpty)
    }

    @Test
    fun givenMultipleListeners_whenNotifySingleLine_allListenersAreNotified() {
        val listener1 = TestListener()
        val listener2 = TestListener()
        val listener3 = TestListener()

        listeners.addListener(listener1)
        listeners.addListener(listener2)
        listeners.addListener(listener3)

        val event: TorEvent.Type.SingleLineEvent = TorEvent.BandwidthUsed
        listeners.notify(event, "0 0")
        assertEquals(event, listener1.lastSingleLineEvent)
        assertEquals(event, listener2.lastSingleLineEvent)
        assertEquals(event, listener3.lastSingleLineEvent)
    }

    @Test
    fun givenMultipleListeners_whenNotifyMultiLine_allListenersAreNotified() {
        val listener1 = TestListener()
        val listener2 = TestListener()
        val listener3 = TestListener()

        listeners.addListener(listener1)
        listeners.addListener(listener2)
        listeners.addListener(listener3)

        val event: TorEvent.Type.MultiLineEvent = TorEvent.NetworkStatus
        listeners.notify(event, listOf("0 0"))
        assertEquals(event, listener1.lastMultiLineEvent)
        assertEquals(event, listener2.lastMultiLineEvent)
        assertEquals(event, listener3.lastMultiLineEvent)
    }

}
