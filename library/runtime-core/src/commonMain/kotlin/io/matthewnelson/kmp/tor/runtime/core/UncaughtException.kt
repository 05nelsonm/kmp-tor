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

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * A special exception to indicate something went terribly wrong somewhere.
 *
 * @see [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.LOG.ERROR]
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
     * */
    public fun interface Handler: ItBlock<UncaughtException> {

        public companion object {

            /**
             * Instance that prints [UncaughtException] stack trace to stderr.
             * */
            @JvmField
            public val PRINT: Handler = Handler { it.printStackTrace() }

            /**
             * Instance that suppresses (does nothing) the [UncaughtException].
             * */
            @JvmField
            public val SUPPRESS: Handler = Handler {}

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
             * unwrapped exception is thrown.
             *
             * @param [context] Contextual information about where/what [block] is
             *   to include in the [UncaughtException]
             * @param [block] the thing to do that may or may not throw exception.
             * @see [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.LOG.ERROR]
             * */
            @JvmStatic
            public fun Handler?.tryCatch(context: Any, block: ItBlock<Unit>) {
                try {
                    block(Unit)
                } catch (t: Throwable) {
                    if (this == null) throw t
                    invoke(UncaughtException("context: $context", t))
                }
            }
        }
    }
}
