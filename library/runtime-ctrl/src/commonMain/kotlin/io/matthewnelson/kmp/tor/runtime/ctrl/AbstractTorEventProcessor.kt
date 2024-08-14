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
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.toDestroyedErrorJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Base abstraction for implementations that process [TorEvent].
 *
 * @suppress
 * */
@OptIn(InternalKmpTorApi::class)
public abstract class AbstractTorEventProcessor
@InternalKmpTorApi
protected constructor(
    staticTag: String?,
    observers: Set<TorEvent.Observer>,
    protected val defaultExecutor: OnEvent.Executor
): TorEvent.Processor {

    @Volatile
    private var _destroyed: Boolean = false
    private val lock = SynchronizedObject()
    private val observers = LinkedHashSet<TorEvent.Observer>(observers.size + 1, 1.0F)
    private val staticTag: String? = staticTag?.ifBlank { null }

    @get:JvmName("destroyed")
    protected val destroyed: Boolean get() = _destroyed
    protected open val debug: Boolean = true
    protected open val isService: Boolean = false
    protected abstract val handler: HandlerWithContext

    init {
        this.observers.addAll(observers)
    }

    public final override fun subscribe(observer: TorEvent.Observer) {
        withObservers { add(observer) }
    }

    public final override fun subscribe(vararg observers: TorEvent.Observer) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { add(it) } }
    }

    public final override fun unsubscribe(observer: TorEvent.Observer) {
        withObservers { remove(observer) }
    }

    public final override fun unsubscribe(vararg observers: TorEvent.Observer) {
        if (observers.isEmpty()) return
        withObservers { observers.forEach { remove(it) } }
    }

    public final override fun unsubscribeAll(event: TorEvent) {
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

    public final override fun unsubscribeAll(vararg events: TorEvent) {
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

    public override fun unsubscribeAll(tag: String) {
        if (tag.isStaticTag()) return
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag != tag) continue
                iterator.remove()
            }
        }
    }

    public override fun clearObservers() {
        withObservers {
            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (observer.tag.isStaticTag()) continue
                iterator.remove()
            }
        }
    }

    protected fun TorEvent.notifyObservers(data: String) {
        val event = this

        if (event == TorEvent.DEBUG && !debug) return

        withObservers(isNotify = true) {
            if (isEmpty()) return@withObservers null
            mapNotNull { if (it.event == event) it else null }
        }?.forEach { observer ->
            val ctx = ObserverContext(observer.toString(isStatic = observer.tag.isStaticTag()))

            handler.tryCatch(ctx) {
                observer.notify(handler + ctx, defaultExecutor, data)
            }
        }
    }

    protected open fun onDestroy(): Boolean {
        if (_destroyed) return false

        val wasDestroyed = withObservers {
            if (_destroyed) return@withObservers false

            val iterator = iterator()
            while (iterator.hasNext()) {
                val observer = iterator.next()
                if (isService && observer.tag.isStaticTag()) continue
                iterator.remove()
            }
            _destroyed = true
            true
        }

        return wasDestroyed
    }

    private fun <T : Any?> withObservers(
        isNotify: Boolean = false,
        block: MutableSet<TorEvent.Observer>.() -> T,
    ): T {
        if (_destroyed && !isNotify) return block(noOpMutableSet())

        return synchronized(lock) {
            block(if (_destroyed && !isNotify) noOpMutableSet() else observers)
        }
    }

    protected fun String?.isStaticTag(): Boolean = this != null && staticTag != null && this == staticTag

    protected companion object {

        @JvmStatic
        @InternalKmpTorApi
        @Suppress("UNCHECKED_CAST")
        protected fun <T : Any> noOpMutableSet(): MutableSet<T> = NoOpMutableSet as MutableSet<T>

        @JvmStatic
        @InternalKmpTorApi
        protected fun TorCmd<*>.toImmediateIllegalStateJob(
            onFailure: OnFailure,
            message: String,
            handler: UncaughtException.Handler,
        ): EnqueuedJob = toDestroyedErrorJob(onFailure, handler, message)
    }

    // testing
    protected open fun registered(): Int = synchronized(lock) { observers.size }

    // Handler that also implements CoroutineExceptionHandler
    protected class HandlerWithContext private constructor(
        @JvmField
        public val delegate: UncaughtException.Handler
    ) : AbstractCoroutineContextElement(CoroutineExceptionHandler),
        UncaughtException.Handler by delegate,
        CoroutineExceptionHandler
    {

        override fun handleException(context: CoroutineContext, exception: Throwable) {
            when (exception) {
                is CancellationException -> throw exception
                is UncaughtException -> invoke(exception)
                else -> {
                    val ctx = context[ObserverContext]?.context ?: context.toString()
                    tryCatch(ctx) { throw exception }
                }
            }
        }

        public companion object {

            @JvmStatic
            public fun of(handler: UncaughtException.Handler): HandlerWithContext {
                if (handler is HandlerWithContext) return handler
                return HandlerWithContext(handler)
            }
        }
    }

    // For passing observer name as context
    protected class ObserverContext(
        @JvmField
        public val context: String,
    ): AbstractCoroutineContextElement(ObserverContext) {
        public companion object Key: CoroutineContext.Key<ObserverContext>
        public override fun toString(): String = context
    }
}

private object NoOpMutableSet: MutableSet<Any> {

    override fun equals(other: Any?): Boolean = other is MutableSet<*> && other.isEmpty()
    override fun hashCode(): Int = 0
    override fun toString(): String = "[]"

    override val size: Int get() = 0
    override fun isEmpty(): Boolean = true
    override fun contains(element: Any): Boolean = false
    override fun containsAll(elements: Collection<Any>): Boolean = elements.isEmpty()

    override fun iterator(): MutableIterator<Any> = EmptyMutableIterator

    override fun add(element: Any): Boolean = false
    override fun addAll(elements: Collection<Any>): Boolean = elements.isEmpty()

    override fun clear() {}

    override fun retainAll(elements: Collection<Any>): Boolean = elements.isEmpty()
    override fun removeAll(elements: Collection<Any>): Boolean = elements.isEmpty()
    override fun remove(element: Any): Boolean = false

    private object EmptyMutableIterator: MutableIterator<Any> {
        override fun hasNext(): Boolean = false
        override fun next(): Any = throw NoSuchElementException()
        override fun remove() { throw IllegalStateException() }
    }
}
