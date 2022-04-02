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
package io.matthewnelson.kmp.tor.controller.common.internal

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import java.net.InetAddress

actual object ControllerUtils {

    @InternalTorApi
    const val ANDROID_NET_MAIN_THREAD_EXCEPTION = "android.os.NetworkOnMainThreadException"

    @Volatile
    @Suppress("ObjectPropertyName")
    private var _localhostAddress: String? = null

    /**
     * Returns the resolved IP address for localhost (typically 127.0.0.1).
     *
     * @throws [RuntimeException] if being called for the first time and is
     *   called from Android's Main Thread
     * */
    @JvmStatic
    @InternalTorApi
    @Throws(RuntimeException::class)
    actual fun localhostAddress(): String =
        _localhostAddress ?: synchronized(this) {
            _localhostAddress ?: run {
                val address = try {
                    InetAddress.getByName("localhost").hostAddress
                } catch (e: Exception) {
                    if (e.toString() == ANDROID_NET_MAIN_THREAD_EXCEPTION) {
                        throw e
                    }
                    null
                }

                (address ?: "127.0.0.1")
                    .also { _localhostAddress = it }
            }
        }

    init {
        // Call from BG thread immediately once ControllerUtils is referenced
        Thread {
            @OptIn(InternalTorApi::class)
            localhostAddress()
        }.start()
    }
}
