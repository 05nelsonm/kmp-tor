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
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller // TODO: REMOVE
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ConfigCallback
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress.Companion.toIPSocketAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.filterByAttribute
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopePort
import io.matthewnelson.kmp.tor.runtime.core.config.builder.RealBuilderScopeTorConfig

/**
 * Generates a minimum viable [TorConfig] using the provided settings from
 * [TorRuntime.BuilderScope] and [TorRuntime.Environment].
 *
 * [generate] is called from a background thread during runtime operations
 * prior to each tor daemon start.
 * */
@OptIn(InternalKmpTorApi::class)
internal class TorConfigGenerator internal constructor(
    internal val environment: TorRuntime.Environment,
    config: Set<ConfigCallback>,
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
        config.forEach { block -> with(block) { invoke(environment) } }
        ConfigCallback.Defaults.apply(this, environment, paths)
    }

    private suspend fun TorConfig.validateTCPPorts(
        NOTIFIER: Notifier,
    ): TorConfig {
        // All distinct ports that are "reassignable"
        val ports = filterByAttribute<TorOption.Attribute.PORT>().filter { setting ->
            setting.extras[BuilderScopePort.EXTRA_REASSIGNABLE] == true
            // If it's Hidden Service, HiddenServiceDir is always the
            // first argument so will be false (we don't want to reassign
            // that).
            && setting.items.first().isPortDistinct
        }

        if (ports.isEmpty()) return this

        val reassignments = ports.mapNotNull { setting ->
            val root = setting.items.first()
            val (host, port) = root.argument.toIPSocketAddressOrNull().let { ipAddress ->
                if (ipAddress != null) {
                    val host = when (ipAddress.address) {
                        is IPAddress.V4 -> LocalHost.IPv4
                        is IPAddress.V6 -> LocalHost.IPv6
                    }

                    host to ipAddress.port
                } else {
                    LocalHost.IPv4 to root.argument.toPortEphemeral()
                }
            }

            if (isPortAvailable(host, port)) return@mapNotNull null


            val reassigned = RealBuilderScopeTorConfig.reassignTCPPortAutoOrNull(setting)
                ?: return@mapNotNull null

            NOTIFIER.w(
                this@TorConfigGenerator,
                "UNAVAILABLE_PORT[${root.option}] ${root.argument} reassigned to 'auto'",
            )
            setting to reassigned
        }

        if (reassignments.isEmpty()) return this

        val newSettings = settings.toMutableSet()
        reassignments.forEach { (old, new) ->
            newSettings.remove(old)
            newSettings.add(new)
        }

        return TorConfig.Builder { putAll(newSettings) }
    }

    public override fun toString(): String = toFIDString(includeHashCode = false)
}
