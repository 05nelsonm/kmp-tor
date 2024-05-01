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

import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.internal.RealTorRuntime
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public class Lifecycle: Destroyable {

    /**
     * An event pertaining to an object's lifecycle.
     *
     * @see [RuntimeEvent.LIFECYCLE]
     * */
    public class Event private constructor(

        /**
         * The object class name
         * */
        @JvmField
        public val clazz: String,

        /**
         * The object [FileID.fidEllipses].
         *
         * If the object was not an instance of [FileID], the
         * value will be null.
         * */
        @JvmField
        public val fid: String?,

        /**
         * The object hash code
         * */
        @JvmField
        public val hash: Int,

        /**
         * The name of the event
         * */
        @JvmField
        public val name: Name,
    ) {

        private constructor(
            obj: Any,
            name: Name
        ): this(
            obj::class.simpleName ?: "Unknown",
            (obj as? FileID)?.fidEllipses,
            obj.hashCode(),
            name,
        )

        @Suppress("FunctionName")
        public companion object {

            @JvmStatic
            public fun OnCreate(obj: Any): Event = Event(obj, Name.OnCreate)
            @JvmStatic
            public fun OnDestroy(obj: Any): Event = Event(obj, Name.OnDestroy)

            // TorRuntime.ServiceFactory LCEs
            @JvmStatic
            public fun OnBind(obj: Any): Event = Event(obj, Name.OnBind)
            @JvmStatic
            public fun OnUnbind(obj: Any): Event = Event(obj, Name.OnUnbind)
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

                // TorRuntime.ServiceFactory LCEs
                @JvmField
                public val OnBind: Name = Name("onBind")
                @JvmField
                public val OnUnbind: Name = Name("onUnbind")
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
                    && other.fid == fid
                    && other.hash == hash
                    && other.name == name
        }

        override fun hashCode(): Int {
            var result = 17
            result = result * 31 + clazz.hashCode()
            result = result * 31 + fid.hashCode()
            result = result * 31 + hash
            result = result * 31 + name.hashCode()
            return result
        }

        override fun toString(): String = buildString {
            append("Lifecycle.Event[class=")
            append(clazz)
            if (fid != null) {
                append("[fid=")
                append(fid)
                append(']')
            }
            append('@')
            append(hash)
            append(", name=")
            append(name)
            append(']')
        }
    }

    /**
     * A wrapper around [TorRuntime] which adds ability to destroy the
     * instance. Is only available if using [TorRuntime.ServiceFactory].
     *
     * **NOTE:** This instance utilizes [OnEvent.Executor.Immediate] and
     * not whatever was declared for [TorRuntime.Builder.defaultEventExecutor].
     * */
    public class DestroyableTorRuntime private constructor(
        private val lifecycle: Lifecycle,
        private val runtime: RealTorRuntime,
    ): TorRuntime by runtime, Destroyable by lifecycle {

        public fun invokeOnCompletion(
            handle: ItBlock<Any?>,
        ): Disposable = lifecycle.job.invokeOnCompletion(handle)

        public override fun equals(other: Any?): Boolean = other is DestroyableTorRuntime && other.runtime == runtime
        public override fun hashCode(): Int = runtime.hashCode()
        public override fun toString(): String = toFIDString()

        internal companion object {

            @JvmSynthetic
            internal fun of(
                runtime: RealTorRuntime,
            ): DestroyableTorRuntime = DestroyableTorRuntime(
                Lifecycle(runtime.handler()),
                runtime,
            )
        }
    }

    /* INTERNAL USAGE FOR TorRuntime.ServiceFactory */

    private val job: LifecycleJob

    @Suppress("ConvertSecondaryConstructorToPrimary")
    private constructor(handler: UncaughtException.Handler) {
        job = LifecycleJob(handler)
    }

    public override fun isDestroyed(): Boolean = job.isCompleting || !job.isActive
    public override fun destroy() { job.complete() }

    public override fun toString(): String = job.toString()

    private class LifecycleJob(
        handler: UncaughtException.Handler,
    ): QueuedJob("TorRuntime", ON_FAILURE, handler) {

        // non-cancellable
        init { onExecuting() }

        fun complete() { onCompletion(Unit, null) }

        private companion object {
            private val ON_FAILURE: OnFailure = OnFailure {}
        }
    }
}
