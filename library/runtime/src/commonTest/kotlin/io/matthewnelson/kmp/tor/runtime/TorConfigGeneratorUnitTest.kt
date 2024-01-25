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

import io.matthewnelson.kmp.file.absoluteFile
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ctrl.api.ThisBlock
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorConfig
import io.matthewnelson.kmp.tor.runtime.ctrl.api.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Proxy.Companion.toPortProxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalKmpTorApi::class)
class TorConfigGeneratorUnitTest {

    private val environment = "".toFile().absoluteFile.let { workDir ->
        TorRuntime.Environment.Builder(workDir, workDir.resolve("cache")) { installationDir ->
            object : ResourceInstaller<Paths.Tor>(installationDir) {
                private val paths = Paths.Tor(
                    geoip = installationDir.resolve("geoip"),
                    geoip6 = installationDir.resolve("geoip6"),
                    tor = installationDir.resolve("tor")
                )

                override fun install(): Paths.Tor = paths
            }
        }
    }

    private val notifier = object : RuntimeEvent.Notifier {
        override fun <R : Any> notify(event: RuntimeEvent<R>, output: R) {}
    }

    @Test
    fun givenGeoipOmission_whenGenerate_thenDoesNotContainSettings() {
        val settings = newGenerator(omitGeoIPFileSettings = true).generate(notifier).settings
        assertEquals(0, settings.filterByKeyword<TorConfig.GeoIPFile.Companion>().size)
        assertEquals(0, settings.filterByKeyword<TorConfig.GeoIPv6File.Companion>().size)
    }

    @Test
    fun givenGeoipNoOmission_whenGenerate_thenContainsSettings() {
        with(newGenerator(omitGeoIPFileSettings = false).generate(notifier).settings) {
            assertContains(TorConfig.GeoIPFile)
            assertContains(TorConfig.GeoIPv6File)
        }
    }

    @Test
    fun givenMultipleUserConfigs_whenGenerate_thenAllAreApplied() {
        var invocations = 0
        newGenerator(
            config = listOf(
                ThisBlock.WithIt { _ -> invocations++ },
                ThisBlock.WithIt { _ -> invocations++ },
            )
        ).generate(notifier)

        assertEquals(2, invocations)
    }

    @Test
    fun givenNoConfig_whenGenerate_thenMinimumSettingsApplied() {
        with(newGenerator().generate(notifier).settings) {
            assertContains(TorConfig.DataDirectory)
            assertContains(TorConfig.CacheDirectory)
            assertContains(TorConfig.ControlPortWriteToFile)
            assertContains(TorConfig.CookieAuthFile)
            assertContains(TorConfig.__SocksPort)
            assertContains(TorConfig.__ControlPort)
            assertContains(TorConfig.DisableNetwork)
            assertContains(TorConfig.RunAsDaemon)
            assertContains(TorConfig.__OwningControllerProcess)
        }
    }

    @Test
    fun givenUnavailablePort_whenGenerate_thenPortRemovedAndReplaced() {
        // socks port at 9050 is automatically added
        val settings = newGenerator(
            allowPortReassignment = true,
            config = listOf(
                ThisBlock.WithIt {
                    put(TorConfig.__DNSPort) { port(1080.toPortProxy()) }
                }
            ),
            isPortAvailable = { _, _ -> false }
        ).generate(notifier).settings

        val socks = settings.filterByKeyword<TorConfig.__SocksPort.Companion>().first()
        assertEquals("auto", socks.argument)
        val dns = settings.filterByKeyword<TorConfig.__DNSPort.Companion>().first()
        assertEquals("auto", dns.argument)
    }

    private inline fun <reified K: TorConfig.Keyword> Set<TorConfig.Setting>.assertContains(keyword: K) {
        assertTrue(filterByKeyword<K>().isNotEmpty())
    }

    private fun newGenerator(
        allowPortReassignment: Boolean = true,
        omitGeoIPFileSettings: Boolean = false,
        config: List<ThisBlock.WithIt<TorConfig.Builder, TorRuntime.Environment>> = emptyList(),
        isPortAvailable: (LocalHost, Port) -> Boolean = { _, _ -> true },
    ): TorConfigGenerator = TorConfigGenerator.of(
        environment,
        allowPortReassignment,
        omitGeoIPFileSettings,
        config,
        isPortAvailable
    )
}
