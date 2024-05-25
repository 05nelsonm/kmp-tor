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
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.internal.ExecutorMainInternal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

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
public fun interface OnSuccess<in T: Any?>: ItBlock<T> {

    public companion object {

        /**
         * A non-operational static instance of [OnSuccess].
         * */
        @JvmStatic
        public fun <T: Any?> noOp(): OnSuccess<T> = NOOP
    }

    private data object NOOP: OnSuccess<Any?> {
        override fun invoke(it: Any?) {}
        override fun toString(): String = "OnSuccess.NOOP"
    }
}

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
public fun interface OnFailure: ItBlock<Throwable> {

    public companion object {

        /**
         * A non-operational static instance of [OnFailure].
         * */
        @JvmStatic
        public fun noOp(): OnFailure = NOOP
    }

    private data object NOOP: OnFailure {
        override fun invoke(it: Throwable) {}
        override fun toString(): String = "OnFailure.NOOP"
    }
}

/**
 * A callback for dispatching event data.
 *
 * Implementations of [OnEvent] should not throw exception,
 * be fast, and non-blocking.
 *
 * **NOTE:** If [OnEvent] is being utilized with `TorRuntime` APIs,
 * exceptions will be treated as an [UncaughtException] and dispatched
 * to [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.ERROR].
 *
 * @see [OnEvent.noOp]
 * @see [OnEvent.Executor]
 * */
public fun interface OnEvent<in Data: Any?>: ItBlock<Data> {

    public companion object {

        /**
         * A non-operational static instance of [OnEvent]. Useful
         * for classes that inherit from an [Event.Observer] and override
         * protected notify function.
         * */
        @JvmStatic
        public fun <Data: Any?> noOp(): OnEvent<Data> = NOOP
    }

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
         * Execute [executable] in desired context.
         *
         * **NOTE: This is an internal API and should not be called from general
         * code; it is strictly for `kmp-tor` event observers. Custom implementations
         * of [Executor] should be for usage with `kmp-tor` only, and not invoke
         * [execute]**
         *
         * The [UncaughtException.Handler] can be retrieved from [CoroutineContext] by
         * [UncaughtException.Handler.uncaughtExceptionHandlerOrNull] if needed.
         *
         * @param [handler] The [UncaughtException.Handler] wrapped as
         *   [CoroutineContext] element to pipe exceptions.
         * @param [executable] to be invoked in desired context.
         * */
        @InternalKmpTorApi
        public fun execute(handler: CoroutineContext, executable: Executable)

        /**
         * Utilizes [Dispatchers.Main] under the hood to transition events
         * dispatched from a background thread, to the UI thread. If
         * [MainCoroutineDispatcher.immediate] is available, that is always
         * preferred.
         *
         * **NOTE:** On `Node.js` this invokes [Executable] immediately as the
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
            override fun execute(handler: CoroutineContext, executable: Executable) { executable.execute() }

            override fun toString(): String = "OnEvent.Executor.Immediate"
        }
    }

    private data object NOOP: OnEvent<Any?> {
        override fun invoke(it: Any?) {}
        override fun toString(): String = "OnEvent.NOOP"
    }
}

/**
 * A callback to return to callers to "undo", or
 * "dispose" of something.
 * */
public fun interface Disposable {
    public fun dispose()

    public companion object {

        /**
         * A non-operational static instance of [Disposable]
         * */
        @JvmStatic
        public fun noOp(): Disposable = NOOP
    }

    private data object NOOP: Disposable {
        override fun dispose() {}
        override fun toString(): String = "Disposable.NOOP"
    }
}

/**
 * A callback for executing something
 *
 * @see [noOp]
 * @see [Once]
 * */
public fun interface Executable {
    public fun execute()

    public companion object {

        /**
         * A non-operational static instance of [Executable]
         * */
        @JvmStatic
        public fun noOp(): Executable = NOOP
    }

    /**
     * Helper for creating single-shot [Executable], with
     * built-in support for thread-safe execution (if needed).
     *
     * Successive invocations of [execute] are ignored.
     *
     * @see [of]
     * */
    public class Once private constructor(
        private val delegate: Executable,
        concurrent: Boolean,
    ): Executable {

        @Volatile
        private var _hasExecuted: Boolean = false
        @get:JvmName("hasExecuted")
        public val hasExecuted: Boolean get() = _hasExecuted

        @OptIn(InternalKmpTorApi::class)
        private val lock = if (concurrent) SynchronizedObject() else null

        public override fun execute() {
            if (_hasExecuted) return

            @OptIn(InternalKmpTorApi::class)
            if (lock != null) {
                synchronized(lock) {
                    if (_hasExecuted) return@synchronized null
                    _hasExecuted = true
                    delegate
                }
            } else {
                _hasExecuted = true
                delegate
            }?.execute()
        }

        public companion object {

            /**
             * Returns an instance of [Executable.Once] that is **not** thread-safe.
             *
             * @throws [IllegalArgumentException] if [executable] is an instance
             *   of [Once] or [noOp]
             * */
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            public fun of (executable: Executable): Once = of(false, executable)

            /**
             * Returns an instance of [Executable.Once].
             *
             * @param [concurrent] true for thread-safe, false not thread-safe
             * @throws [IllegalArgumentException] if [executable] is an instance
             *   of [Once] or [noOp]
             * */
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            public fun of(concurrent: Boolean, executable: Executable): Once {
                require(executable !is Once) {
                    "executable cannot already be an instance of Execute.Once"
                }
                require(executable !is NOOP) {
                    "executable cannot already be an instance of Execute.NOOP"
                }

                return Once(executable, concurrent = concurrent)
            }
        }

        public override fun toString(): String = "Execute.Once@${hashCode()}"
    }

    private data object NOOP: Executable {
        override fun execute() {}
        override fun toString(): String = "Executable.NOOP"
    }
}
