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

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/**
 * Holder for a single proxy address' parts
 *
 * Example:
 *
 *   127.0.0.1:9050
 *
 * Will be translated to:
 *
 *   ProxyAddress(ipAddress=127.0.0.1, port=Port(value=9050))
 *
 * @see [fromString]
 * */
data class ProxyAddress(
    @JvmField
    val ipAddress: String,
    @JvmField
    val port: Port
) {

    override fun toString(): String {
        return "$ipAddress:${port.value}"
    }

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun fromString(address: String): ProxyAddress {
            val splits = address.split(':')

            return try {
                ProxyAddress(
                    ipAddress = splits[0].trim(),
                    port = Port(splits[1].trim().toInt())
                )
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Failed parse $address for an ipAddress and port")
            }
        }
    }
}