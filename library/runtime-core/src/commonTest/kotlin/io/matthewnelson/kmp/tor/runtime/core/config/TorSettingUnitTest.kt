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
package io.matthewnelson.kmp.tor.runtime.core.config

import io.matthewnelson.kmp.file.absoluteFile
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.filterByAttribute
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.filterByOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.toSetting
import io.matthewnelson.kmp.tor.runtime.core.internal.UnixSocketsNotSupportedMessage
import kotlin.test.*

@OptIn(ExperimentalKmpTorApi::class)
class TorSettingUnitTest {

    @Test
    fun givenEmptyItems_whenToSetting_thenThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            emptySet<TorSetting.LineItem>().toSetting()
        }
    }

    @Test
    fun givenFilterBy_whenNestedInGroupSetting_thenReturnsSetting() {
        val config = TorConfig2.Builder {
            DataDirectory.configure("".toFile().absoluteFile.resolve("data"))
            CacheDirectory.configure("".toFile().absoluteFile.resolve("cache"))
            HiddenServiceDir.tryConfigure {
                directory("".toFile().absoluteFile.resolve("hs_dir"))
                version(3)
                port(virtual = Port.HTTP) {
                    target(port = 8080.toPort())
                }
            }
        }

        assertEquals(3, config.settings.size)

        config.filterByOption<HiddenServiceVersion>().let { list ->
            assertEquals(1, list.size)
            val item = list.first().items.elementAt(1)
            assertIs<HiddenServiceVersion>(item.option)
            assertEquals("3", item.argument)
        }

        config.filterByAttribute<Attribute.PORT>().let { list ->
            assertEquals(1, list.size)
            val item = list.first().items.elementAt(2)
            assertIs<HiddenServicePort>(item.option)
            assertEquals("80 8080", item.argument)
        }
    }

    @Test
    fun givenFilterByAttribute_whenAttributeUNIXSOCKET_thenReturnsOnlyThoseConfigured() {
        if (UnixSocketsNotSupportedMessage != null) {
            println("Skipping...")
            return
        }

        val expected = "/some/path/ctrl.sock".toFile()
        val settings = TorConfig2.Builder {
            ControlPort.configure {
                unixSocket(value = expected)
            }
            ControlPort.configure { auto() }
        }.settings

        assertEquals(2, settings.size)

        val list = settings.filterByAttribute<Attribute.UNIX_SOCKET>()
        assertEquals(1, list.size)
        assertTrue(list.first().items.first().argument.contains(expected.path))
    }

    @Test
    fun givenFilterByAttribute_whenAttributePORT_thenReturnsOnlyThoseConfigured() {
        if (UnixSocketsNotSupportedMessage != null) {
            println("Skipping...")
            return
        }

        val expected = "/some/path/ctrl.sock".toFile()
        val settings = TorConfig2.Builder {
            ControlPort.configure {
                unixSocket(value = expected)
            }
            ControlPort.configure { auto() }
        }.settings

        assertEquals(2, settings.size)

        val list = settings.filterByAttribute<Attribute.PORT>()
        assertEquals(1, list.size)
        assertEquals("auto", list.first().items.first().argument)
    }

    @Test
    fun givenFilterBy_whenMultipleOfSame_thenReturnsExpected() {
        val settings = TorConfig2.Builder {
            CacheDirectory.configure("/path/to/cache".toFile())

            ControlPort.configure { port(9055.toPortEphemeral()) }
            ConnectionPadding.configure { disable() }
            DataDirectory.configure("/path/to/data".toFile())

            GeoIPFile.configure("/path/to/geoip".toFile())
            ControlPort.configure { port(9057.toPortEphemeral()) }
            GeoIPv6File.configure("/path/to/geoip6".toFile())

            ControlPort.configure { port(9056.toPortEphemeral()) }
        }.settings

        assertEquals(8, settings.size)

        settings.filterByAttribute<Attribute.DIRECTORY>().let { list ->
            assertEquals(2, list.size)
            assertIs<CacheDirectory>(list.first().items.first().option)
            assertIs<DataDirectory>(list.last().items.last().option)
        }

        settings.filterByAttribute<Attribute.FILE>().let { list ->
            assertEquals(2, list.size)
            assertIs<GeoIPFile>(list.first().items.first().option)
            assertIs<GeoIPv6File>(list.last().items.last().option)
        }

        assertEquals(3, settings.filterByOption<ControlPort>().size)
    }
}
