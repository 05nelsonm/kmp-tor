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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.tor.runtime.core.*
import kotlin.jvm.JvmStatic

/**
 * Events specific to [TorRuntime]
 *
 * e.g.
 *
 *     val errorObserver = RuntimeEvent.LOG.ERROR.observer { t ->
 *         t.printStackTrace()
 *     }
 *
 *
 * @see [Observer]
 * @see [Event.observer]
 * @see [Processor]
 * @see [RuntimeEvent.Companion]
 * */
public sealed class RuntimeEvent<Data: Any> private constructor(
    name: String
): Event<Data, RuntimeEvent<Data>, RuntimeEvent.Observer<Data>>(name) {

    /**
     * Errors encountered by [TorRuntime].
     *
     * All exceptions encountered when notifying other non-[ERROR]
     * observers (including [TorEvent] observers) are piped to [ERROR]
     * observers as [UncaughtException].
     *
     * **NOTE:** Any exceptions thrown by [ERROR] observers are re-thrown
     * as [UncaughtException] (if not already one). This will likely crash
     * the program.
     *
     * **NOTE:** If the error is an [UncaughtException] and no observers
     * for [ERROR] are registered with [TorRuntime], the [UncaughtException]
     * will be thrown (and likely crash the program). It is critical that an
     * [ERROR] observer be registered either via [TorRuntime.Builder.observerStatic],
     * or immediately after [TorRuntime] is instantiated via [TorRuntime.subscribe].
     * */
    public data object ERROR: RuntimeEvent<Throwable>("ERROR")

    /**
     * The current [Action] that is being executed by [TorRuntime].
     *
     * Useful for reacting to specific jobs via attachment of an
     * [ActionJob.invokeOnCompletion] handler.
     *
     * e.g.
     *
     *     EXECUTE.observer { job ->
     *         if (!job.isStop) return@observer
     *         job.invokeOnCompletion {
     *             if (job.isSuccess) {
     *                 // do something
     *             }
     *         }
     *     }
     * */
    public data object EXECUTE: RuntimeEvent<ActionJob>("EXECUTE")

    /**
     * Events pertaining to an object's lifecycle.
     * */
    public data object LIFECYCLE: RuntimeEvent<Lifecycle.Event>("LIFECYCLE")

    public sealed class LOG private constructor(name: String): RuntimeEvent<String>(name) {

        /**
         * Debug level logging. Events will only be dispatched
         * when [TorRuntime.Environment.debug] is set to `true`.
         *
         * **NOTE:** Debug logs may reveal sensitive information
         * and should not be enabled in production!
         * */
        public data object DEBUG: LOG("LOG_DEBUG")

        /**
         * Info level logging.
         * */
        public data object INFO: LOG("LOG_INFO")

        /**
         * Warn level logging. These are non-fatal errors.
         * */
        public data object WARN: LOG("LOG_WARN")
    }

    // TODO: NEWNYM
    //  Because TorCmd.Signal.NewNym returns Reply.Success.OK
    //  but can be rate limited, TorRuntime should intercept
    //  any enqueued NewNym jobs and, upon successful completion
    //  setup an TorEvent.NOTICE observer to catch the rate limit
    //  dispatch. If after 50ms (or something) nothing comes, dispatch
    //  success.

    /**
     * Model to be registered with a [Processor] for being notified
     * via [OnEvent] invocation with [RuntimeEvent] data.
     *
     * @see [Event.Observer]
     * @see [Processor]
     * */
    public open class Observer<Data: Any>(
        event: RuntimeEvent<Data>,
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<Data>,
    ): Event.Observer<Data, RuntimeEvent<Data>>(
        event,
        tag,
        executor,
        onEvent,
    )

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
         * Remove all [Observer] of a single [RuntimeEvent].
         * */
        public fun unsubscribeAll(event: RuntimeEvent<*>)

        /**
         * Remove all [Observer] of multiple [RuntimeEvent].
         * */
        public fun unsubscribeAll(vararg events: RuntimeEvent<*>)

        /**
         * Remove all [Observer] with the given [tag].
         *
         * If the implementing class extends both [Processor]
         * and [TorEvent.Processor], all [TorEvent.Observer] with
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

    public companion object: Entries<RuntimeEvent<*>>(numEvents = 6) {

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public override fun valueOf(name: String): RuntimeEvent<*> {
            return super.valueOf(name)
        }

        @JvmStatic
        public override fun valueOfOrNull(name: String): RuntimeEvent<*>? {
            return super.valueOfOrNull(name)
        }

        @JvmStatic
        public override fun entries(): Set<RuntimeEvent<*>> {
            return super.entries()
        }

        protected override val lazyEntries: ThisBlock<LinkedHashSet<RuntimeEvent<*>>> = ThisBlock {
            add(ERROR); add(EXECUTE); add(LIFECYCLE); add(LOG.DEBUG);
            add(LOG.INFO); add(LOG.WARN);
        }
    }

    /**
     * Helper for selectively exposing the ability to notify observers
     * externally of the [TorRuntime] implementation.
     * */
    public interface Notifier {

        public fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data)

        public companion object {

            @JvmStatic
            @Suppress("NOTHING_TO_INLINE")
            public inline fun <E: LOG> Notifier.log(event: E, from: Any, log: String) {
                notify(event, "$from $log")
            }

            @JvmStatic
            @Suppress("NOTHING_TO_INLINE")
            public inline fun Notifier.d(from: Any, log: String) { log(LOG.DEBUG, from, log) }

            @JvmStatic
            @Suppress("NOTHING_TO_INLINE")
            public inline fun Notifier.i(from: Any, log: String) { log(LOG.INFO, from, log) }

            @JvmStatic
            @Suppress("NOTHING_TO_INLINE")
            public inline fun Notifier.w(from: Any, log: String) { log(LOG.WARN, from, log) }

            @JvmStatic
            @Suppress("NOTHING_TO_INLINE")
            public inline fun Notifier.e(cause: Throwable) { notify(ERROR, cause) }

            @JvmStatic
            @Suppress("NOTHING_TO_INLINE")
            public inline fun Notifier.lce(event: Lifecycle.Event) { notify(LIFECYCLE, event) }
        }
    }

    protected final override fun factory(
        event: RuntimeEvent<Data>,
        tag: String?,
        executor: OnEvent.Executor?,
        onEvent: OnEvent<Data>,
    ): Observer<Data> = Observer(event, tag, executor, onEvent)
}
