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
 * Base abstraction for all address types in `kmp-tor`
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
     * Returns [value] in its canonicalized hostname form
     *
     * e.g.
     *
     *     "127.0.0.1"
     *         .toIPAddressV4()
     *         .canonicalHostName()
     *         .let { println(it) }
     *     // 127.0.0.1
     *
     *     "::1"
     *         .toIPAddressV6()
     *         .canonicalHostName()
     *         .let { println(it) }
     *     // [0:0:0:0:0:0:0:1]
     *
     *     "http://127.0.0.1:8081/path"
     *         .toIPSocketAddress()
     *         .canonicalHostName()
     *         .let { println(it) }
     *     // 127.0.0.1
     *
     *     "http://[::1%1]:8081/path"
     *         .toIPSocketAddress()
     *         .canonicalHostName()
     *         .let { println(it) }
     *     // [0:0:0:0:0:0:0:1%1]
     *
     *     "2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid"
     *         .toOnionAddressV3()
     *         .canonicalHostName()
     *         .let { println(it) }
     *     // 2gzyxa5ihm7nsggfxnu52rck2vv4rvmdlkiu3zzui5du4xyclen53wid.onion
     *
     *     LocalHost.IPv4
     *         .canonicalHostName()
     *         .let { println(it) }
     *     // localhost
     * */
    public fun canonicalHostName(): String = when (this) {
        is IPAddress.V4 -> value
        is IPAddress.V6 -> "[$value]"
        is IPSocketAddress -> address.canonicalHostName()
        is LocalHost -> value
        is OnionAddress -> "$value.onion"
    }

    public final override fun compareTo(other: Address): Int = value.compareTo(other.value)

    /** @suppress */
    public final override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Address) return false
        if (other::class != this::class) return false
        return other.value == value
    }

    /** @suppress */
    public final override fun hashCode(): Int {
        var result = 17
        result = result * 31 + this::class.hashCode()
        result = result * 31 + value.hashCode()
        return result
    }

    /** @suppress */
    public final override fun toString(): String = value
}
