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

import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.Companion.AUTO
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItemOrNull
import kotlin.test.*

@OptIn(ExperimentalKmpTorApi::class)
class LineItemUnitTest {

    @Test
    fun givenImproperArgument_whenToLineItemOrNull_thenReturnsNull() {
        assertNotNull(ControlPort.toLineItemOrNull(AUTO))
        assertNull(ControlPort.toLineItemOrNull(""))
        assertNull(ControlPort.toLineItemOrNull("   "))
        assertNull(ControlPort.toLineItemOrNull("1020\n5"))
    }

    @Test
    fun givenImproperExtras_whenToLineItemOrNull_thenReturnsNull() {
        assertNotNull(ControlPort.toLineItemOrNull(AUTO, mutableSetOf("extra")))
        assertNull(ControlPort.toLineItemOrNull(AUTO, mutableSetOf("")))
        assertNull(ControlPort.toLineItemOrNull(AUTO, mutableSetOf("  ")))
        assertNull(ControlPort.toLineItemOrNull(AUTO, mutableSetOf("e\n")))
        assertNull(ControlPort.toLineItemOrNull(AUTO, mutableSetOf("extra", "")))
        assertNull(ControlPort.toLineItemOrNull(AUTO, mutableSetOf("extra", "  ")))
        assertNull(ControlPort.toLineItemOrNull(AUTO, mutableSetOf("extra", "e\n")))
    }

    @Test
    fun givenNonPersistentOption_whenIsNonPersistent_thenIsTrue() {
        assertTrue(__TransPort.toLineItem(AUTO).isNonPersistent)
        assertFalse(TransPort.toLineItem(AUTO).isNonPersistent)
    }

    @Test
    fun givenOptionAttributeUNIXSOCKET_whenConfiguredAsSuch_thenIsUnixSocketIsTrue() {
        val path = "unix:\"/path/to/uds.sock\""

        listOf(
            HiddenServicePort.toLineItem("80 $path"),
            ControlSocket.toLineItem(Port.ZERO.toString()),
            ControlSocket.toLineItem(path),
            ControlPort.toLineItem(path),
            SocksPort.toLineItem(path),
        ).forEach { item ->
            assertTrue(item.isUnixSocket)

            assertFalse(item.isPort)
            assertFalse(item.isPortAuto)
            assertFalse(item.isPortDisabled)
            assertFalse(item.isPortDistinct)
        }
    }

    @Test
    fun givenOptionAttributePORT_whenConfiguredWithDistinctPort_thenIsPortDistinctIsTrue() {
        val port = 1024.toPortEphemeral().toString()

        listOf(
            HiddenServicePort.toLineItem("80 $port"),
            ControlPort.toLineItem(port),
            SocksPort.toLineItem(port),
        ).forEach { item ->
            assertTrue(item.isPort)
            assertTrue(item.isPortDistinct)

            assertFalse(item.isUnixSocket)
            assertFalse(item.isPortAuto)
            assertFalse(item.isPortDisabled)
            assertFalse(item.isFile)
            assertFalse(item.isDirectory)
        }
    }

    @Test
    fun givenOptionAttributePORT_whenConfiguredWithAuto_thenIsPortAutoIsTrue() {
        listOf(
            ControlPort.toLineItem(AUTO),
            SocksPort.toLineItem(AUTO),
        ).forEach { item ->
            assertTrue(item.isPort)
            assertTrue(item.isPortAuto)

            assertFalse(item.isUnixSocket)
            assertFalse(item.isPortDisabled)
            assertFalse(item.isPortDistinct)
            assertFalse(item.isFile)
            assertFalse(item.isDirectory)
        }
    }

    @Test
    fun givenOptionAttributePORT_whenConfiguredWithDisabled_thenIsPortDisabledIsTrue() {
        listOf(
            ControlPort.toLineItem(Port.ZERO.toString()),
            SocksPort.toLineItem(Port.ZERO.toString()),
        ).forEach { item ->
            assertTrue(item.isPort)
            assertTrue(item.isPortDisabled)

            assertFalse(item.isUnixSocket)
            assertFalse(item.isPortAuto)
            assertFalse(item.isPortDistinct)
            assertFalse(item.isFile)
            assertFalse(item.isDirectory)
        }
    }

    @Test
    fun givenOptionAttributeFILE_whenConfigured_thenIsFileIsTrue() {
        val item = GeoIPFile.toLineItem("/some/path")
        assertTrue(item.isFile)

        assertFalse(item.isUnixSocket)
        assertFalse(item.isPort)
        assertFalse(item.isPortAuto)
        assertFalse(item.isPortDisabled)
        assertFalse(item.isPortDistinct)
        assertFalse(item.isDirectory)
    }

    @Test
    fun givenOptionAttributeDIRECTORY_whenConfigured_thenIsDirectoryIsTrue() {
        val item = CacheDirectory.toLineItem("/some/path")
        assertTrue(item.isDirectory)

        assertFalse(item.isUnixSocket)
        assertFalse(item.isPort)
        assertFalse(item.isPortAuto)
        assertFalse(item.isPortDisabled)
        assertFalse(item.isPortDistinct)
        assertFalse(item.isFile)
    }

    @Test
    fun givenOptionAttributeHIDDENSERVICE_whenConfigured_thenIsHiddenServiceIsTrue() {
        val item = HiddenServiceDir.toLineItem("/some/path")
        assertTrue(item.isHiddenService)
        assertTrue(item.isDirectory)

        assertFalse(item.isUnixSocket)
        assertFalse(item.isPort)
        assertFalse(item.isPortAuto)
        assertFalse(item.isPortDisabled)
        assertFalse(item.isPortDistinct)
        assertFalse(item.isFile)
    }
}
