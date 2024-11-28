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

// TODO: RE-IMPLEMENT
//import io.matthewnelson.kmp.file.path
//import io.matthewnelson.kmp.file.resolve
//import io.matthewnelson.kmp.tor.runtime.ConfigCallback
//import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
//import io.matthewnelson.kmp.tor.runtime.TorRuntime
//import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
//import io.matthewnelson.kmp.tor.runtime.core.net.Port
//import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
//import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
//import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
//import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.filterByOption
//import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
//import kotlinx.coroutines.test.runTest
//import kotlin.test.Test
//import kotlin.test.assertEquals
//import kotlin.test.assertNull
//import kotlin.test.assertTrue
//
//class TorConfigGeneratorUnitTest {
//
//    private val environment = testEnv("config_test")
//
//    private val notifier = object : RuntimeEvent.Notifier {
//        override fun <Data: Any, E: RuntimeEvent<Data>> notify(event: E, data: Data) {}
//    }
//
//    @Test
//    fun givenGeoipNoOmission_whenGenerate_thenContainsSettings() = runTest {
//        with(newGenerator().generate(notifier)) {
//            assertContains(TorOption.GeoIPFile)
//            assertContains(TorOption.GeoIPv6File)
//        }
//    }
//
//    @Test
//    fun givenMultipleUserConfigs_whenGenerate_thenAllAreApplied() = runTest {
//        var invocations = 0
//        newGenerator(
//            config = setOf(
//                ConfigCallback { _ -> invocations++ },
//                ConfigCallback { _ -> invocations++ },
//            )
//        ).generate(notifier)
//
//        assertEquals(2, invocations)
//    }
//
//    @Test
//    fun givenNoConfig_whenGenerate_thenMinimumSettingsApplied() = runTest {
//        with(newGenerator().generate(notifier)) {
//            assertContains(TorOption.DataDirectory)
//            assertContains(TorOption.CacheDirectory)
//            assertContains(TorOption.ControlPortWriteToFile)
//            assertContains(TorOption.CookieAuthFile)
//            assertContains(TorOption.__SocksPort)
//            assertContains(TorOption.__ControlPort)
//            assertContains(TorOption.DisableNetwork)
//            assertContains(TorOption.RunAsDaemon)
//            assertContains(TorOption.__OwningControllerProcess)
//            assertContains(TorOption.__ReloadTorrcOnSIGHUP)
//        }
//    }
//
//    @Test
//    fun givenUnavailablePort_whenGenerate_thenPortRemovedAndReplaced() = runTest {
//        // socks port at 9050 is automatically added
//        val config = newGenerator(
//            config = setOf(
//                ConfigCallback {
//                    TorOption.__DNSPort.configure { port(1080.toPortEphemeral()) }
//                }
//            ),
//            isPortAvailable = { _, _ -> false }
//        ).generate(notifier)
//
//        val socks = config.filterByOption<TorOption.__SocksPort>().first()
//        assertEquals("auto", socks.items.first().argument)
//        val dns = config.filterByOption<TorOption.__DNSPort>().first()
//        assertEquals("auto", dns.items.first().argument)
//    }
//
//    @Test
//    fun givenCookieAuthenticationEnabled_whenNoCookieAuthFile_thenAddsDefault() = runTest {
//        val config = newGenerator(
//            config = setOf(
//                ConfigCallback {
//                    TorOption.CookieAuthentication.configure(true)
//                }
//            )
//        ).generate(notifier)
//
//        config.assertContains(TorOption.CookieAuthFile)
//    }
//
//    @Test
//    fun givenCookieAuthenticationEnabled_whenCookieAuthFile_thenDoesNotModify() = runTest {
//        val expected = environment.workDirectory.resolve("data")
//            .resolve("control_auth_cookie_something")
//
//        val setting = newGenerator(
//            config = setOf(
//                ConfigCallback {
//                    TorOption.CookieAuthentication.configure(true)
//                    TorOption.CookieAuthFile.configure(expected)
//                }
//            )
//        ).generate(notifier)
//            .filterByOption<TorOption.CookieAuthFile>()
//            .first()
//
//        assertEquals(expected.path, setting.items.first().argument)
//    }
//
//    @Test
//    fun givenCookieAuthenticationDisabled_whenCookieAuthFile_thenRemoves() = runTest {
//        val expected = environment.workDirectory.resolve("data")
//            .resolve("control_auth_cookie_something")
//
//        val setting = newGenerator(
//            config = setOf(
//                ConfigCallback {
//                    TorOption.CookieAuthentication.configure(false)
//                    TorOption.CookieAuthFile.configure(expected)
//                }
//            )
//        ).generate(notifier)
//            .filterByOption<TorOption.CookieAuthFile>()
//            .firstOrNull()
//
//        assertNull(setting)
//    }
//
//    @Test
//    fun givenAuthentication_whenNothingDeclared_thenAddsCookieAuthenticationDefaults() = runTest {
//        val config = newGenerator().generate(notifier)
//
//        config.assertContains(TorOption.CookieAuthentication)
//        config.assertContains(TorOption.CookieAuthFile)
//    }
//
//    @Test
//    fun givenAuthCookieFile_whenNoCookieAuthentication_thenEnablesIt() = runTest {
//        val setting = newGenerator(
//            config = setOf(
//                ConfigCallback {
//                    TorOption.CookieAuthFile.configure(file =
//                        environment.workDirectory
//                            .resolve("data")
//                            .resolve("control_auth_cookie")
//                    )
//                }
//            )
//        ).generate(notifier)
//            .filterByOption<TorOption.CookieAuthentication>()
//            .first()
//            .items
//            .first()
//
//        assertEquals("1", setting.argument)
//        assertEquals(TorOption.CookieAuthentication, setting.option)
//    }
//
//    @Suppress("UNUSED_PARAMETER")
//    private inline fun <reified K: TorOption> TorConfig.assertContains(keyword: K) {
//        assertTrue(filterByOption<K>().isNotEmpty())
//    }
//
//    private fun newGenerator(
//        environment: TorRuntime.Environment = this.environment,
//        config: Set<ConfigCallback> = emptySet(),
//        isPortAvailable: suspend (LocalHost, Port) -> Boolean = { _, _ -> true },
//    ): TorConfigGenerator = TorConfigGenerator(
//        environment,
//        config,
//        isPortAvailable
//    )
//}
