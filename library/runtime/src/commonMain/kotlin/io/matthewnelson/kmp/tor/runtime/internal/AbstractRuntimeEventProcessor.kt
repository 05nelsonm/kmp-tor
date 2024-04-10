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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.ctrl.AbstractTorEventProcessor
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch

@OptIn(InternalKmpTorApi::class)
internal abstract class AbstractRuntimeEventProcessor(
    staticTag: String?,
    initialObservers: Set<RuntimeEvent.Observer<*>>,
    initialTorEventObservers: Set<TorEvent.Observer>,
):  AbstractTorEventProcessor(staticTag, initialTorEventObservers),
    RuntimeEvent.Processor
{

    private val observers = LinkedHashSet<RuntimeEvent.Observer<*>>(initialObservers.size + 1, 1.0F)
    private val lock = SynchronizedObject()
    protected final override val handler: UncaughtException.Handler = UncaughtException.Handler { t ->
        RuntimeEvent.LOG.ERROR.notifyObservers(t)
    }

    init {
        observers.addAll(initialObservers)
    }

    public final override fun add(observer: RuntimeEvent.Observer<*>) {
        withObservers { add(observer) }
    }

    public final override fun add(vararg observers: RuntimeEvent.Observer<*>) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { add(it) } }
    }

    public final override fun remove(observer: RuntimeEvent.Observer<*>) {
        withObservers { remove(observer) }
    }

    public final override fun remove(vararg observers: RuntimeEvent.Observer<*>) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { remove(it) } }
    }

    public final override fun removeAll(event: RuntimeEvent<*>) {
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag.isStaticTag()) continue

                if (observer.event == event) {
                    iterator.remove()
                }
            }
        }
    }

    public final override fun removeAll(vararg events: RuntimeEvent<*>) {
        if (events.isEmpty()) return
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag.isStaticTag()) continue

                if (events.contains(observer.event)) {
                    iterator.remove()
                }
            }
        }
    }

    public final override fun removeAll(tag: String) {
        if (tag.isStaticTag()) return
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag != tag) continue
                iterator.remove()
            }
        }

        super.removeAll(tag)
    }

    public final override fun clearObservers() {
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag.isStaticTag()) continue
                iterator.remove()
            }
        }

        super.clearObservers()
    }

    protected fun <R: Any> RuntimeEvent<R>.notifyObservers(output: R) {
        val event = this

        if (event is RuntimeEvent.LOG.DEBUG && !debug) return

        val handler = if (event is RuntimeEvent.LOG.ERROR) {
            UncaughtException.Handler.IGNORE
        } else {
            handler
        }

        withObservers {
            if (isEmpty()) return@withObservers null
            mapNotNull { if (it.event == event) it else null }
        }?.forEach { observer ->
            handler.tryCatch(observer.toString(isStatic = observer.tag.isStaticTag())) {
                @Suppress("UNCHECKED_CAST")
                (observer.onEvent as OnEvent<R>)(output)
            }
        }
    }

    protected override fun onDestroy(): Boolean {
        val wasDestroyed = super.onDestroy()
        if (wasDestroyed) synchronized(lock) { observers.clear() }
        return wasDestroyed
    }

    private fun <T: Any?> withObservers(
        block: MutableSet<RuntimeEvent.Observer<*>>.() -> T,
    ): T {
        if (destroyed) return block(noOpMutableSet())

        return synchronized(lock) {
            block(if (destroyed) noOpMutableSet() else observers)
        }
    }

    protected final override fun registered(): Int = super.registered() + synchronized(lock) { observers.size }
}
