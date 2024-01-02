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
@file:Suppress("FunctionName")

package io.matthewnelson.kmp.tor.runtime.api.config

import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.api.ThisBlock
import io.matthewnelson.kmp.tor.runtime.api.apply
import kotlin.jvm.JvmSynthetic

/**
 * [OtherSocksPortFlags](https://2019.www.torproject.org/docs/tor-manual.html.en#OtherSocksPortFlags)
 * */
@KmpTorDsl
public class SocksFlagBuilder private constructor(private val flags: MutableSet<String>) {

    // To inhibit modification after closure
    private var isConfigured: Boolean = false

    @KmpTorDsl
    public fun NoIPv4Traffic(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("NoIPv4Traffic")
        return this
    }

    @KmpTorDsl
    public fun IPv6Traffic(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("IPv6Traffic")
        return this
    }

    @KmpTorDsl
    public fun PreferIPv6(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("PreferIPv6")
        return this
    }

    @KmpTorDsl
    public fun NoDNSRequest(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("NoDNSRequest")
        return this
    }

    @KmpTorDsl
    public fun NoOnionTraffic(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("NoOnionTraffic")
        return this
    }

    @KmpTorDsl
    public fun OnionTrafficOnly(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("OnionTrafficOnly")
        return this
    }

    @KmpTorDsl
    public fun CacheIPv4DNS(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("CacheIPv4DNS")
        return this
    }

    @KmpTorDsl
    public fun CacheIPv6DNS(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("CacheIPv6DNS")
        return this
    }

    @KmpTorDsl
    public fun CacheDNS(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("CacheDNS")
        return this
    }

    @KmpTorDsl
    public fun UseIPv4Cache(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("UseIPv4Cache")
        return this
    }

    @KmpTorDsl
    public fun UseIPv6Cache(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("UseIPv6Cache")
        return this
    }

    @KmpTorDsl
    public fun UseDNSCache(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("UseDNSCache")
        return this
    }

    @KmpTorDsl
    public fun PreferIPv6Automap(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("PreferIPv6Automap")
        return this
    }

    @KmpTorDsl
    public fun PreferSOCKSNoAuth(): SocksFlagBuilder {
        if (isConfigured) return this
        flags.add("PreferSOCKSNoAuth")
        return this
    }

    internal companion object {

        @JvmSynthetic
        internal fun configure(
            flags: MutableSet<String>,
            block: ThisBlock<SocksFlagBuilder>,
        ) { SocksFlagBuilder(flags).apply(block).isConfigured = true }
    }
}