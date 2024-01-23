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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized

@OptIn(InternalKmpTorApi::class)
internal abstract class InstanceKeeper<K, V> internal constructor(
    initialCapacity: Int = 1,
    loadFactor: Float = 1.0F,
): SynchronizedObject() {

    private val instances = LinkedHashMap<K, V>(initialCapacity, loadFactor)

    protected fun getOrCreateInstance(
        key: K,
        block: () -> V,
    ): V = synchronized(this@InstanceKeeper) {
        instances[key] ?: block()
            .also { instances[key] = it }
    }

    internal open class Open<K, V> internal constructor(
        initialCapacity: Int = 1,
        loadFactor: Float = 1.0F,
    ): InstanceKeeper<K, V>(initialCapacity, loadFactor) {

        internal fun getOrCreate(key: K, block: () -> V): V = getOrCreateInstance(key, block)
    }
}
