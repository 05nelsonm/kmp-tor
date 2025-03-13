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

import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.internal.net_createServer
import io.matthewnelson.kmp.tor.runtime.core.internal.onError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class PortUtilJsUnitTest: PortUtilBaseTest() {

    override val isNodeJs: Boolean = true

    override suspend fun openServerSocket(
        ipAddress: IPAddress,
        port: Int,
    ): AutoCloseable {
        val server = net_createServer { it.destroy(); Unit }
        server.onError { err -> fail("$err") }
        val options = js("{}")
        options["port"] = port
        options["host"] = ipAddress.value
        options["backlog"] = 1
        server.listen(options) {}
        withContext(Dispatchers.Default) { delay(10.milliseconds) }
        return object : AutoCloseable {
            override fun close() {
                server.close()
                server.unref()
            }
        }
    }
}
