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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.ItBlock
import kotlin.jvm.JvmInline

@JvmInline
internal value class Debugger(private val block: ItBlock<String>) {

    internal companion object {

        internal fun Debugger?.d(prefix: Any?, text: () -> String) {
            if (this == null) return
            try {
                val p = if (prefix != null) "$prefix - " else ""
                val t = text()
                block(p + t)
            } catch (_: Throwable) {}
        }
    }
}
