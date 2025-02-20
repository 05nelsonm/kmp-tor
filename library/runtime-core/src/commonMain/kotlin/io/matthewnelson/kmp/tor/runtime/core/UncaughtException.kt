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
@file:Suppress("RemoveRedundantQualifierName")

package io.matthewnelson.kmp.tor.runtime.core

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext
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

    /**
     * Contextual information about where the [UncaughtException] occurred.
     * */
    @JvmField
    public val context: String,

    /**
     * The underlying cause of the [UncaughtException].
     * */
    public override val cause: Throwable,
): RuntimeException(context, cause) {

    public override val message: String = "context: $context"

    /**
     * A typealias for message with contextual information about
     * where and what threw the exception.
     *
     * @see [Handler.tryCatch2]
     * */

    /**
     * Producer for [UncaughtException]
     *
     * @see [tryCatch2]
     * @see [withSuppression2]
     * */
    public fun interface Handler: ItBlock<UncaughtException> {

        public companion object {

            /**
             * Instance that prints [UncaughtException] stack trace to stderr.
             * */
            @JvmField
            public val PRINT: Handler = object : Handler {
                override fun invoke(it: UncaughtException) { it.printStackTrace() }
                /** @suppress */
                override fun toString(): String = "UncaughtException.Handler.PRINT"
            }

            /**
             * Instance that swallows (ignores) the [UncaughtException].
             * */
            @JvmField
            public val IGNORE: Handler = object : Handler {
                override fun invoke(it: UncaughtException) {}
                /** @suppress */
                override fun toString(): String = "UncaughtException.Handler.IGNORE"
            }

            /**
             * Instance that automatically throws [UncaughtException]
             * */
            @JvmField
            public val THROW: Handler = object : Handler {
                override fun invoke(it: UncaughtException) { throw it }
                /** @suppress */
                override fun toString(): String = "UncaughtException.Handler.THROW"
            }

            /**
             * Helper for wrapping external function calls in order to redirect
             * any uncaught exceptions.
             *
             * For example, when notifying attached [TorEvent.Observer] and one
             * does not properly handle its exceptions, instead of it causing the
             * entire program to crash, an [UncaughtException] is redirected to
             * [Handler].
             *
             * **NOTE:** If [Handler] is null, [Handler.THROW] is used.
             *
             * @see [withSuppression2]
             * @see [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.ERROR]
             *
             * @param [context] Contextual information about where/what [block] is
             *   to include in the [UncaughtException]
             * @param [block] the thing to do that may or may not throw an exception.
             *
             * @throws [UncaughtException] if and only if [block] throws exception, and
             *   provided [Handler] chooses to throw it.
             * */
            @JvmStatic
            public inline fun Handler?.tryCatch2(context: Any, block: () -> Unit) {
                try {
                    block()
                } catch (t: Throwable) {
                    if (this == IGNORE) return
                    val e = if (t is UncaughtException) t else {
                        UncaughtException.of(context.toString(), t)
                    }
                    (this ?: THROW).invoke(e)
                }
            }

            /**
             * Wraps an existing handler with suppression such
             * that any invocation of [tryCatch2] within [block]
             * lambda is combined into a single [UncaughtException],
             * which is then propagated to the current handler (if
             * there even was an [UncaughtException]).
             *
             * If [SuppressedHandler] reference is leaked outside
             * the [withSuppression2] lambda, the [UncaughtException]
             * will be passed back to the originating non-suppressed
             * [Handler].
             *
             * Nested calls of [withSuppression2] will use the root
             * [SuppressedHandler], so all [tryCatch2] invocations
             * are propagated to a root exception and added as a
             * suppressed exception.
             *
             * **NOTE:** If [Handler] is null, [Handler.THROW] is used.
             *
             * e.g.
             *
             *     myHandler.withSuppression2 {
             *         val suppressed = this
             *
             *         withSuppression2 {
             *             val nested = this
             *             assertEquals(suppressed, nested)
             *         }
             *     }
             *
             * Great for loops and stuff.
             *
             * e.g.
             *
             *     myHandler.withSuppression2 {
             *         // demonstration purposes
             *         val suppressedHandler = this
             *
             *         jobs.forEach { job ->
             *
             *             // Any UncaughtException generated by tryCatch
             *             // will be added as a suppressed exception to
             *             // the first UncaughtException.
             *             tryCatch2(context = job) { job.cancel(null) }
             *         }
             *
             *         // on lambda closure the single UncaughtException
             *         // (if there is one) will be passed back to
             *         // myHandler
             *     }
             * */
            @JvmStatic
            public inline fun <T: Any?> Handler?.withSuppression2(block: SuppressedHandler.() -> T): T {
                val handler = if (this is SuppressedHandler && !isActive) root() else this ?: THROW

                var threw: UncaughtException? = null
                var isActive = true

                val suppressed = if (handler is SuppressedHandler) {
                    // Was still active. Is a nested withSuppression2 invocation
                    handler
                } else {
                    SuppressedHandler.of(isActive = { isActive }, root = handler) { t ->
                        if (threw?.addSuppressed(t) == null) threw = t
                    }
                }

                val result = try {
                    block(suppressed)
                } finally {
                    isActive = false
                }

                threw?.let { handler(it) }
                return result
            }

            /**
             * Retrieves the [Handler] from [CoroutineContext] if present.
             *
             * @see [OnEvent.Executor.execute]
             * */
            @JvmStatic
            @JvmName("fromCoroutineContextOrNull")
            public fun CoroutineContext.uncaughtExceptionHandlerOrNull(): Handler? {
                val handler = get(CoroutineExceptionHandler) ?: return null
                return handler as? Handler
            }

            /** @suppress */
            @JvmStatic
            @Deprecated("Use tryCatch2", ReplaceWith("tryCatch2(context, block)"))
            public fun Handler?.tryCatch(context: Any, block: ItBlock<Unit>) {
                tryCatch2(context) { block(Unit) }
            }

            /** @suppress */
            @JvmStatic
            @Deprecated("Use withSuppression2", ReplaceWith("this.withSuppression2 { block() }"))
            public fun <T: Any?> Handler?.withSuppression(block: SuppressedHandler.() -> T): T {
                return withSuppression2(block)
            }
        }
    }

    /**
     * A special [Handler] utilized within [Handler.withSuppression2]
     * lambda which propagates all exceptions caught by [Handler.tryCatch2]
     * into a single, root exception (the first one thrown), with all
     * subsequent exceptions added via [Throwable.addSuppressed].
     * */
    public class SuppressedHandler private constructor(
        private val _isActive: () -> Boolean,
        private val _root: Handler,
        private val suppressed: Handler,
    ): Handler {

        /**
         * If the [Handler.withSuppression2] block has completed.
         * */
        @get:JvmName("isActive")
        public val isActive: Boolean get() = _isActive()

        @OptIn(InternalKmpTorApi::class)
        private val lock = synchronizedObject()

        override fun invoke(it: UncaughtException) {
            if (!isActive) return _root(it)

            // Prevent potential ConcurrentModificationException
            // if being utilized in multithreaded manner.
            @OptIn(InternalKmpTorApi::class)
            val useRoot = synchronized(lock) {
                if (!isActive) return@synchronized true
                suppressed(it)
                false
            }

            if (!useRoot) return
            _root(it)
        }

        /** @suppress */
        public override fun toString(): String = "UncaughtException.SuppressedHandler@${hashCode()}"

        @JvmSynthetic
        @PublishedApi
        internal fun root(): Handler = _root

        @PublishedApi
        internal companion object {

            @JvmSynthetic
            @PublishedApi
            internal fun of(
                isActive: () -> Boolean,
                root: Handler,
                suppressed: Handler,
            ): SuppressedHandler {
                require(root !is SuppressedHandler) { "root handler cannot be an instance of SuppressedHandler" }
                require(suppressed !is SuppressedHandler) { "suppressed cannot be an instance of SuppressedHandler" }
                return SuppressedHandler(isActive, root, suppressed)
            }
        }
    }

    @PublishedApi
    internal companion object {

        @JvmSynthetic
        @PublishedApi
        internal fun of(context: String, cause: Throwable): UncaughtException = UncaughtException(context, cause)
    }
}
