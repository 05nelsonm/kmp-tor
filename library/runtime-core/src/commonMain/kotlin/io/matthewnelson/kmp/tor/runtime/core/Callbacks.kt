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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.internal.ExecutorMainInternal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmField

/**
 * An alias of [ItBlock] indicating a callback for
 * something occurring successfully.
 *
 * **NOTE:** Exceptions should not be thrown
 * within the [OnSuccess] lambda. If [OnSuccess] is
 * being utilized with `TorRuntime` APIs, it will be
 * treated as an [UncaughtException] and dispatched
 * to [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.ERROR]
 * observers.
 * */
public typealias OnSuccess<T> = ItBlock<T>

/**
 * An alias of [ItBlock] indicating a callback for
 * something occurring exceptionally.
 *
 * **NOTE:** The exception should not be re-thrown
 * within the [OnFailure] lambda. If [OnFailure] is
 * being utilized with `TorRuntime` APIs, it will be
 * treated as an [UncaughtException] and dispatched
 * to [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.ERROR]
 * observers.
 * */
public typealias OnFailure = ItBlock<Throwable>

/**
 * A callback for dispatching events.
 *
 * Implementations of [OnEvent] should not throw exception,
 * be fast, and non-blocking.
 *
 * **NOTE:** If [OnEvent] is being utilized with `TorRuntime` APIs,
 * exceptions will be treated as an [UncaughtException] and dispatched
 * to [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.ERROR]
 * observers.
 *
 * @see [OnEvent.Executor]
 * */
public fun interface OnEvent<in It: Any>: ItBlock<It> {

    /**
     * `kmp-tor` utilizes several different background threads for
     * which events are generated on, then dispatches them to registered
     * observers' [OnEvent] callbacks. The [Executor] API allows for
     * fine-tuning the context in which that dispatching occurs on
     * several customizable levels.
     *
     * Both [io.matthewnelson.kmp.tor.runtime.RuntimeEvent] and [TorEvent]
     * observer APIs allow declaration of a specific [Executor] to be used
     * for the individual observer. If no [Executor] is specified, then the
     * [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Processor] and
     * [TorEvent.Processor] implementations fallback to using whatever
     * [Executor] was declared when they were created. This means that an
     * [Executor] can be set for default behavior of how events get dispatched
     * based off of the needs of the application, and then be selectively
     * overridden on a per-observer basis, when necessary, for the needs of
     * that observer and how it is used and/or implemented.
     *
     * @see [Executor.Main]
     * @see [Executor.Immediate]
     * */
    public fun interface Executor {

        /**
         * Execute [block] in desired context.
         *
         * **NOTE: This is an internal API and should not be called from general
         * code; it is strictly for `kmp-tor` event observers. Custom implementations
         * of [Executor] should be for usage with `kmp-tor` only**
         *
         * The [UncaughtException.Handler] can be retrieved through use of keys
         * and casting, if needed.
         *
         * e.g.
         *
         *     handler[CoroutineExceptionHandler] as? UncaughtException.Handler
         *
         * @param [handler] The [UncaughtException.Handler] wrapped as
         *   [CoroutineContext] element to pipe exceptions.
         * @param [block] to be invoked in desired context.
         * */
        @InternalKmpTorApi
        public fun execute(handler: CoroutineContext, block: ItBlock<Unit>)

        /**
         * Utilizes [Dispatchers.Main] under the hood to transition events
         * dispatched from a background thread, to the UI thread. If
         * [MainCoroutineDispatcher.immediate] is available, that is always
         * preferred.
         *
         * **NOTE:** On `Node.js` this invokes [ItBlock] immediately as the
         * `kmp-tor` implementation is entirely asynchronous.
         *
         * **WARNING:** Jvm/Android requires the respective coroutines UI
         * dependency `kotlinx-coroutines-{android/javafx/swing}`
         *
         * **WARNING:** Non-Darwin native targets do not have [Dispatchers.Main]
         * resulting in an exception when [execute] is invoked.
         * */
        public object Main: Executor by ExecutorMainInternal {
            override fun toString(): String = "OnEvent.Executor.Main"
        }

        /**
         * Invokes block immediately on whatever thread [execute] has been called
         * from.
         *
         * **NOTE:** [execute] does not form an event loop and may result in a
         * StackOverflowError. It should not be called from general code.
         *
         * Observers utilizing [Immediate] must have a thread-safe implementation
         * of [OnEvent] callbacks whenever referencing things outside the
         * confines of its lambda.
         * */
        public object Immediate: Executor {

            @InternalKmpTorApi
            override fun execute(handler: CoroutineContext, block: ItBlock<Unit>) { block(Unit) }

            override fun toString(): String = "OnEvent.Executor.Immediate"
        }
    }
}

/**
 * A callback to return to callers to "undo", or
 * "dispose" of something.
 * */
public fun interface Disposable {
    public operator fun invoke()

    public companion object {

        /**
         * A non-operational implementation of [Disposable]
         * */
        @JvmField
        public val NOOP: Disposable = Disposable {}
    }
}
