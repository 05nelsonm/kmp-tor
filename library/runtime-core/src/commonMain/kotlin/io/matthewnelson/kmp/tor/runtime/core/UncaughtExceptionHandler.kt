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

public fun interface UncaughtExceptionHandler {

    public operator fun invoke(context: String, cause: Throwable)

    public companion object {

        @JvmField
        public val PRINT: UncaughtExceptionHandler =
            UncaughtExceptionHandler { context, cause -> Exception(context, cause).printStackTrace() }

        @JvmField
        public val SUPPRESS: UncaughtExceptionHandler =
            UncaughtExceptionHandler { _, _ -> }

        @JvmField
        public val THROW: UncaughtExceptionHandler =
            UncaughtExceptionHandler { context, cause -> throw Exception(context, cause) }

        /**
         * Helper for wrapping external function calls in order to redirect
         * any uncaught exceptions due to a bad implementation. For example,
         * when notifying attached [TorEvent.Observer] where an exception
         * should not be thrown.
         * */
        @JvmStatic
        public fun UncaughtExceptionHandler?.tryCatch(context: Any, block: ItBlock<Unit>) {
            try {
                block(Unit)
            } catch (t: Throwable) {
                if (this == null) throw t
                invoke("context: $context", t)
            }
        }
    }
}
