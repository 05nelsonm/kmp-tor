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
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.controller.internal.DebugItem
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.locks.SynchronizedObject

@InternalTorApi
sealed interface ListenersHandler: TorEventProcessor<TorEvent.SealedListener> {
    val isEmpty: Boolean
    fun <T> withLock(action: MutableSet<TorEvent.SealedListener>.() -> T): T
    fun notify(event: TorEvent.Type.SingleLineEvent, output: String)
    fun notify(event: TorEvent.Type.MultiLineEvent, output: List<String>)

    companion object {
        @InternalTorApi
        @Throws(IllegalArgumentException::class)
        fun newInstance(
            initialCapacity: Int = 1,
            debugger: (DebugItem.ListenerError) -> Unit
        ): ListenersHandler =
            RealListenersHandler(initialCapacity, debugger)
    }
}

@OptIn(InternalTorApi::class)
private class RealListenersHandler(
    initialCapacity: Int,
    val debugger: (DebugItem.ListenerError) -> Unit,
): SynchronizedObject(), ListenersHandler {

    init {
        require(initialCapacity >= 0) {
            "ListenersHandler.initialCapacity must be greater than or equal to 0"
        }
    }

    private val set: MutableSet<TorEvent.SealedListener> = LinkedHashSet(initialCapacity)

    override fun <T> withLock(action: MutableSet<TorEvent.SealedListener>.() -> T): T {
        return synchronized(this) { action.invoke(set) }
    }

    override val isEmpty: Boolean
        get() = synchronized(this) { set.isEmpty() }

    override fun addListener(listener: TorEvent.SealedListener): Boolean {
        return synchronized(this) {
            set.add(listener)
        }
    }

    override fun removeListener(listener: TorEvent.SealedListener): Boolean {
        return synchronized(this) {
            set.remove(listener)
        }
    }

    override fun notify(event: TorEvent.Type.SingleLineEvent, output: String) {
        synchronized(this) {
            for (listener in set) {
                try {
                    listener.onEvent(event, output)
                } catch (e: Exception) {
                    debugger.invoke(DebugItem.ListenerError(listener, DebugItem.Error(e)))
                }
            }
        }
    }

    override fun notify(event: TorEvent.Type.MultiLineEvent, output: List<String>) {
        synchronized(this) {
            for (listener in set) {
                try {
                    listener.onEvent(event, output)
                } catch (e: Exception) {
                    debugger.invoke(DebugItem.ListenerError(listener, DebugItem.Error(e)))
                }
            }
        }
    }
}