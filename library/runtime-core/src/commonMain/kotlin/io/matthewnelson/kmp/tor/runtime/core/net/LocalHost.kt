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
package io.matthewnelson.kmp.tor.runtime.core.net

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost.Cache.Companion.firstOrNull
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost.Cache.Companion.firstOrThrow
import io.matthewnelson.kmp.tor.runtime.core.internal.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.time.TimeSource

/**
 * The host machine's loopback [IPv4] and [IPv6] addresses,
 * typically `127.0.0.1` and `::1`, respectively.
 *
 * Results from system sources are cached for 5s to inhibit
 * unnecessary repeated calls.
 * */
public sealed class LocalHost private constructor(): Address("localhost") {

    /**
     * Resolves `localhost` to an [IPAddress] via system calls
     *
     * **NOTE:** This is a blocking call and should be invoked from
     * a background thread.
     *
     * @throws [IOException] if there were any errors (e.g. calling
     *   from Main thread on Android)
     * */
    @Throws(IOException::class)
    public abstract fun resolve(): IPAddress

    public object IPv4: LocalHost() {

        @Throws(IOException::class)
        override fun resolve(): IPAddress.V4 = Cache.resolve(checkCache = true).firstOrThrow()

        @JvmSynthetic
        internal override fun fromCache(): IPAddress.V4? = Cache.getOrNull()?.firstOrNull()
    }

    public object IPv6: LocalHost() {

        @Throws(IOException::class)
        override fun resolve(): IPAddress.V6 = Cache.resolve(checkCache = true).firstOrThrow()

        @JvmSynthetic
        internal override fun fromCache(): IPAddress.V6? = Cache.getOrNull()?.firstOrNull()
    }

    private class Cache private constructor(addresses: Set<IPAddress>) {

        private val addresses: Set<IPAddress> = addresses.toImmutableSet()
        private val timeMark = TimeSource.Monotonic.markNow()

        private fun isNotExpired(): Boolean {
            val elapsedNanos = timeMark.elapsedNow().inWholeNanoseconds
            // java uses 5s expiry time, so the extra 250 ns
            // ensures there is always going to be a refresh.
            return elapsedNanos < 5_000_000_250L
        }

        companion object {

            @Volatile
            private var _cache: Cache? = null

            @JvmStatic
            internal fun getOrNull(): Set<IPAddress>? {
                val cache = _cache ?: return null
                if (cache.isNotExpired()) {
                    return cache.addresses
                }
                _cache = null
                return null
            }

            @JvmStatic
            @Throws(IOException::class)
            internal fun resolve(checkCache: Boolean): Set<IPAddress> {
                if (checkCache) getOrNull()?.let { return it }

                val addresses = LinkedHashSet<IPAddress>(2, 1.0F)

                try {
                    tryPlatformResolve(addresses)
                } catch (t: Throwable) {
                    // Android can throw if called from Main thread
                    // which we do not want to swallow.
                    throw t.wrapIOException { "Failed to resolve IP addresses for localhost" }
                }

                tryParsingIfConfig(addresses)
                tryParsingEtcHosts(addresses)

                // Lastly, add well-known loopback addresses as a fallback.
                addresses.add(IPAddress.V4.loopback())
                addresses.add(IPAddress.V6.loopback())

                val c = Cache(addresses)
                _cache = c
                return c.addresses
            }

            @JvmStatic
            @Throws(IOException::class)
            inline fun <reified T: IPAddress> Set<IPAddress>.firstOrThrow(): T {
                return firstOrNull<T>()
                    ?: throw IOException("IP${T::class.simpleName?.lowercase()} address not found for localhost")
            }

            @JvmStatic
            inline fun <reified T: IPAddress> Set<IPAddress>.firstOrNull(): T? {
                forEach { if (it is T) return it }
                return null
            }
        }
    }

    public companion object {

        /** @suppress */
        @InternalKmpTorApi
        @Throws(IOException::class)
        public fun refreshCache() { Cache.resolve(checkCache = false) }
    }

    @JvmSynthetic
    internal abstract fun fromCache(): IPAddress?
}
