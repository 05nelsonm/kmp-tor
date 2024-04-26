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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ConfigBuilderCallback
import io.matthewnelson.kmp.tor.runtime.ConfigBuilderCallback.Companion.putDefaults
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.*
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress.Companion.toProxyAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.builder.ExtendedTorConfigBuilder

/**
 * Generates a minimum viable [TorConfig] using the provided settings from
 * [TorRuntime.Builder] and [TorRuntime.Environment].
 *
 * [generate] is called from a background thread during runtime operations
 * prior to each tor daemon start.
 * */
@OptIn(InternalKmpTorApi::class)
internal class TorConfigGenerator internal constructor(
    internal val environment: TorRuntime.Environment,
    private val omitGeoIPFileSettings: Boolean,
    private val config: Set<ConfigBuilderCallback>,
    private val isPortAvailable: suspend (LocalHost, Port) -> Boolean,
) {

    @Throws(Exception::class)
    internal suspend fun generate(
        n: Notifier,
    ): Pair<TorConfig, ResourceInstaller.Paths.Tor> {
        n.notify(LOG.DEBUG, "Installing tor resources (if needed)")
        val paths = environment.torResource.install()

        val config = createConfig(paths, n).validateTCPPorts(n)
        return config to paths
    }

    private fun createConfig(
        paths: ResourceInstaller.Paths.Tor,
        n: Notifier,
    ): TorConfig = TorConfig.Builder {
        try {
            n.notify(LOG.DEBUG, "Refreshing localhost IP address cache")
            LocalHost.refreshCache()
        } catch (_: IOException) {}

        // Apply library consumers' configuration(s)
        config.forEach { block -> apply(environment, block) }

        putDefaults(environment, omitGeoIPFileSettings, paths)
    }

    private suspend fun TorConfig.validateTCPPorts(
        n: Notifier,
    ): TorConfig {
        val ports = filterByAttribute<Keyword.Attribute.Port>().filter { setting ->
            setting[Extra.AllowReassign] == true
        }

        if (ports.isEmpty()) return this

        val reassignments = ports.mapNotNull { setting ->
            val (host, port) = setting.argument.toProxyAddressOrNull().let { pAddress ->
                if (pAddress != null) {
                    val host = when (pAddress.address) {
                        is IPAddress.V4 -> LocalHost.IPv4
                        is IPAddress.V6 -> LocalHost.IPv6
                    }

                    host to pAddress.port
                } else {
                    LocalHost.IPv4 to setting.argument.toPortProxy()
                }
            }

            if (isPortAvailable(host, port)) return@mapNotNull null
            val reassigned = setting.reassignTCPPortAutoOrNull() ?: return@mapNotNull null
            n.notify(
                LOG.WARN,
                "UNAVAILABLE_PORT[${setting.keyword}] ${setting.argument} reassigned to 'auto'"
            )
            setting to reassigned
        }

        if (reassignments.isEmpty()) return this

        return TorConfig.Builder(other = this) {
            reassignments.forEach { (old, new) ->
                (this as ExtendedTorConfigBuilder).remove(old)
                put(new)
            }
        }
    }
}
