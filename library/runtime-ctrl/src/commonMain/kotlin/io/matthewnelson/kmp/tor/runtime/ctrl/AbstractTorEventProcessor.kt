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
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.api.TorEvent

/**
 * Base abstraction for implementations that process [TorEvent].
 * */
public abstract class AbstractTorEventProcessor
@InternalKmpTorApi
protected constructor(
    initialObservers: Set<TorEvent.Observer>
): TorEvent.Processor {

    private val observers = initialObservers.toMutableSet()

    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()

    public final override fun add(observer: TorEvent.Observer) {
        withObservers { add(observer) }
    }

    public final override fun add(vararg observers: TorEvent.Observer) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { add(it) } }
    }

    public final override fun remove(observer: TorEvent.Observer) {
        withObservers { remove(observer) }
    }

    public final override fun remove(vararg observers: TorEvent.Observer) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { remove(it) } }
    }

    public final override fun removeAll(event: TorEvent) {
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.event == event) {
                    iterator.remove()
                }
            }
        }
    }

    public final override fun removeAll(vararg events: TorEvent) {
        if (events.isEmpty()) return
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (events.contains(observer.event)) {
                    iterator.remove()
                }
            }
        }
    }

    public override fun removeAll(tag: String) {
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag == tag) {
                    iterator.remove()
                }
            }
        }
    }

    public override fun clearObservers() {
        withObservers { clear() }
    }

    protected fun notifyObservers(event: TorEvent, output: String) {
        withObservers {
            for (observer in this) {
                if (observer.event != event) continue
                observer.block.invoke(output)
            }
        }
    }

    protected fun <T: Any?> withObservers(
        block: MutableSet<TorEvent.Observer>.() -> T,
    ): T {
        @OptIn(InternalKmpTorApi::class)
        val result = synchronized(lock) {
            block(observers)
        }

        return result
    }
}
