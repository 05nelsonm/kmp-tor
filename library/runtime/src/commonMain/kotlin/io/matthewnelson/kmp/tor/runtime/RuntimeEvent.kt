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
import io.matthewnelson.kmp.tor.runtime.ctrl.api.ItBlock
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorEvent
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

public sealed class RuntimeEvent<R: Any> private constructor() {

    @get:JvmName("name")
    public val name: String get() = toString()

    public data object DEBUG: RuntimeEvent<String>()
    public data object ERROR: RuntimeEvent<String>()
    public data object INFO: RuntimeEvent<String>()
    public data object WARN: RuntimeEvent<String>()

    // TODO: Actions

    public fun observer(
        block: ItBlock<R>,
    ): Observer<R> = observer("", block)

    public fun observer(
        tag: String,
        block: ItBlock<R>,
    ): Observer<R> = Observer(tag, this, block)

    public class Observer<R: Any>(
        tag: String?,
        @JvmField
        public val event: RuntimeEvent<R>,
        @JvmField
        public val output: ItBlock<R>
    ) {
        @JvmField
        public val tag: String? = tag?.ifBlank { null }

        override fun toString(): String = buildString {
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
         * Remove all [Observer] that are currently registered.
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
                ?: throw IllegalArgumentException("Unknown RuntimeEvent of $name")
        }

        @JvmStatic
        public fun valueOfOrNull(name: String): RuntimeEvent<*>? {
            return entries.firstOrNull { event ->
                event.name.equals(name, ignoreCase = true)
            }
        }

        @JvmField
        public val entries: Set<RuntimeEvent<*>> = immutableSetOf(
            DEBUG,
            ERROR,
            INFO,
            WARN,
        )
    }
}
