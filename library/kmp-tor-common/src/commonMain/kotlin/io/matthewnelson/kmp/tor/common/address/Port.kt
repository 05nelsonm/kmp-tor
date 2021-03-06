/*
 * Copyright (c) 2021 Matthew Nelson
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

import io.matthewnelson.component.parcelize.Parcelable
import io.matthewnelson.component.parcelize.Parcelize
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a valid port between 0 and 65535
 *
 * @see [RealPort]
 * @throws [IllegalArgumentException] if port is not valid
 * */
@SealedValueClass
@OptIn(ExperimentalTorApi::class)
sealed interface Port: Parcelable {

    val value: Int

    companion object {
        const val MIN = 0
        const val MAX = 65535

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(port: Int): Port {
            return RealPort(port)
        }

        @JvmStatic
        fun fromIntOrNull(port: Int?): Port? {
            return try {
                RealPort(port ?: return null)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

@JvmInline
@Parcelize
private value class RealPort(override val value: Int): Port {

    init {
        require(value in Port.MIN..Port.MAX) {
            "Invalid port range. Must be between ${Port.MIN} and ${Port.MAX}"
        }
    }

    override fun toString(): String = "Port(value=$value)"
}
