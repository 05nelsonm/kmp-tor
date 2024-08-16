/*
 * Copyright (c) 2024 Matthew Nelson
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
@file:Suppress("FunctionName", "PropertyName")

package io.matthewnelson.kmp.tor.runtime.core.builder

import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Configure flags that are specific to [TorConfig.__SocksPort].
 *
 * - `null`  - no action (default)
 * - `true`  - add the flag if not present
 * - `false` - remove the flag if present
 *
 * [tor-man#OtherSocksPortFlags](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OtherSocksPortFlags)
 * */
@KmpTorDsl
public class SocksFlagBuilder private constructor() {

    @JvmField
    public var NoIPv4Traffic: Boolean? = null
    @JvmField
    public var IPv6Traffic: Boolean? = null
    @JvmField
    public var PreferIPv6: Boolean? = null
    @JvmField
    public var NoDNSRequest: Boolean? = null
    @JvmField
    public var NoOnionTraffic: Boolean? = null
    @JvmField
    public var OnionTrafficOnly: Boolean? = null
    @JvmField
    public var CacheIPv4DNS: Boolean? = null
    @JvmField
    public var CacheIPv6DNS: Boolean? = null
    @JvmField
    public var CacheDNS: Boolean? = null
    @JvmField
    public var UseIPv4Cache: Boolean? = null
    @JvmField
    public var UseIPv6Cache: Boolean? = null
    @JvmField
    public var UseDNSCache: Boolean? = null
    @JvmField
    public var PreferIPv6Automap: Boolean? = null
    @JvmField
    public var PreferSOCKSNoAuth: Boolean? = null

    internal companion object {

        @JvmSynthetic
        internal fun configure(
            flags: MutableSet<String>,
            block: ThisBlock<SocksFlagBuilder>,
        ) {
            val b = SocksFlagBuilder().apply(block)

            b.NoIPv4Traffic?.let {
                val flag = "NoIPv4Traffic"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.IPv6Traffic?.let {
                val flag = "IPv6Traffic"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.PreferIPv6?.let {
                val flag = "PreferIPv6"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.NoDNSRequest?.let {
                val flag = "NoDNSRequest"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.NoOnionTraffic?.let {
                val flag = "NoOnionTraffic"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.OnionTrafficOnly?.let {
                val flag = "OnionTrafficOnly"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.CacheIPv4DNS?.let {
                val flag = "CacheIPv4DNS"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.CacheIPv6DNS?.let {
                val flag = "CacheIPv6DNS"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.CacheDNS?.let {
                val flag = "CacheDNS"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.UseIPv4Cache?.let {
                val flag = "UseIPv4Cache"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.UseIPv6Cache?.let {
                val flag = "UseIPv6Cache"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.UseDNSCache?.let {
                val flag = "UseDNSCache"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.PreferIPv6Automap?.let {
                val flag = "PreferIPv6Automap"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.PreferSOCKSNoAuth?.let {
                val flag = "PreferSOCKSNoAuth"
                if (it) flags.add(flag) else flags.remove(flag)
            }
        }
    }
}
