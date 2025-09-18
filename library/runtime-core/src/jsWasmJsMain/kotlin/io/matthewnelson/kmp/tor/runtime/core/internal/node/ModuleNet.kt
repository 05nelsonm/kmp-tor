/*
 * Copyright (c) 2025 Matthew Nelson
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
@file:OptIn(DelicateFileApi::class, InternalKmpTorApi::class)

package io.matthewnelson.kmp.tor.runtime.core.internal.node

import io.matthewnelson.kmp.file.Buffer
import io.matthewnelson.kmp.file.DelicateFileApi
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.jsExternTryCatch
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.internal.js.JsObject
import io.matthewnelson.kmp.tor.runtime.core.internal.js.JsUint8Array
import io.matthewnelson.kmp.tor.runtime.core.internal.js.new
import io.matthewnelson.kmp.tor.runtime.core.internal.js.set
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress
import kotlin.js.JsName

/** [docs](https://nodejs.org/api/net.html#netcreateserveroptions-connectionlistener) */
internal sealed external interface ModuleNet {
    fun createConnection(options: JsObject, listener: () -> Unit): JsSocket
    fun createServer(listener: (JsSocket) -> Unit): JsServer
}

/** [docs](https://nodejs.org/api/net.html#class-netserver) */
@JsName("Server")
internal sealed external interface JsServer: JsEventEmitter {
    fun close()
    fun listen(options: JsObject, callback: () -> Unit)
    fun unref(): JsServer
}

@OptIn(InternalKmpTorApi::class)
internal expect fun JsServer.onClose(block: () -> Unit): Disposable.Once

/** [docs](https://nodejs.org/api/net.html#class-netsocket) */
@InternalKmpTorApi
@JsName("Socket")
public sealed external interface JsSocket: JsEventEmitter {
    public fun destroy()
    public val destroyed: Boolean
    public fun write(data: JsUint8Array, callback: () -> Unit): Boolean
    public fun unref(): JsSocket
}

@InternalKmpTorApi
@Throws(Throwable::class)
public fun jsCreateConnection(
    address: IPSocketAddress,
    listener: () -> Unit,
): JsSocket {
    val net = node_net
    val options = JsObject.new()
    options["port"] = address.port.value
    options["host"] = address.address.value
    options["family"] = when (address.address) {
        is IPAddress.V4 -> 4
        is IPAddress.V6 -> 6
    }
    return jsExternTryCatch { net.createConnection(options, listener) }
}

@InternalKmpTorApi
@Throws(Throwable::class)
public fun jsCreateConnection(
    path: File,
    listener: () -> Unit,
): JsSocket {
    val net = node_net
    val options = JsObject.new()
    options["path"] = path.path
    return jsExternTryCatch { net.createConnection(options, listener) }
}

@InternalKmpTorApi
public expect fun JsSocket.onData(block: (buf: Buffer) -> Unit): Disposable.Once

@InternalKmpTorApi
public expect fun JsSocket.onceClose(block: (hadError: Boolean) -> Unit): Disposable.Once

@InternalKmpTorApi
public expect fun JsSocket.onceDrain(block: () -> Unit): Disposable.Once

@InternalKmpTorApi
public expect fun JsSocket.onceError(block: (Throwable) -> Unit): Disposable.Once
