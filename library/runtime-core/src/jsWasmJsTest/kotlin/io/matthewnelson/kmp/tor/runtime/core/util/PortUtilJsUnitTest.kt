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
package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.internal.js.JsObject
import io.matthewnelson.kmp.tor.runtime.core.internal.js.new
import io.matthewnelson.kmp.tor.runtime.core.internal.js.set
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.internal.node.node_net
import io.matthewnelson.kmp.tor.runtime.core.internal.node.onError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UNUSED")
@OptIn(InternalKmpTorApi::class)
class PortUtilJsUnitTest: PortUtilBaseTest() {

    override val isNodeJs: Boolean = true

    override suspend fun openServerSocket(
        ipAddress: IPAddress,
        port: Int,
    ): Closeable {
        val net = node_net
        val server = net.createServer { socket -> socket.destroy() }
        server.onError { t -> fail("Server Error", t) }
        val options = JsObject.new()
        options["port"] = port
        options["host"] = ipAddress.value
        options["backlog"] = 1
        server.listen(options) {}
        withContext(Dispatchers.Default) { delay(10.milliseconds) }
        return Closeable {
            server.close()
            server.unref()
        }
    }
}
