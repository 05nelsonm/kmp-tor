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
 * A special exception to indicate something went terribly wrong.
 *
 * @see [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.LOG.ERROR]
 * */
public class UncaughtException private constructor(
    override val message: String,
    override val cause: Throwable,
): Exception(message, cause) {

    /**
     * Producer for [UncaughtException]
     *
     * @see [tryCatch]
     * */
    public fun interface Handler: ItBlock<UncaughtException> {

        public companion object {

            /**
             * Instance that prints the [UncaughtException]
             * stack trace.
             * */
            @JvmField
            public val PRINT: Handler = Handler { it.printStackTrace() }

            /**
             * Instance that suppresses (does nothing) with the
             * [UncaughtException].
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
             * any uncaught exceptions due to a bad implementation. For example,
             * when notifying attached [TorEvent.Observer] where an exception
             * should not be thrown.
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
