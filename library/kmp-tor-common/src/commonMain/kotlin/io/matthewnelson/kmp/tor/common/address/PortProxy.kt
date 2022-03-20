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
package io.matthewnelson.kmp.tor.common.address

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid proxy port between 1024 and 65535
 *
 * @throws [IllegalArgumentException] if port is not valid
 * */
@JvmInline
value class PortProxy(val value: Int) {

    init {
        require(value in MIN..Port.MAX) {
            "Invalid port range. Must be between $MIN and ${Port.MAX}"
        }
    }

    companion object {
        const val MIN = 1024

        @JvmStatic
        fun fromIntOrNull(port: Int?): PortProxy? {
            return try {
                PortProxy(port ?: return null)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
