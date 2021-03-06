/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager.internal.util.synchronous

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal class SynchronizedMutableMap<T: Any>(initialCapacity: Int = 1): SynchronizedObject() {

    private val map: MutableMap<String, T> = LinkedHashMap(initialCapacity)

    internal fun <V: Any?> withLock(block: MutableMap<String, T>.() -> V): V {
        return synchronized(this) {
            block.invoke(map)
        }
    }
}
