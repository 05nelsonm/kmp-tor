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

import io.matthewnelson.kmp.tor.runtime.core.*
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public class Lifecycle private constructor(
    private val handler: UncaughtException.Handler
): Destroyable {

    /**
     * An event pertaining to an object's lifecycle.
     *
     * @see [RuntimeEvent.LIFECYCLE]
     * */
    public class Event private constructor(
        @JvmField
        public val clazz: String,
        @JvmField
        public val hash: Int,
        @JvmField
        public val name: Name,
    ) {

        private constructor(obj: Any, name: Name): this(obj::class.simpleName ?: "Unknown", obj.hashCode(), name)

        @Suppress("FunctionName")
        public companion object {

            @JvmStatic
            public fun OnCreate(obj: Any): Event = Event(obj, Name.OnCreate)
            @JvmStatic
            public fun OnDestroy(obj: Any): Event = Event(obj, Name.OnDestroy)

            // Android TorService LCEs
            @JvmStatic
            public fun OnBind(obj: Any): Event = Event(obj, Name.OnBind)
            @JvmStatic
            public fun OnStart(obj: Any): Event = Event(obj, Name.OnStart)
            @JvmStatic
            public fun OnRemoved(obj: Any): Event = Event(obj, Name.OnRemoved)
            @JvmStatic
            public fun OnReturned(obj: Any): Event = Event(obj, Name.OnReturned)

            @JvmStatic
            public fun OnSubscribed(obj: Any): Event = Event(obj, Name.OnSubscribed)
            @JvmStatic
            public fun OnUnsubscribed(obj: Any): Event = Event(obj, Name.OnUnsubscribed)
        }

        public class Name private constructor(private val value: String) {

            public companion object {

                @JvmField
                public val OnCreate: Name = Name("onCreate")
                @JvmField
                public val OnDestroy: Name = Name("onDestroy")

                // Android TorService LCEs
                @JvmField
                public val OnBind: Name = Name("onBind")
                @JvmField
                public val OnStart: Name = Name("onStart")
                @JvmField
                public val OnRemoved: Name = Name("onRemoved")
                @JvmField
                public val OnReturned: Name = Name("onReturned")

                @JvmField
                public val OnSubscribed: Name = Name("onSubscribed")
                @JvmField
                public val OnUnsubscribed: Name = Name("onUnsubscribed")
            }

            override fun equals(other: Any?): Boolean = other is Name && other.value == value
            override fun hashCode(): Int = 17 * 42 + value.hashCode()
            override fun toString(): String = value
        }

        override fun equals(other: Any?): Boolean {
            return  other is Event
                    && other.clazz == clazz
                    && other.hash == hash
                    && other.name == name
        }

        override fun hashCode(): Int {
            var result = 17
            result = result * 31 + clazz.hashCode()
            result = result * 31 + hash
            result = result * 31 + name.hashCode()
            return result
        }

        override fun toString(): String = buildString {
            append("Lifecycle.Event[class=")
            append(clazz)
            append('@')
            append(hash)
            append(", name=")
            append(name)
            append(']')
        }
    }

    private val job = Job()

    override fun isDestroyed(): Boolean = job.isCompleting || !job.isActive

    override fun destroy() { job.complete() }

    public fun invokeOnCompletion(
        handle: ItBlock<Unit>,
    ): Disposable = job.invokeOnCompletion { handle(Unit) }

    private inner class Job: QueuedJob("LIFECYCLE", ON_FAILURE, handler) {

        // non-cancellable
        init { onExecuting() }

        fun complete() { onCompletion(Unit) { null } }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(handler: UncaughtException.Handler): Lifecycle = Lifecycle(handler)

        private val ON_FAILURE: OnFailure = OnFailure {}
    }

    public override fun toString(): String = "Lifecycle[isDestroyed=${isDestroyed()}]@${hashCode()}"
}
