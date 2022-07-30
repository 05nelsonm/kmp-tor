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

import io.matthewnelson.component.parcelize.Parcelize
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid proxy port between 1024 and 65535
 *
 * @see [RealPortProxy]
 * @throws [IllegalArgumentException] if port is not valid
 * */
@SealedValueClass
@OptIn(ExperimentalTorApi::class)
sealed interface PortProxy: Port {

    companion object {
        const val MIN = 1024
        const val MAX = Port.MAX

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(port: Int): PortProxy {
            return RealPortProxy(port)
        }

        @JvmStatic
        fun fromIntOrNull(port: Int?): PortProxy? {
            return try {
                RealPortProxy(port ?: return null)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

@JvmInline
@Parcelize
private value class RealPortProxy(override val value: Int): PortProxy {

    init {
        require(value in PortProxy.MIN..PortProxy.MAX) {
            "Invalid port range. Must be between ${PortProxy.MIN} and ${PortProxy.MAX}"
        }
    }

    override fun toString(): String = "PortProxy(value=$value)"
}
