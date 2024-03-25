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

import kotlin.jvm.JvmStatic

public interface Destroyable {

    public fun destroy()
    public fun isDestroyed(): Boolean

    public companion object {

        @JvmStatic
        @Throws(IllegalStateException::class)
        public fun Destroyable.checkDestroy() {
            if (!isDestroyed()) return
            val name = this::class.simpleName ?: "UnknownClass"
            throw IllegalStateException("$name.isDestroyed[true]")
        }
    }
}
