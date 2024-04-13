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

import io.matthewnelson.kmp.file.absoluteFile
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.ConfigBuilderCallback
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(InternalKmpTorApi::class)
class TorConfigGeneratorUnitTest {

    private val environment = "".toFile().absoluteFile.resolve("config-test").let { workDir ->
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
    fun givenGeoipOmission_whenGenerate_thenDoesNotContainSettings() = runTest {
        val settings = newGenerator(omitGeoIPFileSettings = true).generate(notifier).settings
        assertEquals(0, settings.filterByKeyword<TorConfig.GeoIPFile.Companion>().size)
        assertEquals(0, settings.filterByKeyword<TorConfig.GeoIPv6File.Companion>().size)
    }

    @Test
    fun givenGeoipNoOmission_whenGenerate_thenContainsSettings() = runTest {
        with(newGenerator(omitGeoIPFileSettings = false).generate(notifier)) {
            assertContains(TorConfig.GeoIPFile)
            assertContains(TorConfig.GeoIPv6File)
        }
    }

    @Test
    fun givenMultipleUserConfigs_whenGenerate_thenAllAreApplied() = runTest {
        var invocations = 0
        newGenerator(
            config = setOf(
                ConfigBuilderCallback { _ -> invocations++ },
                ConfigBuilderCallback { _ -> invocations++ },
            )
        ).generate(notifier)

        assertEquals(2, invocations)
    }

    @Test
    fun givenNoConfig_whenGenerate_thenMinimumSettingsApplied() = runTest {
        with(newGenerator().generate(notifier)) {
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
    fun givenUnavailablePort_whenGenerate_thenPortRemovedAndReplaced() = runTest {
        // socks port at 9050 is automatically added
        val settings = newGenerator(
            config = setOf(
                ConfigBuilderCallback {
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

    @Suppress("UNUSED_PARAMETER")
    private inline fun <reified K: TorConfig.Keyword> TorConfig.assertContains(keyword: K) {
        assertTrue(filterByKeyword<K>().isNotEmpty())
    }

    private fun newGenerator(
        omitGeoIPFileSettings: Boolean = false,
        config: Set<ConfigBuilderCallback> = emptySet(),
        isPortAvailable: suspend (LocalHost, Port) -> Boolean = { _, _ -> true },
    ): TorConfigGenerator = TorConfigGenerator(
        environment,
        omitGeoIPFileSettings,
        config,
        isPortAvailable
    )
}
