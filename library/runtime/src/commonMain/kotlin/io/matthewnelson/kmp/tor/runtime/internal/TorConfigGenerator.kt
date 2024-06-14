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
@file:Suppress("LocalVariableName")

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ConfigBuilderCallback
import io.matthewnelson.kmp.tor.runtime.ConfigBuilderCallback.Companion.putDefaults
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.*
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress.Companion.toIPSocketAddressOrNull
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
    config: Set<ConfigBuilderCallback>,
    private val isPortAvailable: suspend (LocalHost, Port) -> Boolean,
): FileID by environment {

    private val config = config.toImmutableSet()

    @Throws(Exception::class)
    internal suspend fun generate(
        NOTIFIER: Notifier,
    ): Pair<TorConfig, ResourceInstaller.Paths.Tor> {
        NOTIFIER.d(this, "Installing tor resources (if needed)")
        val paths = environment.torResource.install()

        val config = createConfig(paths, NOTIFIER).validateTCPPorts(NOTIFIER)
        return config to paths
    }

    private fun createConfig(
        paths: ResourceInstaller.Paths.Tor,
        NOTIFIER: Notifier,
    ): TorConfig = TorConfig.Builder {
        NOTIFIER.d(this@TorConfigGenerator, "Refreshing localhost IP address cache")
        try {
            LocalHost.refreshCache()
        } catch (_: IOException) {
            NOTIFIER.w(this@TorConfigGenerator, "Refreshing localhost IP addresses failed")
        }

        // Apply library consumers' configuration(s)
        config.forEach { block -> apply(environment, block) }

        putDefaults(environment, paths)
    }

    private suspend fun TorConfig.validateTCPPorts(
        NOTIFIER: Notifier,
    ): TorConfig {
        val ports = filterByAttribute<Keyword.Attribute.Port>().filter { setting ->
            setting[Extra.AllowReassign] == true
        }

        if (ports.isEmpty()) return this

        val reassignments = ports.mapNotNull { setting ->
            val (host, port) = setting.argument.toIPSocketAddressOrNull().let { pAddress ->
                if (pAddress != null) {
                    val host = when (pAddress.address) {
                        is IPAddress.V4 -> LocalHost.IPv4
                        is IPAddress.V6 -> LocalHost.IPv6
                    }

                    host to pAddress.port
                } else {
                    LocalHost.IPv4 to setting.argument.toPortEphemeral()
                }
            }

            if (isPortAvailable(host, port)) return@mapNotNull null
            val reassigned = setting.reassignTCPPortAutoOrNull() ?: return@mapNotNull null
            NOTIFIER.w(
                this@TorConfigGenerator,
                "UNAVAILABLE_PORT[${setting.keyword}] ${setting.argument} reassigned to 'auto'",
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

    public override fun toString(): String = toFIDString(includeHashCode = false)
}
