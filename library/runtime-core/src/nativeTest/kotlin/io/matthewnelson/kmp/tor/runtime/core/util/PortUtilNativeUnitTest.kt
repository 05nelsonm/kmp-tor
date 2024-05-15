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

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalStdlibApi::class)
class PortUtilNativeUnitTest: PortUtilBaseTest() {

    private lateinit var manager: SelectorManager

    @BeforeTest
    fun setup() {
        manager = SelectorManager()
    }

    @AfterTest
    fun tearDown() {
        manager.close()
    }

    override suspend fun openServerSocket(
        ipAddress: IPAddress,
        port: Int,
    ): AutoCloseable {
        val socket = aSocket(manager).tcp().bind(ipAddress.value, port)

        return object : AutoCloseable {
            override fun close() {
                socket.close()
            }
        }
    }
}
