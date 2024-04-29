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
package io.matthewnelson.kmp.tor.runtime.mobile.internal

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized

@OptIn(InternalKmpTorApi::class)
internal class PersistentKeyMap<K: Any, V: Any> {

    private val lock = SynchronizedObject()

    private val map = LinkedHashMap<K, V?>(1, 1.0f)

    internal operator fun get(key: K): V? = withLock { map[key] }
    internal operator fun set(key: K, value: V) { withLock { map[key] = value } }

    internal val entries: Set<Map.Entry<K, V?>> get() = withLock { map.toImmutableMap().entries }
    internal val keys: Set<K> get() = withLock { map.keys.toImmutableSet() }
    internal val values: List<V> get() = withLock {
        map.values.mapNotNullTo(ArrayList(1)) { it }.toImmutableList()
    }

    internal val sizeKeys: Int get() = withLock { map.size }
    internal val sizeValues: Int get() = withLock { map.values.count { it != null } }

    internal fun clear() { withLock { map.clear() } }

    internal fun isEmpty(): Boolean = withLock {
        for (v in map.values) {
            if (v != null) return@withLock false
        }

        return@withLock true
    }

    internal fun remove(key: K): V? = withLock {
        val value = map[key] ?: return@withLock null
        map[key] = null
        value
    }

    private inline fun <T: Any?> withLock(block: () -> T): T = synchronized(lock, block)
}
