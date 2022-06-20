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
package io.matthewnelson.kmp.tor.ext.callback.common

import kotlin.jvm.JvmField

fun interface TorCallback<in T: Any?> {

    fun invoke(result: T)

    companion object {
        /**
         * Optional static instance that will always throw on request failure.
         *
         * Use case is primarily to pipe exceptions to the uncaught exception
         * handler that was set when instantiating `CallbackTorController` or
         * `CallbackTorManager`.
         * */
        @JvmField
        val THROW = TorCallback<Throwable> { throw it }
    }
}
