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
import java.net.InetAddress
import javax.net.ServerSocketFactory

actual object PortUtil {

    private const val LOCAL_HOST = "localhost"
    private const val ANDROID_MAIN_THREAD_EXCEPTION = "android.os.NetworkOnMainThreadException"

    /**
     * Must be called from a background thread.
     *
     * @throws [RuntimeException] if called from Android's Main thread.
     * */
    @JvmStatic
    @Throws(RuntimeException::class)
    actual fun isTcpPortAvailable(port: Port): Boolean {
        return try {
            realIsTcpPortAvailable(port.value)
            true
        } catch (e: Exception) {
            if (e.toString() == ANDROID_MAIN_THREAD_EXCEPTION) {
                throw e
            } else {
                false
            }
        }
    }

    /**
     * Will find the next available [Port] starting from the provided
     * [port], checking availability in increments of 1 up to the specified
     * limit of times.
     *
     * ex1: (port = Port(9050), limit = 50) Will check availability from
     *   9050 to 9100
     *
     * ex2: (port = Port(65535), limit = 50) will check availability from
     *   65535, and 0 to 48
     *
     * If the initial [port] is available, it will be returned.
     *
     * @throws [RuntimeException] if:
     *   - [limit] has been reached
     *   - [limit] is less than 1 or greater than 65535
     *   - called from Android's Main Thread.
     * */
    @JvmStatic
    @Throws(RuntimeException::class)
    actual fun findNextAvailableTcpPort(port: Port, limit: Int): Port {
        if (limit !in 1..Port.MAX) {
            throw RuntimeException("limit must be greater than or equal to 1")
        }

        var currentPort = port.value
        var countDown = limit

        while (countDown >= 0) {
            try {
                realIsTcpPortAvailable(currentPort)
                return Port(currentPort)
            } catch (e: Exception) {
                if (e.toString() == "android.os.NetworkOnMainThreadException") {
                    throw e
                } else {
                    countDown--
                    currentPort = if (currentPort == Port.MAX) {
                        Port.MIN
                    } else {
                        currentPort + 1
                    }
                }
            }
        }

        throw RuntimeException("Failed to find an available Port")
    }

    // if it doesn't throw exception, port is available
    @Throws(Exception::class)
    private fun realIsTcpPortAvailable(port: Int) {
        // check if TCP port is available. Will throw exception otherwise.
        val serverSocket = ServerSocketFactory.getDefault().createServerSocket(
            port,
            1,
            InetAddress.getByName(LOCAL_HOST)
        )
        serverSocket.close()
    }

}
