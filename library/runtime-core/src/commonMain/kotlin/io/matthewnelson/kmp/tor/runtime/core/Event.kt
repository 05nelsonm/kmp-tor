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
package io.matthewnelson.kmp.tor.runtime.core

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmField

/**
 * Base abstraction for creating enum like event/observer type
 * hierarchies using kotlin sealed classes & data objects.
 *
 * e.g.
 *
 *     public sealed class MyEvent private constructor(
 *         name: String,
 *     ): Event<String, MyEvent, MyEvent.Observer>(name) {
 *
 *         public data object THIS: MyEvent("THIS")
 *         public data object THAT: MyEvent("THAT")
 *
 *         public open class Observer(
 *             event: MyEvent,
 *             tag: String?,
 *             executor: OnEvent.Executor?,
 *             onEvent: OnEvent<String>,
 *         ): Event.Observer<String, MyEvent>(
 *             event,
 *             tag,
 *             executor,
 *             onEvent
 *         )
 *
 *         public companion object: Entries<MyEvent>(numEvents = 2) {
 *
 *             @JvmStatic
 *             @Throws(IllegalArgumentException::class)
 *             public override fun valueOf(name: String): MyEvent {
 *                 return super.valueOf(name)
 *             }
 *
 *             @JvmStatic
 *             public override fun valueOfOrNull(name: String): MyEvent? {
 *                 return super.valueOfOrNull(name)
 *             }
 *
 *             @JvmStatic
 *             public override fun entries(): Set<MyEvent> {
 *                 return super.entries()
 *             }
 *
 *             protected override val lazyEntries: ThisBlock<LinkedHashSet<MyEvent>> =
 *                 ThisBlock {
 *                     // NOTE: Update numEvents when adding an event
 *                     add(THIS); add(THAT);
 *                 }
 *         }
 *
 *         protected final override fun factory(
 *             event: MyEvent,
 *             tag: String?,
 *             executor: OnEvent.Executor?,
 *             onEvent: OnEvent<String>,
 *         ): Observer = Observer(event, tag, executor, onEvent)
 *     }
 *
 * @see [io.matthewnelson.kmp.tor.runtime.core.TorEvent]
 * @see [io.matthewnelson.kmp.tor.runtime.RuntimeEvent]
 * */
