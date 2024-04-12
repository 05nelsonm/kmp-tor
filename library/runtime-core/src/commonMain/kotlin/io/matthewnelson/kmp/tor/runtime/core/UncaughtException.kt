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
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * A special exception to indicate something went terribly wrong somewhere.
 *
 * @see [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.ERROR]
 * */
public class UncaughtException private constructor(
    override val message: String,
    override val cause: Throwable,
): Exception(message, cause) {

    /**
     * A typealias for message with contextual information about
     * where and what threw the exception.
     *
     * @see [Handler.tryCatch]
     * */
    @JvmField
    public val context: String = message

    /**
     * Producer for [UncaughtException]
     *
     * @see [tryCatch]
     * @see [withSuppression]
     * */
    public fun interface Handler: ItBlock<UncaughtException> {

        public companion object {

            /**
             * Instance that prints [UncaughtException] stack trace to stderr.
             * */
            @JvmField
            public val PRINT: Handler = Handler { it.printStackTrace() }

            /**
             * Instance that swallows (ignores) the [UncaughtException].
             * */
            @JvmField
            public val IGNORE: Handler = Handler {}

            /**
             * Instance that automatically throws [UncaughtException]
             * */
            @JvmField
            public val THROW: Handler = Handler { throw it }

            /**
             * Helper for wrapping external function calls in order to redirect
             * any uncaught exceptions.
             *
             * For example, when notifying attached [TorEvent.Observer] and one
             * does not properly handle its exceptions, instead of it causing the
             * entire program to crash, an [UncaughtException] is redirected to
             * [Handler].
             *
             * **NOTE:** If [Handler] is null, [block] is still invoked and the
             * [UncaughtException] is thrown.
             *
             * **NOTE:** [IllegalStateException] is thrown if handler is a leaked
             * reference of [withSuppression] (i.e. [withSuppression] was used and
             * the [SuppressedHandler] is being utilized **after** lambda closure).
             *
             * @param [context] Contextual information about where/what [block] is
             *   to include in the [UncaughtException]
             * @param [block] the thing to do that may or may not throw exception.
             * @see [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.ERROR]
             * @see [withSuppression]
             * @throws [IllegalStateException] if [SuppressedHandler.isActive] is false
             * @throws [UncaughtException] if handler is null or [Handler.invoke] is
             *   set up to throw the exception
             * */
            @JvmStatic
            public fun Handler?.tryCatch(context: Any, block: ItBlock<Unit>) {
                (this as? SuppressedHandler)?.checkIsActive()

                try {
                    block(Unit)
                } catch (t: Throwable) {
                    val e = if (t is UncaughtException) {
                        t
                    } else {
                        UncaughtException("context: $context", t)
                    }

                    (this ?: THROW)(e)
                }
            }

            /**
             * Wraps an existing handler with suppression such
             * that any invocation of [tryCatch] within [block]
             * lambda is combined into a single [UncaughtException],
             * which is then propagated to the current handler (if
             * there even was an [UncaughtException]).
             *
             * [SuppressedHandler] used in [block] will throw an
             * [IllegalStateException] if it is referenced outside
             * of [withSuppression] lambda, so do not hold onto the
             * reference, treat as a disposable resource.
             *
             * Nested calls of [withSuppression] will use the root
             * [SuppressedHandler], so all [tryCatch] invocations
             * are propagated to a root exception and added as a
             * suppressed exception.
             *
             * e.g.
             *
             *     myHandler.withSuppression {
             *         val suppressed = this
             *
             *         withSuppression {
             *             val nested = this
             *             assertEquals(suppressed, nested)
             *         }
             *     }
             *
             * Great for loops and stuff.
             *
             * e.g.
             *
             *     myHandler.withSuppression {
             *         // demonstration purposes
             *         val suppressedHandler = this
             *
             *         jobs.forEach { job ->
             *
             *             // Any UncaughtException generated by tryCatch
             *             // will be added as a suppressed exception to
             *             // the first UncaughtException.
             *             tryCatch(context = job) { job.cancel(null) }
             *         }
             *
             *         // on lambda closure the single UncaughtException
             *         // (if there is one) will be passed back to
             *         // myHandler
             *     }
             *
             * **NOTE:** If [Handler] is null, [block] is still invoked and the
             * [UncaughtException] (if there is one) is thrown on lambda closure.
             * */
            @JvmStatic
            public fun <T: Any?> Handler?.withSuppression(
                block: SuppressedHandler.() -> T
            ): T {
                val delegate = this
                var threw: UncaughtException? = null
                var isActive = true

                val suppressed = if (delegate is SuppressedHandler) {
                    delegate
                } else {
                    SuppressedHandler.of(isActive = { isActive }) { t ->
                        val result: Unit? = threw?.addSuppressed(t)
                        if (result == null) threw = t
                    }
                }

                val result = block(suppressed)
                isActive = false
                threw?.let { (delegate ?: THROW)(it) }
                return result
            }

            /**
             * Helper for API builders utilizing [Handler] that checks
             * if the instance is not a leaked reference of [SuppressedHandler]
             * */
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            public fun Handler.requireInstanceIsNotSuppressed() {
                require(this !is SuppressedHandler) {
                    "handler is an instance of SuppressedHandler (leaked reference)"
                }
            }
        }
    }

    /**
     * A special [Handler] utilized within [Handler.withSuppression]
     * lambda which propagates all exceptions caught by [Handler.tryCatch]
     * into a single, root exception (the first thrown), with all
     * subsequent exceptions added via [Throwable.addSuppressed].
     *
     * **NOTE:** Utilization outside [Handler.withSuppression] lambda
     * will result in [IllegalStateException] being thrown by
     * [Handler.tryCatch] and [invoke].
     * */
    public class SuppressedHandler private constructor(
        private val _isActive: () -> Boolean,
        private val handler: Handler
    ): Handler {

        /**
         * If the [Handler.withSuppression] block has completed.
         * */
        @get:JvmName("isActive")
        public val isActive: Boolean get() = _isActive()
        @OptIn(InternalKmpTorApi::class)
        private val lock = SynchronizedObject()

        override fun invoke(it: UncaughtException) {
            checkIsActive(it)

            // Prevent potential ConcurrentModificationException
            // if being utilized in multithreaded manner.
            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) { handler(it) }
        }

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun checkIsActive(cause: UncaughtException? = null) {
            if (isActive) return

            val msg = "$this cannot be referenced outside of withSuppression block"
            throw IllegalStateException(msg, cause)
        }

        internal companion object {

            @JvmSynthetic
            internal fun of(
                isActive: () -> Boolean,
                handler: Handler
            ): SuppressedHandler {
                if (handler is SuppressedHandler) return handler
                return SuppressedHandler(isActive, handler)
            }
        }
    }
}
