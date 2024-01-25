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
import io.matthewnelson.kmp.tor.runtime.ctrl.api.ThisBlock
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorConfig
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorConfig.*
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.ctrl.api.apply
import io.matthewnelson.kmp.tor.runtime.ctrl.api.builder.ExtendedTorConfigBuilder
import io.matthewnelson.kmp.tor.runtime.ctrl.api.builder.UnixSocketBuilder
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
    private val config: List<ThisBlock.WithIt<Builder, TorRuntime.Environment>>,
    private val isPortAvailable: (LocalHost, Port) -> Boolean,
) {

    @Throws(Exception::class)
    internal fun generate(n: Notifier): TorConfig = TorConfig.Builder {
        n.notify(LOG.DEBUG, "Installing tor resources (if needed)")
        val pathsTor = environment.torResource.install()

        try {
            n.notify(LOG.DEBUG, "Refreshing localhost IP address cache")
            LocalHost.refreshCache()
        } catch (_: IOException) {}

        // Apply library consumers' configuration(s)
        config.forEach { apply(it, environment) }

        validate(n, pathsTor)
    }

    private fun Builder.validate(n: Notifier, pathsTor: ResourceInstaller.Paths.Tor) {
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

        checkPortAvailability(n)

        // Required for TorRuntime
        put(DisableNetwork) { disable = true }
        put(RunAsDaemon) { enable = false }
        put(__OwningControllerProcess) { /* default */ }
    }

    private fun Builder.checkPortAvailability(n: Notifier) {
        if (!allowPortReassignment) return
        (this as ExtendedTorConfigBuilder).ports().forEach { port ->
            val reassigned = port.checkTCPPortAvailability(isPortAvailable) ?: return@forEach
            n.notify(
                LOG.WARN,
                "Unavailable Port[${port.argument.toPort()}]. " +
                "${port.keyword} reassigned to ${reassigned.argument}"
            )

            // Remove and replace
            remove(port)
            put(reassigned)
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            environment: TorRuntime.Environment,
            allowPortReassignment: Boolean,
            omitGeoIPFileSettings: Boolean,
            config: List<ThisBlock.WithIt<Builder, TorRuntime.Environment>>,
            isPortAvailable: (LocalHost, Port) -> Boolean = { h, p -> p.isAvailable(h) },
        ): TorConfigGenerator = TorConfigGenerator(
            environment,
            allowPortReassignment,
            omitGeoIPFileSettings,
            config,
            isPortAvailable
        )
    }
}
