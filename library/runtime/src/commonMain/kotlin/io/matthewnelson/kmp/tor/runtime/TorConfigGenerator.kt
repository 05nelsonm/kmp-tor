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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.*
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress.Companion.toProxyAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.builder.ExtendedTorConfigBuilder
import io.matthewnelson.kmp.tor.runtime.core.builder.UnixSocketBuilder
import io.matthewnelson.kmp.tor.runtime.core.util.isAvailableAsync
import kotlin.jvm.JvmSynthetic

/**
 * Generates a minimum viable [TorConfig] using the provided settings from
 * [TorRuntime.Builder] and [TorRuntime.Environment].
 *
 * [generate] is called from a background thread during runtime operations
 * prior to each tor daemon start.
 * */
@OptIn(InternalKmpTorApi::class)
public class TorConfigGenerator private constructor(
    private val environment: TorRuntime.Environment,
    private val allowPortReassignment: Boolean,
    private val omitGeoIPFileSettings: Boolean,
    private val config: Set<ConfigBuilderCallback>,
    private val isPortAvailable: suspend (LocalHost, Port) -> Boolean,
) {

    @Throws(Exception::class)
    internal suspend fun generate(n: Notifier): TorConfig = createConfig(n)
        .validateTCPPorts(n)

    private fun createConfig(n: Notifier): TorConfig = TorConfig.Builder {
        n.notify(LOG.DEBUG, "Installing tor resources (if needed)")
        val pathsTor = environment.torResource.install()

        try {
            n.notify(LOG.DEBUG, "Refreshing localhost IP address cache")
            LocalHost.refreshCache()
        } catch (_: IOException) {}

        // Apply library consumers' configuration(s)
        config.forEach { block -> apply(environment, block) }

        putDefaults(pathsTor)
    }

    private fun Builder.putDefaults(pathsTor: ResourceInstaller.Paths.Tor) {
        // Dirs/Files
        if (!omitGeoIPFileSettings) {
            put(GeoIPFile) { file = pathsTor.geoip }
            put(GeoIPv6File) { file = pathsTor.geoip6 }
        }

        putIfAbsent(DataDirectory) {
            directory = environment.workDir
                .resolve(DataDirectory.DEFAULT_NAME)
        }
        putIfAbsent(CacheDirectory) {
            directory = environment.cacheDir
        }
        putIfAbsent(ControlPortWriteToFile) {
            file = environment.workDir
                .resolve(ControlPortWriteToFile.DEFAULT_NAME)
        }

        // Authentication
        if (
            !(this as ExtendedTorConfigBuilder).contains(CookieAuthFile)
            // && !contains(HashedControlPassword)
        ) {
            put(CookieAuthFile) {
                file = environment.workDir
                    .resolve(CookieAuthFile.DEFAULT_NAME)
            }
        }

        // Ports
        if (!contains(__SocksPort)) {
            // Add default socks port so that port availability check
            // can reassign it to auto if needed
            put(__SocksPort) { asPort { /* default: 9050 */ } }
        }

        if (!contains(__ControlPort)) {
            put(__ControlPort) {
                try {
                    // Prefer using Unix Domain Sockets where possible
                    asUnixSocket {
                        file = environment.workDir
                            .resolve(UnixSocketBuilder.DEFAULT_NAME_CTRL)
                    }
                } catch (_: UnsupportedOperationException) {
                    // fallback to TCP if unavailable on the system
                    asPort { auto() }
                }
            }
        }

        // Required for TorRuntime
        put(DisableNetwork) { disable = true }
        put(RunAsDaemon) { enable = false }
        put(__OwningControllerProcess) { /* default */ }
    }

    private suspend fun TorConfig.validateTCPPorts(n: Notifier): TorConfig {
        if (!allowPortReassignment) return this
        val ports = filterByAttribute<Keyword.Attribute.Port>().filter { setting ->
            if (setting.keyword.attributes.contains(Keyword.Attribute.HiddenService)) return@filter false
            // TODO: Investigate why Node.js does not adhere to filterByAttribute
            if (!setting.keyword.attributes.contains(Keyword.Attribute.Port)) return@filter false
            if (setting.argument == "0") return@filter false
            if (setting.argument == "auto") return@filter false
            // If configured as UnixSocket, it's filtered out by filterByAttribute
            true
        }

        if (ports.isEmpty()) return this

        val reassignments = ports.mapNotNull { setting ->
            val (host, port) = setting.argument.toProxyAddressOrNull().let { pAddress ->
                if (pAddress != null) {
                    val host = when (pAddress.address) {
                        is IPAddress.V4 -> LocalHost.IPv4
                        is IPAddress.V6 -> LocalHost.IPv6
                    }

                    Pair(host, pAddress.port)
                } else {
                    Pair(LocalHost.IPv4, setting.argument.toPortProxy())
                }
            }

            if (isPortAvailable(host, port)) return@mapNotNull null
            val reassigned = setting.reassignTCPPortAutoOrNull() ?: return@mapNotNull null
            n.notify(
                LOG.WARN,
                "UNAVAILABLE_PORT[${setting.keyword}] ${setting.argument} reassigned to 'auto'"
            )
            Pair(setting, reassigned)
        }

        if (reassignments.isEmpty()) return this

        return TorConfig.Builder(other = this) {
            reassignments.forEach { (old, new) ->
                (this as ExtendedTorConfigBuilder).remove(old)
                put(new)
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            environment: TorRuntime.Environment,
            allowPortReassignment: Boolean,
            omitGeoIPFileSettings: Boolean,
            config: Set<ConfigBuilderCallback>,
            isPortAvailable: suspend (LocalHost, Port) -> Boolean = { h, p -> p.isAvailableAsync(h) },
        ): TorConfigGenerator = TorConfigGenerator(
            environment,
            allowPortReassignment,
            omitGeoIPFileSettings,
            config,
            isPortAvailable
        )
    }
}
