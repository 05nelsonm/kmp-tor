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
package io.matthewnelson.kmp.tor.runtime.core.address

import kotlin.jvm.JvmField

/**
 * Base abstraction for all address types
 *
 * @see [IPAddress]
 * @see [IPSocketAddress]
 * @see [LocalHost]
 * @see [OnionAddress]
 * */
public sealed class Address(
    @JvmField
    public val value: String,
): Comparable<Address> {

    /**
     * Returns the [value] in its canonicalized hostname form
     *
     * e.g.
     *
     *     println("127.0.0.1"
     *         .toIPAddressV4()
     *         .canonicalHostName()
     *     )
     *     // 127.0.0.1
     *
     *     println("::1"
     *         .toIPAddressV6()
     *         .canonicalHostName()
     *     )
     *     // [::1]
     *
     *     println("http://127.0.0.1:8081/path"
     *         .toIPSocketAddress()
     *         .canonicalHostName()
     *     )
     *     // 127.0.0.1
     *
     *     println("http://2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion"
     *         .toOnionAddressV3()
     *         .canonicalHostName()
     *     )
     *     // 2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion
     * */
    public fun canonicalHostName(): String = when (this) {
        is IPAddress.V4 -> value
        is IPAddress.V6 -> "[$value]"
        is IPSocketAddress -> address.canonicalHostName()
        is LocalHost -> value
        is OnionAddress -> "$value.onion"
    }

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
