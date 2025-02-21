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
package io.matthewnelson.kmp.tor.runtime.service.internal

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(InternalKmpTorApi::class)
internal class SynchronizedInstance<T: Any> private constructor(private val instance: T) {

    private val lock = synchronizedObject()

    @OptIn(ExperimentalContracts::class)
    internal inline fun <R: Any?> withLock(block: T.() -> R): R {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return synchronized(lock) { block(instance) }
    }

    internal companion object {

        internal fun <E: Any?, T: MutableCollection<E>> of(
            collection: T,
        ): SynchronizedInstance<T> = SynchronizedInstance(collection)

        internal fun <K: Any?, V: Any?, T: MutableMap<K, V>> of(
            map: T,
        ): SynchronizedInstance<T> = SynchronizedInstance(map)
    }
}
