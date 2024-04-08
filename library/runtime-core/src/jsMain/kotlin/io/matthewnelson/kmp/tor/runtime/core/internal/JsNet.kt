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
@file:Suppress("ClassName", "FunctionName")
@file:JsModule("net")
@file:JsNonModule

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.process.InternalProcessApi
import io.matthewnelson.kmp.process.internal.events_EventEmitter

/** [docs](https://nodejs.org/api/net.html#netcreateserveroptions-connectionlistener) */
@JsName("createServer")
internal external fun net_createServer(connectionListener: (socket: dynamic) -> Unit): net_Server

/** [docs](https://nodejs.org/api/net.html#class-netserver) */
@JsName("Server")
@OptIn(InternalProcessApi::class)
internal external class net_Server: events_EventEmitter {
    fun close()
    fun listen(port: Int, host: String, backlog: Int, callback: () -> Unit)
}
