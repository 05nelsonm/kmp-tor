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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@InternalKmpTorApi
public expect class ReentrantLock() {
    public fun lock()
    public fun tryLock(): Boolean
    public fun unlock()
}

@PublishedApi
@InternalKmpTorApi
internal expect inline fun <T: Any?> ReentrantLock.withLockImpl(
    block: () -> T
): T

@InternalKmpTorApi
@OptIn(ExperimentalContracts::class)
public inline fun <T: Any?> ReentrantLock.withLock(
    block: () -> T
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return withLockImpl(block)
}

// Needed because Jvm using withLock is not possible
// with coroutines because block is a critical section
@InternalKmpTorApi
@OptIn(ExperimentalContracts::class)
public suspend fun <T: Any?> ReentrantLock.withLockAsync(
    block: suspend () -> T
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    var threw: Throwable? = null

    val result: T? = try {
        lock()
        block()
    } catch (t: Throwable) {
        unlock()
        threw = t
        null
    }

    threw?.let { throw it }
    unlock()

    @Suppress("UNCHECKED_CAST")
    return result as T
}
