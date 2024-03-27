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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package io.matthewnelson.kmp.tor.runtime.core

import kotlin.jvm.JvmField

/**
 * A callback for dispatching things.
 * */
public expect fun interface Callback<in It: Any> {

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
