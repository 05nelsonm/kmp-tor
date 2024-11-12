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

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.ctrl.AbstractTorEventProcessor
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression

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

    protected fun <Data: Any> RuntimeEvent<Data>.notifyObservers(data: Data) {
        val event = this

        if (event is RuntimeEvent.LOG.DEBUG && !debug) return

        val observers = withObservers(isNotify = true) {
            if (isEmpty()) return@withObservers null
            mapNotNull { if (it.event == event) it else null }
        }

        if (observers.isNullOrEmpty()) {
            // Throw UncaughtException if no ERROR observers present.
            if (event is RuntimeEvent.ERROR && data is UncaughtException) {
                throw data
            }

            return
        }

        if (event is RuntimeEvent.ERROR) {
            UncaughtException.Handler.THROW.withSuppression { notify(observers, data) }
        } else {
            handler.notify(observers, data)
        }
    }

    private fun <Data: Any> UncaughtException.Handler.notify(observers: List<RuntimeEvent.Observer<*>>, data: Data) {
        observers.forEach { observer ->
            val ctx = ObserverContext(observer.toString(isStatic = observer.tag.isStaticTag()))
            val handlerContext = if (this is HandlerWithContext) this + ctx else ctx

            tryCatch(ctx) {
                @Suppress("UNCHECKED_CAST")
                (observer as RuntimeEvent.Observer<Data>).notify(handlerContext, defaultExecutor, data)
            }
        }
    }

    protected override fun onDestroy(): Boolean {
        if (!super.onDestroy()) return false
        synchronized(lock) {
            val iterator = observers.iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (isService && observer.tag.isStaticTag()) continue
                iterator.remove()
            }
        }
        return true
    }

    private fun <T: Any?> withObservers(
        isNotify: Boolean = false,
        block: MutableSet<RuntimeEvent.Observer<*>>.() -> T,
    ): T {
        if (destroyed && !isNotify) return block(noOpMutableSet())

        return synchronized(lock) {
            block(if (destroyed && !isNotify) noOpMutableSet() else observers)
        }
    }

    protected final override fun registered(): Int = super.registered() + synchronized(lock) { observers.size }
}
