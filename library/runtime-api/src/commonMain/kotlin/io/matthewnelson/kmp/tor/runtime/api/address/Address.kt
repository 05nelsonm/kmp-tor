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

import kotlin.jvm.JvmField

/**
 * Base abstraction for all address types
 *
 * @see [IPAddress]
 * @see [OnionAddress]
 * @see [ProxyAddress]
 * */
public sealed class Address(
    @JvmField
    public val value: String,
): Comparable<Address> {

    /**
     * Returns the [value] in it's canonicalized hostname form
     *
     * e.g.
     *
     *     println("127.0.0.1"
     *         .toIPAddressV4()
     *         .canonicalHostname()
     *     )
     *     // 127.0.0.1
     *
     *     println("::1"
     *         .toIPAddressV6()
     *         .canonicalHostname()
     *     )
     *     // [::1]
     *
     *     println("http://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion"
     *         .toOnionAddressV3()
     *         .canonicalHostname()
     *     )
     *     // 2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion
     *
     *     println("http://127.0.0.1:8081/path"
     *         .toProxyAddress()
     *         .canonicalHostName()
     *     )
     *     // 127.0.0.1
     * */
    public abstract fun canonicalHostname(): String

    public final override fun compareTo(other: Address): Int = value.compareTo(other.value)

    public final override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Address) return false
        if (other::class != this::class) return false
        return other.value == value
    }

    public final override fun hashCode(): Int {
        var result = 17
        result = result * 31 + this::class.hashCode()
        result = result * 31 + value.hashCode()
        return result
    }

    public final override fun toString(): String = value
}
