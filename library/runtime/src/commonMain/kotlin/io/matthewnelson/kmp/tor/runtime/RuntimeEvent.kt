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
     * to register via [Processor.add]
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
     * @param [onEvent] the callback to pass the event output to
     * */
    public fun observer(
        onEvent: OnEvent<R>,
    ): Observer<R> = observer("", onEvent)

    /**
     * Create an observer for the given [RuntimeEvent]
     * to register via [Processor.add]
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
     * @param [tag] Any non-blank string value
     * @param [onEvent] the callback to pass the event output to
     * */
    public fun observer(
        tag: String,
        onEvent: OnEvent<R>,
    ): Observer<R> = Observer(tag, this, onEvent)

    public class Observer<R: Any>(
        tag: String?,
        @JvmField
        public val event: RuntimeEvent<R>,
        @JvmField
        public val onEvent: OnEvent<R>,
    ) {
        @JvmField
        public val tag: String? = tag?.ifBlank { null }

        public override fun toString(): String = toString(isStatic = false)

        public fun toString(isStatic: Boolean): String = buildString {
            val tag = if (tag != null && isStatic) "STATIC" else tag

            append("RuntimeEvent.Observer[tag=")
            append(tag.toString())
            append(",event=")
            append(event.name)
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
        public fun add(observer: Observer<*>)

        /**
         * Add multiple [Observer].
         * */
        public fun add(vararg observers: Observer<*>)

        /**
         * Remove a single [Observer].
         * */
        public fun remove(observer: Observer<*>)

        /**
         * Remove multiple [Observer].
         * */
        public fun remove(vararg observers: Observer<*>)

        /**
         * Remove all [Observer] of a single [RuntimeEvent]
         * */
        public fun removeAll(event: RuntimeEvent<*>)

        /**
         * Remove all [Observer] of multiple [RuntimeEvent]
         * */
        public fun removeAll(vararg events: RuntimeEvent<*>)

        /**
         * Remove all [Observer] with the given [tag].
         *
         * If the implementing class extends both [Processor]
         * and [TorEvent.Processor], [TorEvent.Observer] with
         * the given [tag] will also be removed.
         * */
        public fun removeAll(tag: String)

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
