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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.util.toInetAddress
import java.net.InetAddress
import java.net.ServerSocket

@JvmInline
internal actual value class ServerSocketProducer private actual constructor(
    private actual val value: Any
) {

    @Throws(Exception::class)
    @OptIn(ExperimentalStdlibApi::class)
    internal actual fun open(port: Int): AutoCloseable {
        return ServerSocket(port, /* backlog */ 1, value as InetAddress)
    }

    internal actual companion object {

        @JvmSynthetic
        @Throws(IOException::class)
        internal actual fun IPAddress.toServerSocketProducer(): ServerSocketProducer {
            val inet = toInetAddress()
            return ServerSocketProducer(inet)
        }
    }
}
