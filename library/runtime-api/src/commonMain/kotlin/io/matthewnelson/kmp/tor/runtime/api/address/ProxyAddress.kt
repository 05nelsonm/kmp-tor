/*
 * Copyright (c) 2023 Matthew Nelson
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
package io.matthewnelson.kmp.tor.runtime.api.address

import io.matthewnelson.kmp.tor.runtime.api.address.IPAddress.Companion.toIPAddressOrNull
import io.matthewnelson.kmp.tor.runtime.api.address.Port.Companion.toPortOrNull
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Holder for a single proxy address' parts
 * */
public class ProxyAddress(
    @JvmField
    public val address: IPAddress,
    @JvmField
    public val port: Port,
): Address(address.canonicalHostname() + ':' + port) {

    public operator fun component1(): IPAddress = address
    public operator fun component2(): Port = port

    override fun canonicalHostname(): String = address.canonicalHostname()

    public companion object {

        @JvmStatic
        @JvmName("get")
        @Throws(IllegalArgumentException::class)
        public fun String.toProxyAddress(): ProxyAddress {
            return toProxyAddressOrNull()
                ?: throw IllegalArgumentException("$this is not a valid IP address & port")
        }

        @JvmStatic
        @JvmName("getOrNull")
        public fun String.toProxyAddressOrNull(): ProxyAddress? {
            val address = toIPAddressOrNull() ?: return null
            val port = toPortOrNull() ?: return null
            return ProxyAddress(address, port)
        }
    }
}