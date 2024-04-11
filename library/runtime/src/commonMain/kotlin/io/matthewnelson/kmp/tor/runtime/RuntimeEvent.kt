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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Events specific to [TorRuntime]
 *
 * @see [Observer]
 * @see [observer]
 * @see [Processor]
 * */
public sealed class RuntimeEvent<R: Any> private constructor(
    @JvmField
    public val name: String,
) {

    public data object LOG {

        /**
         * Debug level logging. Events will only be dispatched
         * when [TorRuntime.Environment.debug] is set to `true`.
         * */
        public data object DEBUG: RuntimeEvent<String>("LOG_DEBUG")

        /**
         * Error level logging. Note that all [UncaughtException]
         * are redirected to [ERROR] observers. All exceptions
         * thrown by [ERROR] observers are suppressed.
         * */
        public data object ERROR: RuntimeEvent<Throwable>("LOG_ERROR")

        /**
         * Info level logging.
         * */
        public data object INFO: RuntimeEvent<String>("LOG_INFO")

        /**
         * Warn level logging. These are non-fatal errors.
         * */
        public data object WARN: RuntimeEvent<String>("LOG_WARN")
    }

    // TODO: Other events

    /**
     * Create an observer for the given [RuntimeEvent]
     * to register via [Processor.subscribe].
     *
     * e.g. (Kotlin)
     *
     *     RuntimeEvent.DEBUG.observer { output ->
     *         println(output)
     *     }
     *
     * e.g. (Java)
     *
     *     RuntimeEvent.DEBUG.INSTANCE.observer(output -> {
     *         System.out.println(output);
     *     });
     *
     * @param [onEvent] The callback to pass the event output to
     * */
    public fun observer(
        onEvent: OnEvent<R>,
    ): Observer<R> = observer("", onEvent)

    /**
     * Create an observer for the given [RuntimeEvent]
     * to register via [Processor.subscribe].
     *
     * This is useful for lifecycle aware components, all of which
     * can be removed with a single call using the [tag] upon
     * component destruction.
     *
     * e.g. (Kotlin)
     *
     *     RuntimeEvent.DEBUG.observer("my service") { output ->
     *         println(output)
     *     }
     *
     * e.g. (Java)
     *
     *     RuntimeEvent.DEBUG.INSTANCE.observer("my service", output -> {
     *         System.out.println(output);
     *     });
     *
     * @param [tag] Any non-blank string value
     * @param [onEvent] The callback to pass the event output to
     * */
    public fun observer(
        tag: String,
        onEvent: OnEvent<R>,
    ): Observer<R> = Observer(this, tag, null, onEvent)

    /**
     * Create an observer for the given [RuntimeEvent], [tag] and
     * [OnEvent.Executor] to register via [Processor.subscribe].
     *
     * e.g. (Kotlin)
     *
     *     RuntimeEvent.DEBUG.observer(null, OnEvent.Executor.Main) { output ->
     *         println(output)
     *     }
     *
     * e.g. (Java)
     *
     *     RuntimeEvent.DEBUG.INSTANCE.observer(
     *         null,
     *         OnEvent.Executor.Main.INSTANCE,
     *         output -> {
     *             System.out.println(output);
     *         }
     *     );
     *
     * @param [tag] Any non-blank string value
     * @param [executor] A custom executor for this observer which
     *   will be used instead of the default one passed to
     *   [Observer.notify].
     * @param [onEvent] The callback to pass the event output to
     * */
    public fun observer(
        tag: String?,
        executor: OnEvent.Executor,
        onEvent: OnEvent<R>,
    ): Observer<R> = Observer(this, tag, executor, onEvent)

    /**
     * Model to be registered with a [Processor] for being notified
     * via callback invocation with [RuntimeEvent] output information.
     * */
    public open class Observer<R: Any>(
        /**
         * The [RuntimeEvent] this is observing
         * */
        @JvmField
        public val event: RuntimeEvent<R>,
        tag: String?,
        @JvmField
        protected val executor: OnEvent.Executor?,
        @JvmField
        protected val onEvent: OnEvent<R>,
    ) {

        /**
         * An identifier string
         * */
        @JvmField
        public val tag: String? = tag?.ifBlank { null }

        /**
         * Invokes [OnEvent] for the given [event] string
         *
         * @param [default] the default [OnEvent.Executor] to fall
         *   back to if [executor] was not defined for this observer.
         * */
        public fun notify(default: OnEvent.Executor, event: R) {
            (executor ?: default).execute { onEvent(event) }
        }

        public final override fun toString(): String = toString(isStatic = false)

        public fun toString(isStatic: Boolean): String = buildString {
            val tag = if (tag != null && isStatic) "STATIC" else tag

            append("RuntimeEvent.Observer[tag=")
            append(tag.toString())
            append(",event=")
            append(event.name)

            when (executor) {
                null -> "null"
                OnEvent.Executor.Main,
                OnEvent.Executor.Unconfined -> executor.toString()
                else -> "Custom"
            }.let {
                append(",executor=")
                append(it)
            }

            append("]@")
            append(hashCode())
        }
    }

    /**
     * Base interface for implementations that process [RuntimeEvent].
     * */
    public interface Processor {

        /**
         * Add a single [Observer].
         * */
        public fun subscribe(observer: Observer<*>)

        /**
         * Add multiple [Observer].
         * */
        public fun subscribe(vararg observers: Observer<*>)

        /**
         * Remove a single [Observer].
         * */
        public fun unsubscribe(observer: Observer<*>)

        /**
         * Remove multiple [Observer].
         * */
        public fun unsubscribe(vararg observers: Observer<*>)

        /**
         * Remove all [Observer] of a single [RuntimeEvent]
         * */
        public fun unsubscribeAll(event: RuntimeEvent<*>)

        /**
         * Remove all [Observer] of multiple [RuntimeEvent]
         * */
        public fun unsubscribeAll(vararg events: RuntimeEvent<*>)

        /**
         * Remove all [Observer] with the given [tag].
         *
         * If the implementing class extends both [Processor]
         * and [TorEvent.Processor], [TorEvent.Observer] with
         * the given [tag] will also be removed.
         * */
        public fun unsubscribeAll(tag: String)

        /**
         * Remove all non-static [Observer] that are currently
         * registered.
         *
         * If the implementing class extends both [Processor]
         * and [TorEvent.Processor], all [TorEvent.Observer]
         * will also be removed.
         * */
        public fun clearObservers()
    }

    public companion object {

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun valueOf(name: String): RuntimeEvent<*> {
            return valueOfOrNull(name)
                ?: throw IllegalArgumentException("Unknown RuntimeEvent.name[$name]")
        }

        @JvmStatic
        public fun valueOfOrNull(name: String): RuntimeEvent<*>? {
            return entries.firstOrNull { event ->
                event.name.equals(name, ignoreCase = true)
            }
        }

        @get:JvmStatic
        @get:JvmName("entries")
        public val entries: Set<RuntimeEvent<*>> by lazy {
            immutableSetOf(
                LOG.DEBUG,
                LOG.ERROR,
                LOG.INFO,
                LOG.WARN,
            )
        }
    }

    @InternalKmpTorApi
    public interface Notifier {
        public fun <R: Any> notify(event: RuntimeEvent<R>, output: R)
    }

    final override fun toString(): String = name
}
