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
package io.matthewnelson.kmp.tor.manager.util

import io.matthewnelson.kmp.tor.common.address.Port
import kotlin.jvm.JvmStatic

expect object PortUtil {

    /**
     * Must be called from a background thread.
     *
     * @throws [RuntimeException] if called from Android's Main thread.
     * */
    @JvmStatic
    @Throws(RuntimeException::class)
    fun isTcpPortAvailable(port: Port): Boolean

    /**
     * Will find the next available [Port] starting from the provided
     * [port], checking availability in increments of 1 up to the specified
     * limit of times.
     *
     * ex1: (port = Port(9050), limit = 50) Will check availability from
     *   9050 to 9100
     *
     * ex2: (port = Port(65535), limit = 50) will check availability from
     *   65535, and 1024 to 1073
     *
     * If the initial [port] is available, it will be returned.
     *
     * @throws [RuntimeException] if:
     *   - [limit] has been reached
     *   - [limit] is less than 1
     *   - called from Android's Main Thread.
     * */
    @JvmStatic
    @Throws(RuntimeException::class)
    fun findNextAvailableTcpPort(port: Port, limit: Int): Port
}
