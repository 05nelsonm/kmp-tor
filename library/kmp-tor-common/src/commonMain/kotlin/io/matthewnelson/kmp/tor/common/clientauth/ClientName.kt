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
package io.matthewnelson.kmp.tor.common.clientauth

import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

/**
 * Holder for a client name.
 *
 * Must be between 1 and 16 characters, and not contain the following:
 *  - spaces
 *  - \n
 *  - \r
 *  - \t
 *
 * @see [RealClientName]
 * */
sealed interface ClientName {

    val value: String

    companion object {

        @JvmStatic
        @Throws(IllegalArgumentException::class)
        operator fun invoke(name: String): ClientName {
            return RealClientName(name)
        }

        @JvmStatic
        fun fromStringOrNull(name: String): ClientName? {
            return try {
                RealClientName(name)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}

@JvmInline
private value class RealClientName(override val value: String): ClientName {

    init {
        require(value.length in 1..16) {
            "ClientName must be between 1 and 16 characters in length"
        }
        require(!value.contains(' ')) {
            "ClientName cannot contain spaces"
        }
        require(!value.contains('\n')) {
            "ClientName cannot contain '\\n'"
        }
        require(!value.contains('\r')) {
            "ClientName cannot contain '\\r'"
        }
        require(!value.contains('\t')) {
            "ClientName cannot contain '\\t'"
        }
    }

    override fun toString(): String {
        return "ClientName(value=$value)"
    }
}
