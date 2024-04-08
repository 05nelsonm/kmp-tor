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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.tor.runtime.core.Disposable

@OptIn(InternalProcessApi::class)
internal inline fun net_Socket.onError(
    noinline listener: (error: dynamic) -> Unit
): Disposable {
    on("error", listener)
    return Disposable {
        removeListener("error", listener)
    }
}

@OptIn(InternalProcessApi::class)
internal inline fun net_Socket.onceError(
    noinline listener: (error: dynamic) -> Unit
): Disposable {
    once("error", listener)
    return Disposable {
        removeListener("error", listener)
    }
}

@OptIn(InternalProcessApi::class)
internal inline fun net_Socket.onceClose(
    noinline listener: (hadError: Boolean) -> Unit
): net_Socket {
    once("close", listener)
    return this
}
