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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized

@OptIn(InternalKmpTorApi::class)
internal abstract class InstanceKeeper<K: Any, V: Any> internal constructor(initialCapacity: Int = 1) {

    private val lock = SynchronizedObject()
    private val instances = ArrayList<Pair<K, V>>(initialCapacity)

    protected fun getOrCreateInstance(
        key: K,
        block: (others: List<Pair<K, V>>) -> V,
    ): V = synchronized(lock) {
        var instance: V? = null

        for (i in instances) {
            if (i.first == key) {
                instance = i.second
                break
            }
        }

        if (instance == null) {
            val others = instances.toImmutableList()
            val i = block(others)
            instances.add(key to i)
            instance = i
        }

         instance
    }
}
