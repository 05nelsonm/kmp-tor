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
@file:Suppress("ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.internal.commonAwaitAsync
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Helper for creating APIs that wrap an [EnqueuedJob] with
 * asynchronous execution functionality.
 *
 * **NOTE:** This is an internal API not meant for public consumption.
 *
 * @see [io.matthewnelson.kmp.tor.runtime.core.util.executeAsync]
 * @see [io.matthewnelson.kmp.tor.runtime.Action.Companion.executeAsync]
 * @suppress
 * */
@InternalKmpTorApi
//@Throws(Throwable::class)
@OptIn(ExperimentalContracts::class)
@Deprecated("Not meant for public use", level = DeprecationLevel.ERROR)
public actual suspend fun <Arg: EnqueuedJob.Argument, Success: Any> Arg.awaitAsync(
    enqueue: (arg: Arg, onFailure: OnFailure, onSuccess: OnSuccess<Success>) -> EnqueuedJob,
): Success {
    contract {
        callsInPlace(enqueue, InvocationKind.AT_MOST_ONCE)
    }

    return commonAwaitAsync(enqueue)
}
