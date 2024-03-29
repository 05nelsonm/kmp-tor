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
package io.matthewnelson.kmp.tor.runtime.core

import kotlin.jvm.JvmField

/**
 * An alias of [ItBlock] indicating a callback for
 * something occurring successfully.
 *
 * **NOTE:** Exceptions should not be thrown
 * within the [OnSuccess] lambda. If [OnSuccess] is
 * being utilized with TorRuntime APIs, it will be
 * treated as an [UncaughtException] and dispatched
 * to [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.LOG.ERROR]
 * observers.
 * */
public typealias OnSuccess<T> = ItBlock<T>

/**
 * An alias of [ItBlock] indicating a callback for
 * something occurring exceptionally.
 *
 * **NOTE:** The exception should not be re-thrown
 * within the [OnFailure] lambda. If [OnFailure] is
 * being utilized with TorRuntime APIs, it will be
 * treated as an [UncaughtException] and dispatched
 * to [io.matthewnelson.kmp.tor.runtime.RuntimeEvent.LOG.ERROR]
 * observers.
 * */
public typealias OnFailure = ItBlock<Throwable>

/**
 * A callback for dispatching things.
 * */
public fun interface Callback<in It: Any> {
    public operator fun invoke(it: It)

    // TODO: DispatchMain Issue #349
}

/**
 * A callback to return to callers to "undo", or
 * "dispose" something.
 * */
public fun interface Disposable {
    public operator fun invoke()

    public companion object {

        /**
         * A non-operational implementation of [Disposable]
         * */
        @JvmField
        public val NOOP: Disposable = Disposable {}
    }
}
