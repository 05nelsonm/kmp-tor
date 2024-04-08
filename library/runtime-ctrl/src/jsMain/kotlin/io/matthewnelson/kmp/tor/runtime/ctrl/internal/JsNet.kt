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
@file:Suppress("FunctionName", "ClassName")
@file:JsModule("net")
@file:JsNonModule

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.internal.events_EventEmitter
import org.khronos.webgl.Uint8Array

/** [docs](https://nodejs.org/api/net.html#netcreateconnectionpath-connectlistener) */
@JsName("createConnection")
internal external fun net_createConnection(
    options: dynamic,
    connectionListener: () -> Unit,
): net_Socket

/** [docs](https://nodejs.org/api/net.html#class-netsocket) */
@JsName("Socket")
@OptIn(InternalProcessApi::class)
internal external class net_Socket: events_EventEmitter {

    // @Throws(Throwable::class)
    internal fun write(
        chunk: Uint8Array,
        callback: () -> Unit,
    ): Boolean

    internal fun destroy()
    internal val destroyed: Boolean
}
