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

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.wrapIOException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost.Cache.Companion.firstOrNull
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost.Cache.Companion.firstOrThrow
import io.matthewnelson.kmp.tor.runtime.ctrl.api.internal.resolveAll
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.time.TimeSource

public object LocalHost: Address("localhost") {

    @JvmStatic
    @Throws(IOException::class)
    public fun resolveIPv4(): IPAddress.V4 = Cache.resolve(checkCache = true).firstOrThrow()

    @JvmStatic
    @Throws(IOException::class)
    public fun resolveIPv6(): IPAddress.V6 = Cache.resolve(checkCache = true).firstOrThrow()

    public override fun canonicalHostname(): String = value

    private class Cache private constructor(private val addresses: Set<IPAddress>) {

        private val timeMark = TimeSource.Monotonic.markNow()

        private fun isNotExpired(): Boolean {
            val elapsedNanos = timeMark.elapsedNow().inWholeNanoseconds
            // java uses 5s expiry time, so the extra 250 ns
            // ensures there is always going to be a refresh.
            return elapsedNanos < 5_000_000_250L
        }

        companion object {

            @Volatile
            private var cache: Cache? = null

            @JvmStatic
            internal fun getOrNull(): Set<IPAddress>? {
                val cache = cache ?: return null
                if (cache.isNotExpired()) {
                    return cache.addresses
                }
                this.cache = null
                return null
            }

            @JvmStatic
            @Throws(IOException::class)
            internal fun resolve(checkCache: Boolean): Set<IPAddress> {
                if (checkCache) getOrNull()?.let { return it }
                val addresses = try {
                    resolveAll()
                } catch (t: Throwable) {
                    throw t.wrapIOException { "Failed to resolve IP addresses for $value" }
                }

                if (addresses.isEmpty()) {
                    throw IOException("No IP addresses found for $value")
                }

                cache = Cache(addresses.toImmutableSet())
                return addresses
            }

            @JvmStatic
            @Throws(IOException::class)
            inline fun <reified T: IPAddress> Set<IPAddress>.firstOrThrow(): T {
                return firstOrNull<T>()
                    ?: throw IOException("IP${T::class.simpleName?.lowercase()} address not found for $value")
            }

            @JvmStatic
            inline fun <reified T: IPAddress> Set<IPAddress>.firstOrNull(): T? {
                forEach { if (it is T) return it }
                return null
            }
        }
    }

    @InternalKmpTorApi
    @Throws(IOException::class)
    public fun refreshCache() { Cache.resolve(checkCache = false) }

    @JvmSynthetic
    internal fun cachedIPv4OrNull(): IPAddress.V4? = Cache.getOrNull()?.firstOrNull()
    @JvmSynthetic
    internal fun cachedIPv6OrNull(): IPAddress.V6? = Cache.getOrNull()?.firstOrNull()
}
