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
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(InternalKmpTorApi::class)
internal abstract class InstanceKeeper<K: Any, V: Any> internal constructor(initialCapacity: Int = 1) {

    private val lock = synchronizedObject()
    private val instances = ArrayList<Pair<K, V>>(initialCapacity)

    @OptIn(ExperimentalContracts::class)
    protected inline fun getOrCreateInstance(
        key: K,
        block: (others: List<Pair<K, V>>) -> V,
    ): V {
        contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }

        return synchronized(lock) {
            for (i in instances) {
                if (i.first != key) continue
                return@synchronized i.second
            }


            val others = instances.toImmutableList()
            val instance = block(others)
            if (others.find { it.second == instance } == null) {
                instances.add(key to instance)
            }
            instance
        }
    }
}
