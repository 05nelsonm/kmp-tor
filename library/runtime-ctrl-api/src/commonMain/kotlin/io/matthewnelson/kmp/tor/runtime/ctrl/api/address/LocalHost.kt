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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.address

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.platformResolveIPv4
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.platformResolveIPv6
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.time.TimeSource

public object LocalHost: Address("localhost") {

    @JvmStatic
    @Throws(IOException::class)
    public fun resolveIPv4(): IPAddress.V4 = Cache.V4.resolve(checkCache = true)

    @JvmStatic
    @Throws(IOException::class)
    public fun resolveIPv6(): IPAddress.V6 = Cache.V6.resolve(checkCache = true)

    public override fun canonicalHostname(): String = value

    private class Cache private constructor(private val address: IPAddress) {

        private val timeMark = TimeSource.Monotonic.markNow()

        private fun isExpired(): Boolean {
            val elapsedNanos = timeMark.elapsedNow().inWholeNanoseconds
            // java uses 5s expiry time, so the extra 250 ns
            // ensures there is always going to be a refresh.
            return elapsedNanos > 5_000_000_250L
        }

        object V4 {

            @Volatile
            private var cache: Cache? = null

            @JvmStatic
            fun getOrNull(): IPAddress.V4? {
                val cached = cache ?: return null
                if (!cached.isExpired()) {
                    return cached.address as IPAddress.V4
                }
                cache = null
                return null
            }

            @JvmStatic
            @Throws(IOException::class)
            fun resolve(checkCache: Boolean): IPAddress.V4 {
                if (checkCache) getOrNull()?.let { return it }
                val address: IPAddress.V4 = try {
                    platformResolveIPv4()
                } catch (t: Throwable) {
                    throw t.wrapIOException { "Failed to resolve IPv4 address for $value" }
                }
                cache = Cache(address)
                return address
            }
        }

        object V6 {

            @Volatile
            private var cache: Cache? = null

            @JvmStatic
            fun getOrNull(): IPAddress.V6? {
                val cached = cache ?: return null
                if (!cached.isExpired()) {
                    return cached.address as IPAddress.V6
                }
                cache = null
                return null
            }

            @JvmStatic
            @Throws(IOException::class)
            fun resolve(checkCache: Boolean): IPAddress.V6 {
                if (checkCache) getOrNull()?.let { return it }
                val address: IPAddress.V6 = try {
                    platformResolveIPv6()
                } catch (t: Throwable) {
                    throw t.wrapIOException { "Failed to resolve IPv6 address for $value" }
                }
                cache = Cache(address)
                return address
            }
        }
    }

    @JvmStatic
    @InternalKmpTorApi
    @Throws(IOException::class)
    public fun resolveIPv4NoCache(): IPAddress.V4 = Cache.V4.resolve(checkCache = false)

    @JvmStatic
    @InternalKmpTorApi
    @Throws(IOException::class)
    public fun resolveIPv6NoCache(): IPAddress.V6 = Cache.V6.resolve(checkCache = false)

    @JvmSynthetic
    internal fun cachedIPv4OrNull(): IPAddress.V4? = Cache.V4.getOrNull()
    @JvmSynthetic
    internal fun cachedIPv6OrNull(): IPAddress.V6? = Cache.V6.getOrNull()
}