public abstract class Event<Data: Any?, E: Event<Data, E, O>, O: Event.Observer<Data, E>> protected constructor(
    @JvmField
    public val name: String,
) {

    /**
     * Creates a new observer for subscribing to its events.
     *
     * e.g. (Kotlin)
     *
     *     MyEvent.THIS.observer { data ->
     *         updateNotification(data.formatBandwidth())
     *     }
     *
     * e.g. (Java)
     *
     *     MyEvent.THAT.INSTANCE.observer(data -> {
     *         updateNotification(formatBandwidth(data));
     *     });
     *
     * @param [onEvent] The callback to pass event data to
     * */
    public fun observer(
        onEvent: OnEvent<Data>,
    ): O = observer(null, null, onEvent)

    /**
     * Creates a new observer with the provided [tag] for subscribing
     * to its events.
     *
     * This is useful for lifecycle aware components, all of which
     * can be removed with a single call using the [tag] upon
     * component destruction.
     *
     * e.g. (Kotlin)
     *
     *     MyEvent.THIS.observer("my service") { data ->
     *         updateNotification(data.formatBandwidth())
     *     }
     *
     * e.g. (Java)
     *
     *     MyEvent.THAT.INSTANCE.observer("my service", data -> {
     *         updateNotification(formatBandwidth(data));
     *     });
     *
     * @param [tag] A string to help grouping/identifying observer(s)
     * @param [onEvent] The callback to pass event data to
     * */
    public fun observer(
        tag: String,
        onEvent: OnEvent<Data>,
    ): O = observer(tag, null, onEvent)

    /**
     * Creates a new observer with the provided [tag] and [executor]
     * for subscribing to its events.
     *
     * This is useful for lifecycle aware components, all of which
     * can be removed with a single call using the [tag] upon
     * component destruction.
     *
     * e.g. (Kotlin)
     *
     *     MyEvent.THIS.observer(null, OnEvent.Executor.Main) { data ->
     *         updateNotification(data.formatBandwidth())
     *     }
     *
     * e.g. (Java)
     *
     *     MyEvent.THAT.INSTANCE.observer("my tag", null, data -> {
     *         updateNotification(formatBandwidth(data));
     *     });
     *
     * @param [tag] A string to help grouping/identifying observer(s)
     * @param [executor] The thread context in which [onEvent] will be
     *   invoked in. If null, the default passed to [Observer.notify]
     *   from the Event processor implementation will be utilized.
     * @param [onEvent] The callback to pass event data to.
     * */
    public fun observer(
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<Data>,
    ): O {
        @Suppress("UNCHECKED_CAST")
        return factory(this as E, tag, executor, onEvent)
    }

    public final override fun equals(other: Any?): Boolean {
        if (other !is Event<*, *, *>) return false
        if (other::class != this::class) return false
        return other.name == name
    }

    public final override fun hashCode(): Int {
        var result = 17
        result = result * 31 + this::class.hashCode()
        result = result * 31 + name.hashCode()
        return result
    }

    public final override fun toString(): String = name

    /**
     * Base abstraction for creating event observer types.
     *
     * @param [event] The event being observed.
     * @param [tag] A string to help grouping/identifying observer(s).
     * @param [executor] The thread context in which onEvent will be
     *   invoked in. If null, the default passed to [notify] from
     *   the Event processor implementation will be utilized.
     * @param [onEvent] The callback to pass event data to.
     * */
    public abstract class Observer<Data: Any?, E: Event<Data, E, *>> protected constructor(
        @JvmField
        public val event: E,
        tag: String?,
        private val executor: OnEvent.Executor?,
        private val onEvent: OnEvent<Data>,
    ) {

        /**
         * A string value for grouping or identifying the observer.
         *
         * Will either be non-blank, or null.
         * */
        @JvmField
        public val tag: String? = tag?.ifBlank { null }

        /**
         * Optional override for inheritors to do things before
         * invoking actual [OnEvent] callback (if at all) within
         * the confines of the [OnEvent.Executor] context.
         *
         * @see [OnEvent.noOp]
         * */
        protected open fun notify(data: Data) { onEvent(data) }

        /**
         * Invokes [OnEvent] with the provided [data]
         *
         * @param [default] the default [OnEvent.Executor] to fall
         *   back to if no executor was not defined for this observer.
         * */
        public fun notify(default: OnEvent.Executor, data: Data) {
            notify(EmptyCoroutineContext, default, data)
        }

        /**
         * Invokes [OnEvent] with the provided [data]
         *
         * @param [handler] Optional ability to pass [UncaughtException.Handler]
         *   wrapped as a [CoroutineExceptionHandler] to use with coroutine
         *   dispatchers. (See [UncaughtException.Handler.uncaughtExceptionHandlerOrNull])
         * @param [default] the default [OnEvent.Executor] to fall back to
         *   if no executor was not defined for this observer.
         * */
        public fun notify(handler: CoroutineContext, default: OnEvent.Executor, data: Data) {
            val executor = executor ?: default

            val executable = when (executor) {
                is OnEvent.Executor.Main -> Executable { notify(data) }

                // Mitigate object creation and just execute directly
                // instead of needlessly calling executor.execute.
                is OnEvent.Executor.Immediate -> null

                // Externally created OnEvent.Executor not within our control.
                // Ensure this only can be executed once.
                else -> Executable.Once.of(concurrent = true) { notify(data) }
            }

            if (executable == null) {
                notify(data)
            } else {
                @OptIn(InternalKmpTorApi::class)
                executor.execute(handler, executable)
            }
        }

        public final override fun toString(): String = toString(isStatic = false)

        /**
         * Helper for processor implementations as to not expose a
         * static tag externally.
         * */
        public fun toString(isStatic: Boolean): String = buildString {
            val tag = if (tag != null && isStatic) "STATIC" else tag

            append("Observer[tag=")
            append(tag.toString())
            append(", event=")
            append(event.name)

            when (executor) {
                null -> "null"
                OnEvent.Executor.Main,
                OnEvent.Executor.Immediate -> executor.toString()
                else -> "Custom"
            }.let {
                append(", executor=")
                append(it)
            }

            append("]@")
            append(hashCode())
        }
    }

    /**
     * Protected factory function for creating instances
     * of the [Event.Observer] implementation.
     * */
    protected abstract fun factory(
        event: E,
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<Data>,
    ): O

    /**
     * Abstraction for the [Event] implementation's companion object.
     *
     * Functions are `protected` to selectively expose what is necessary
     * by overriding and changing visibility to `public` along with
     * the @JvmStatic annotation.
     *
     * @param [numEvents] The number of event types to use as an initial
     *   capacity value for the LinkedHashSet supplied to [lazyEntries].
     * */
    public abstract class Entries<E: Event<*, *, *>> protected constructor(numEvents: Int) {

        private val _entries by lazy {
            LinkedHashSet<E>((numEvents + 1).coerceAtLeast(1), 1.0f)
                .apply(lazyEntries)
                .toImmutableSet()
        }

        @Throws(IllegalArgumentException::class)
        protected open fun valueOf(name: String): E = valueOfOrNull(name)
            ?: throw IllegalArgumentException("Unknown event for name[$name]")

        protected open fun valueOfOrNull(name: String): E? {
            return _entries.firstOrNull { it.name == name }
        }

        protected open fun entries(): Set<E> = _entries

        /**
         * For inheritors of [Entries] to implement.
         *
         * Is invoked lazily **once** and converted into an
         * immutable implementation of [Set] to utilize for the
         * remainder of the runtime.
         * */
        protected abstract val lazyEntries: ThisBlock<LinkedHashSet<E>>
    }
}
