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
import io.matthewnelson.kmp.tor.runtime.ConfigCallback
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.filterByOption
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
        assertEquals(0, settings.filterByOption<TorOption.GeoIPFile>().size)
        assertEquals(0, settings.filterByOption<TorOption.GeoIPv6File>().size)
    }

    @Test
    fun givenGeoipNoOmission_whenGenerate_thenContainsSettings() = runTest {
        with(newGenerator().generate(notifier).first) {
            assertContains(TorOption.GeoIPFile)
            assertContains(TorOption.GeoIPv6File)
        }
    }

    @Test
    fun givenMultipleUserConfigs_whenGenerate_thenAllAreApplied() = runTest {
        var invocations = 0
        newGenerator(
            config = setOf(
                ConfigCallback { _ -> invocations++ },
                ConfigCallback { _ -> invocations++ },
            )
        ).generate(notifier)

        assertEquals(2, invocations)
    }

    @Test
    fun givenNoConfig_whenGenerate_thenMinimumSettingsApplied() = runTest {
        with(newGenerator().generate(notifier).first) {
            assertContains(TorOption.DataDirectory)
            assertContains(TorOption.CacheDirectory)
            assertContains(TorOption.ControlPortWriteToFile)
            assertContains(TorOption.CookieAuthFile)
            assertContains(TorOption.__SocksPort)
            assertContains(TorOption.__ControlPort)
            assertContains(TorOption.DisableNetwork)
            assertContains(TorOption.RunAsDaemon)
            assertContains(TorOption.__OwningControllerProcess)
            assertContains(TorOption.__ReloadTorrcOnSIGHUP)
        }
    }

    @Test
    fun givenUnavailablePort_whenGenerate_thenPortRemovedAndReplaced() = runTest {
        // socks port at 9050 is automatically added
        val config = newGenerator(
            config = setOf(
                ConfigCallback {
                    TorOption.__DNSPort.configure { port(1080.toPortEphemeral()) }
                }
            ),
            isPortAvailable = { _, _ -> false }
        ).generate(notifier).first

        val socks = config.filterByOption<TorOption.__SocksPort>().first()
        assertEquals("auto", socks.items.first().argument)
        val dns = config.filterByOption<TorOption.__DNSPort>().first()
        assertEquals("auto", dns.items.first().argument)
    }

    @Test
    fun givenCookieAuthenticationEnabled_whenNoCookieAuthFile_thenAddsDefault() = runTest {
        val config = newGenerator(
            config = setOf(
                ConfigCallback {
                    TorOption.CookieAuthentication.configure(true)
                }
            )
        ).generate(notifier).first

        config.assertContains(TorOption.CookieAuthFile)
    }

    @Test
    fun givenCookieAuthenticationEnabled_whenCookieAuthFile_thenDoesNotModify() = runTest {
        val expected = environment.workDirectory.resolve("data")
            .resolve("control_auth_cookie_something")

        val setting = newGenerator(
            config = setOf(
                ConfigCallback {
                    TorOption.CookieAuthentication.configure(true)
                    TorOption.CookieAuthFile.configure(expected)
                }
            )
        ).generate(notifier)
            .first
            .filterByOption<TorOption.CookieAuthFile>()
            .first()

        assertEquals(expected.path, setting.items.first().argument)
    }

    @Test
    fun givenCookieAuthenticationDisabled_whenCookieAuthFile_thenRemoves() = runTest {
        val expected = environment.workDirectory.resolve("data")
            .resolve("control_auth_cookie_something")

        val setting = newGenerator(
            config = setOf(
                ConfigCallback {
                    TorOption.CookieAuthentication.configure(false)
                    TorOption.CookieAuthFile.configure(expected)
                }
            )
        ).generate(notifier)
            .first
            .filterByOption<TorOption.CookieAuthFile>()
            .firstOrNull()

        assertNull(setting)
    }

    @Test
    fun givenAuthentication_whenNothingDeclared_thenAddsCookieAuthenticationDefaults() = runTest {
        val config = newGenerator().generate(notifier).first

        config.assertContains(TorOption.CookieAuthentication)
        config.assertContains(TorOption.CookieAuthFile)
    }

    @Test
    fun givenAuthCookieFile_whenNoCookieAuthentication_thenEnablesIt() = runTest {
        val setting = newGenerator(
            config = setOf(
                ConfigCallback {
                    TorOption.CookieAuthFile.configure(file =
                        environment.workDirectory
                            .resolve("data")
                            .resolve("control_auth_cookie")
                    )
                }
            )
        ).generate(notifier)
            .first
            .filterByOption<TorOption.CookieAuthentication>()
            .first()
            .items
            .first()

        assertEquals("1", setting.argument)
        assertEquals(TorOption.CookieAuthentication, setting.option)
    }

    @Suppress("UNUSED_PARAMETER")
    private inline fun <reified K: TorOption> TorConfig.assertContains(keyword: K) {
        assertTrue(filterByOption<K>().isNotEmpty())
    }

    private fun newGenerator(
        environment: TorRuntime.Environment = this.environment,
        config: Set<ConfigCallback> = emptySet(),
        isPortAvailable: suspend (LocalHost, Port) -> Boolean = { _, _ -> true },
    ): TorConfigGenerator = TorConfigGenerator(
        environment,
        config,
        isPortAvailable
    )
}
