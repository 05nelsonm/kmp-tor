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
package io.matthewnelson.kmp.tor.runtime.core.builder

import io.matthewnelson.kmp.file.absoluteFile
import io.matthewnelson.kmp.file.normalize
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.internal.toByte
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class ExtendedTorConfigUnitTest {

    @Test
    fun givenExtendedConfig_whenCastAs_thenWorksAsExpected() {
        val dns = TorConfig.__DNSPort.Builder { port(1080.toPortEphemeral()) }

        TorConfig.Builder {

            put(dns)
            put(TorConfig.__SocksPort) { asPort { disable() } }
            put(TorConfig.HiddenServiceDir) {
                directory = "".toFile()
                version { HSv(3) }
                port {
                    virtual = 80.toPort()
                    targetAsPort { target = 443.toPort() }
                }
            }

            // contains
            assertTrue((this as ExtendedTorConfigBuilder).contains(TorConfig.__SocksPort))
            assertTrue(contains(TorConfig.HiddenServiceMaxStreams))
            assertFalse(contains(TorConfig.__ControlPort))

            // remove
            assertTrue(contains(dns.keyword))
            remove(dns)
            assertFalse(contains(dns.keyword))
        }
    }

    @Test
    fun givenBuilder_whenDataDir_thenReturnsSetting() {
        TorConfig.Builder {
            var dir = (this as ExtendedTorConfigBuilder).dataDirectory()

            assertNull(dir)

            put(TorConfig.DataDirectory) {
                directory = "".toFile()
            }

            dir = dataDirectory()
            assertNotNull(dir)
            assertEquals(TorConfig.DataDirectory.Companion, dir.keyword)
            assertEquals("".toFile().absoluteFile.normalize(), dir.argument.toFile())

            var auth = cookieAuthentication()
            assertNull(auth)

            put(TorConfig.CookieAuthentication) {
                enable = true
            }

            auth = cookieAuthentication()
            assertNotNull(auth)
            assertEquals(TorConfig.CookieAuthentication.Companion, auth.keyword)
            assertEquals(true.toByte().toString(), auth.argument)
        }
    }

    @Test
    fun givenBuilder_whenCookieAuthentication_thenReturnsSetting() {
        TorConfig.Builder {
            var auth = (this as ExtendedTorConfigBuilder).cookieAuthentication()
            assertNull(auth)

            put(TorConfig.CookieAuthentication) {
                enable = true
            }

            auth = cookieAuthentication()
            assertNotNull(auth)
            assertEquals(TorConfig.CookieAuthentication.Companion, auth.keyword)
            assertEquals(true.toByte().toString(), auth.argument)
        }
    }
}
