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
internal abstract class AbstractRuntimeEventProcessor internal constructor(
    staticTag: String?,
    observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
    defaultExecutor: OnEvent.Executor,
    observersTorEvent: Set<TorEvent.Observer>,
):  AbstractTorEventProcessor(staticTag, observersTorEvent, defaultExecutor),
    RuntimeEvent.Processor
{

    private val observers = LinkedHashSet<RuntimeEvent.Observer<*>>(observersRuntimeEvent.size + 1, 1.0F)
    private val lock = SynchronizedObject()
    protected override val handler = HandlerWithContext.of { t -> RuntimeEvent.ERROR.notifyObservers(t) }
    // Used for RuntimeEvent.ERROR ONLY
    private val handlerERROR = HandlerWithContext.of(UncaughtException.Handler.THROW)

    init {
        observers.addAll(observersRuntimeEvent)
    }

    public final override fun subscribe(observer: RuntimeEvent.Observer<*>) {
        withObservers { add(observer) }
    }

    public final override fun subscribe(vararg observers: RuntimeEvent.Observer<*>) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { add(it) } }
    }

    public final override fun unsubscribe(observer: RuntimeEvent.Observer<*>) {
        withObservers { remove(observer) }
    }

    public final override fun unsubscribe(vararg observers: RuntimeEvent.Observer<*>) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { remove(it) } }
    }

    public final override fun unsubscribeAll(event: RuntimeEvent<*>) {
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

    public final override fun unsubscribeAll(vararg events: RuntimeEvent<*>) {
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

    public final override fun unsubscribeAll(tag: String) {
        if (tag.isStaticTag()) return
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag != tag) continue
                iterator.remove()
            }
        }

        super.unsubscribeAll(tag)
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

        val observers = withObservers {
            if (isEmpty()) return@withObservers null
            mapNotNull { if (it.event == event) it else null }
        }

        if (observers.isNullOrEmpty()) {
            // Throw UncaughtException if no ERROR observers present.
            if (event is RuntimeEvent.ERROR && output is UncaughtException) {
                throw output
            }

            return
        }

        val handler = if (event is RuntimeEvent.ERROR) {
            handlerERROR
        } else {
            handler
        }

        observers.forEach { observer ->
            val ctx = ObserverContext(observer.toString(isStatic = observer.tag.isStaticTag()))

            handler.tryCatch(ctx) {
                @Suppress("UNCHECKED_CAST")
                (observer as RuntimeEvent.Observer<R>)
                    .notify(handler + ctx, defaultExecutor, output)
            }
        }
    }

    protected override fun onDestroy(): Boolean {
        if (!super.onDestroy()) return false
        synchronized(lock) { observers.clear() }
        return true
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
