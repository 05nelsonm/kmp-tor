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
package io.matthewnelson.kmp.tor.ext.callback.controller.common

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi

fun interface TorCallback<in T: Any?> {
    fun invoke(result: T)
}

@InternalTorApi
@Suppress("nothing_to_inline")
inline fun TorCallback<Throwable>?.shouldFailImmediately(
    failure: Boolean,
    uncaughtExceptionHandlerProvider: () -> TorCallback<Throwable>,
    exceptionProvider: () -> Exception,
): EmptyTask? {
    return if (failure) {
        // Can throw an exception here. Should be piped
        // to the uncaught exception handler.
        try {
            this?.invoke(exceptionProvider.invoke())
        } catch (t: Throwable) {
            uncaughtExceptionHandlerProvider.invoke().invoke(t)
        }

        EmptyTask
    } else {
        null
    }
}
