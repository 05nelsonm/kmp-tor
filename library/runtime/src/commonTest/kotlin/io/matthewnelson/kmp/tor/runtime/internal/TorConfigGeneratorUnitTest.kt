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

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.runtime.ConfigBuilderCallback
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TorConfigGeneratorUnitTest {

    private fun installer(installationDir: File) = object : ResourceInstaller<Paths.Tor>(installationDir) {
        private val paths = Paths.Tor(
            geoip = installationDir.resolve("geoip"),
            geoip6 = installationDir.resolve("geoip6"),
            tor = installationDir.resolve("tor")
        )

        override fun install(): Paths.Tor = paths
    }

    private val environment = testEnv("config_test", ::installer)

    private val notifier = object : RuntimeEvent.Notifier {
        override fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data) {}
    }

    @Test
    fun givenGeoipOmission_whenGenerate_thenDoesNotContainSettings() = runTest {
        val environment = testEnv("config_test_omit_geoip", ::installer) { omitGeoIPFileSettings = true }
        val settings = newGenerator(environment).generate(notifier).first.settings
        assertEquals(0, settings.filterByKeyword<TorConfig.GeoIPFile.Companion>().size)
        assertEquals(0, settings.filterByKeyword<TorConfig.GeoIPv6File.Companion>().size)
    }

    @Test
    fun givenGeoipNoOmission_whenGenerate_thenContainsSettings() = runTest {
        with(newGenerator().generate(notifier).first) {
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
        with(newGenerator().generate(notifier).first) {
            assertContains(TorConfig.DataDirectory)
            assertContains(TorConfig.CacheDirectory)
            assertContains(TorConfig.ControlPortWriteToFile)
            assertContains(TorConfig.CookieAuthFile)
            assertContains(TorConfig.__SocksPort)
            assertContains(TorConfig.__ControlPort)
            assertContains(TorConfig.DisableNetwork)
            assertContains(TorConfig.RunAsDaemon)
            assertContains(TorConfig.__OwningControllerProcess)
            assertContains(TorConfig.__ReloadTorrcOnSIGHUP)
        }
    }

    @Test
    fun givenUnavailablePort_whenGenerate_thenPortRemovedAndReplaced() = runTest {
        // socks port at 9050 is automatically added
        val settings = newGenerator(
            config = setOf(
                ConfigBuilderCallback {
                    put(TorConfig.__DNSPort) { port(1080.toPortEphemeral()) }
                }
            ),
            isPortAvailable = { _, _ -> false }
        ).generate(notifier).first.settings

        val socks = settings.filterByKeyword<TorConfig.__SocksPort.Companion>().first()
        assertEquals("auto", socks.argument)
        val dns = settings.filterByKeyword<TorConfig.__DNSPort.Companion>().first()
        assertEquals("auto", dns.argument)
    }

    @Test
    fun givenCookieAuthenticationEnabled_whenNoCookieAuthFile_thenAddsDefault() = runTest {
        val config = newGenerator(
            config = setOf(
                ConfigBuilderCallback {
                    put(TorConfig.CookieAuthentication) { enable = true }
                }
            )
        ).generate(notifier).first

        config.assertContains(TorConfig.CookieAuthFile)
    }

    @Test
    fun givenCookieAuthenticationEnabled_whenCookieAuthFile_thenDoesNotModify() = runTest {
        val expected = environment.workDirectory.resolve("data")
            .resolve(TorConfig.CookieAuthFile.DEFAULT_NAME + "_something")

        val setting = newGenerator(
            config = setOf(
                ConfigBuilderCallback {
                    put(TorConfig.CookieAuthentication) { enable = true }
                    put(TorConfig.CookieAuthFile) {
                        file = expected
                    }
                }
            )
        ).generate(notifier)
            .first
            .settings
            .filterByKeyword<TorConfig.CookieAuthFile.Companion>()
            .first()

        assertEquals(expected.path, setting.argument)
    }

    @Test
    fun givenCookieAuthenticationDisabled_whenCookieAuthFile_thenRemoves() = runTest {
        val expected = environment.workDirectory.resolve("data")
            .resolve(TorConfig.CookieAuthFile.DEFAULT_NAME + "_something")

        val setting = newGenerator(
            config = setOf(
                ConfigBuilderCallback {
                    put(TorConfig.CookieAuthentication) { enable = false }
                    put(TorConfig.CookieAuthFile) {
                        file = expected
                    }
                }
            )
        ).generate(notifier)
            .first
            .settings
            .filterByKeyword<TorConfig.CookieAuthFile.Companion>()
            .firstOrNull()

        assertNull(setting)
    }

    @Test
    fun givenAuthentication_whenNothingDeclared_thenAddsCookieAuthenticationDefaults() = runTest {
        val config = newGenerator().generate(notifier).first

        config.assertContains(TorConfig.CookieAuthentication)
        config.assertContains(TorConfig.CookieAuthFile)
    }

    @Test
    fun givenAuthCookieFile_whenNoCookieAuthentication_thenEnablesIt() = runTest {
        val setting = newGenerator(
            config = setOf(
                ConfigBuilderCallback {
                    put(TorConfig.CookieAuthFile) {
                        file = environment.workDirectory
                            .resolve("data")
                            .resolve(TorConfig.CookieAuthFile.DEFAULT_NAME)
                    }
                }
            )
        ).generate(notifier)
            .first
            .settings
            .filterByKeyword<TorConfig.CookieAuthentication.Companion>()
            .first()

        assertEquals("1", setting.argument)
        assertEquals(TorConfig.CookieAuthentication.Companion, setting.keyword)
    }

    @Suppress("UNUSED_PARAMETER")
    private inline fun <reified K: TorConfig.Keyword> TorConfig.assertContains(keyword: K) {
        assertTrue(filterByKeyword<K>().isNotEmpty())
    }

    private fun newGenerator(
        environment: TorRuntime.Environment = this.environment,
        config: Set<ConfigBuilderCallback> = emptySet(),
        isPortAvailable: suspend (LocalHost, Port) -> Boolean = { _, _ -> true },
    ): TorConfigGenerator = TorConfigGenerator(
        environment,
        config,
        isPortAvailable
    )
}
