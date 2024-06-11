/*
 * Copyright (c) 2023 Matthew Nelson
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

import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

public interface Destroyable {

    public fun destroy()
    public fun isDestroyed(): Boolean

    public companion object {

        /**
         * Checks if a [Destroyable] instance has been destroyed or not.
         *
         * @throws [IllegalStateException] If [isDestroyed] is true.
         * */
        @JvmStatic
        @Throws(IllegalStateException::class)
        public fun Destroyable.checkIsNotDestroyed() {
            if (!isDestroyed()) return
            throw destroyedException()
        }

        /**
         * Creates an [IllegalStateException] with a default message of
         *
         *     {this::class.simpleName}.isDestroyed[true]
         *
         * If `simpleName` returns null (e.g. Anonymous Object), then a
         * default of `UnknownClass` will be used.
         *
         * @param [namePrefix] if not empty, message will be prefixed with
         *   the declared [namePrefix] and a `.` character.
         * */
        @JvmStatic
        @JvmOverloads
        public fun Destroyable.destroyedException(
            namePrefix: String = "",
        ): IllegalStateException {
            var name = this::class.simpleName ?: ""
            name = if (name.isEmpty()) {
                "UnknownClass"
            } else {
                if (namePrefix.isNotEmpty()) {
                    "$namePrefix.$name"
                } else {
                    name
                }
            }

            return IllegalStateException("$name.isDestroyed[true]")
        }
    }
}
