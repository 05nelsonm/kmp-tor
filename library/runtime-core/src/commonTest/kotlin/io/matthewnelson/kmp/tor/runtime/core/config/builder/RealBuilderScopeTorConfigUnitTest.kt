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
package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.toSetting
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class RealBuilderScopeTorConfigUnitTest {

    @Suppress("PrivatePropertyName")
    private val TorConfig.BuilderScope.Real: RealBuilderScopeTorConfig get() = this as RealBuilderScopeTorConfig

    @Test
    fun givenContainsControlPort_whenPersistentOption_thenReturnsAsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsControlPort)
            ControlPort.configure {}
            assertTrue(Real.containsControlPort)
        }
    }

    @Test
    fun givenContainsControlPort_whenNonPersistentOption_thenReturnsAsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsControlPort)
            __ControlPort.configure {}
            assertTrue(Real.containsControlPort)
        }
    }

    @Test
    fun givenContainsSocksPort_whenPersistentOption_thenReturnsAsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsSocksPort)
            SocksPort.configure {}
            assertTrue(Real.containsSocksPort)
        }
    }

    @Test
    fun givenContainsSocksPort_whenNonPersistentOption_thenReturnsAsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsSocksPort)
            __SocksPort.configure {}
            assertTrue(Real.containsSocksPort)
        }
    }

    @Test
    fun givenContains_whenDormantCanceledByStartup_thenReturnsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsDormantCanceledByStartup)
            DormantCanceledByStartup.configure(true)
            assertTrue(Real.containsDormantCanceledByStartup)
        }
    }

    @Test
    fun givenContains_whenCacheDirectory_thenReturnsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsCacheDirectory)
            CacheDirectory.configure("".toFile())
            assertTrue(Real.containsCacheDirectory)
        }
    }

    @Test
    fun givenContains_whenControlPortWriteToFile_thenReturnsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsControlPortWriteToFile)
            ControlPortWriteToFile.configure("".toFile())
            assertTrue(Real.containsControlPortWriteToFile)
        }
    }

    @Test
    fun givenContains_whenCookieAuthFile_thenReturnsExpected() {
        TorConfig.Builder {
            assertFalse(Real.containsCookieAuthFile)
            CookieAuthFile.configure("".toFile())
            assertTrue(Real.containsCookieAuthFile)
        }
    }

    @Test
    fun givenContains_whenCookieAuthentication_thenReturnsExpected() {
        TorConfig.Builder {
            assertNull(Real.cookieAuthenticationOrNull)
            CookieAuthentication.configure(true)
            assertEquals(true, Real.cookieAuthenticationOrNull)
            CookieAuthentication.configure(false)
            assertEquals(false, Real.cookieAuthenticationOrNull)
        }
    }

    @Test
    fun givenScope_whenDataDirectoryOrNull_thenReturnsExpected() {
        TorConfig.Builder {
            assertNull(Real.dataDirectoryOrNull)
            DataDirectory.configure("".toFile())
            assertNotNull(Real.dataDirectoryOrNull)
        }
    }

    @Test
    fun givenScope_whenRemoveCookieAuthFile_thenRemovesIt() {
        TorConfig.Builder {
            CookieAuthFile.configure("".toFile())
            assertTrue(Real.containsCookieAuthFile)
            Real.removeCookieAuthFile()
            assertFalse(Real.containsCookieAuthFile)
        }
    }

    @Test
    fun givenPort_whenDistinctReassignable_thenIsReassigned() {
        val setting = SocksPort.asSetting {
            flagsSocks {
                OnionTrafficOnly = true
            }
        }

        val actual = setting.reassignAutoOrNull()
        assertNotNull(actual)
        assertNotEquals(setting, actual)
        assertEquals(setting.extras, actual.extras)
        assertEquals(setting.items.size, actual.items.size)

        val rootSetting = setting.items.first()
        val rootActual = actual.items.first()
        assertEquals(rootSetting.option, rootActual.option)
        assertEquals("9050", rootSetting.argument)
        assertEquals("auto", rootActual.argument)
        assertEquals(rootSetting.optionals, rootActual.optionals)
        assertTrue(rootSetting.optionals.isNotEmpty())
    }

    @Test
    fun givenPort_whenReassignableFalse_thenIsNotReassigned() {
        val setting = SocksPort.asSetting { reassignable(false) }
        assertNull(setting.reassignAutoOrNull())
    }

    @Test
    fun givenPort_whenAuto_thenIsNotReassigned() {
        val setting = SocksPort.asSetting { auto() }
        assertNull(setting.reassignAutoOrNull())
    }

    @Test
    fun givenPort_whenDisabled_thenIsNotReassigned() {
        val setting = SocksPort.asSetting { disable() }
        assertNull(setting.reassignAutoOrNull())
    }

    @Test
    fun givenHiddenServicePort_whenItIsArgument_thenIsNeverReassigned() {
        val setting = HiddenServicePort.toLineItem("80 8080").toSetting()
        assertTrue(setting.items.first().isPort)
        assertNull(setting.reassignAutoOrNull())
    }

    private fun TorSetting.reassignAutoOrNull(): TorSetting? {
        return RealBuilderScopeTorConfig.reassignTCPPortAutoOrNull(this)
    }
}
