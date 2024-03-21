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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.util

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.net_createServer
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.onError
import kotlin.test.fail

@OptIn(ExperimentalStdlibApi::class)
class PortUtilJsUnitTest: PortUtilBaseTest() {

    override fun openServerSocket(
        ipAddress: IPAddress,
        port: Int,
    ): AutoCloseable {
        @OptIn(InternalKmpTorApi::class)
        val server = net_createServer { it.destroy() }
        server.onError { err -> fail(err.toString()) }
        server.listen(port, ipAddress.value, 1) {}
        return object : AutoCloseable {
            override fun close() { server.close() }
        }
    }
}
