/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.controller.common.config

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import kotlin.test.*

@Suppress("ClassName")
@OptIn(InternalTorApi::class)
class TorConfig_Setting_HiddenService_UnitTest {

    @Test
    fun givenHiddenService_whenSameDir_settingsAreEqual() {
        val path = Path("/some_dir")

        val (hs1, hs2) = Pair(HiddenService(), HiddenService())

        hs1.setPorts(setOf(HiddenService.Ports(Port(11))))
        hs2.setPorts(setOf(HiddenService.Ports(Port(22))))

        hs1.setMaxStreams(HiddenService.MaxStreams(1))
        hs2.setMaxStreams(HiddenService.MaxStreams(2))

        hs1.setMaxStreamsCloseCircuit(TorF.True)

        hs1.set(FileSystemDir(path))
        hs2.set(FileSystemDir(path))

        assertNotEquals(hs1.maxStreams, hs2.maxStreams)
        assertNotEquals(hs1.maxStreamsCloseCircuit, hs2.maxStreamsCloseCircuit)
        assertNotEquals(hs1.ports, hs2.ports)

        assertEquals(hs1, hs2)
    }

    @Test
    fun givenHiddenService_whenCloned_matchesOriginal() {
        val expectedHs = HiddenService()
        val expectedDir = FileSystemDir(Path("/some_dir"))
        val expectedPorts = setOf(HiddenService.Ports(Port(11)))
        val expectedMaxStreams = HiddenService.MaxStreams(1)
        val expectedMaxStreamsCloseCircuit = TorF.True

        expectedHs.set(expectedDir)
        expectedHs.setPorts(expectedPorts)
        expectedHs.setMaxStreams(expectedMaxStreams)
        expectedHs.setMaxStreamsCloseCircuit(expectedMaxStreamsCloseCircuit)

        val actual = expectedHs.clone()
        assertEquals(expectedDir, actual.value)
        assertEquals(expectedPorts, actual.ports)
        assertEquals(expectedMaxStreams, actual.maxStreams)
        assertEquals(expectedMaxStreamsCloseCircuit, actual.maxStreamsCloseCircuit)
    }

    @Test
    fun givenHiddenService_whenUnixSocketUnsupported_setPortsFiltersThemOut() {
        if (PlatformUtil.isDarwin || PlatformUtil.isLinux) return

        val hs = HiddenService()

        assertNull(hs.ports)
        hs.setPorts(setOf(
            HiddenService.Ports(Port(80)),
            HiddenService.UnixSocket(Port(80), Path("/some/unix/path"))
        ))

        assertEquals(1, hs.ports?.size)
    }

    @Test
    fun givenHiddenService_whenUnixSocketSupported_setPortsDoesNotFilterThemOut() {
        if (!(PlatformUtil.isDarwin || PlatformUtil.isLinux)) return

        val hs = HiddenService()

        assertNull(hs.ports)
        hs.setPorts(setOf(
            HiddenService.Ports(Port(80)),
            HiddenService.UnixSocket(Port(80), Path("/some/unix/path"))
        ))

        assertEquals(2, hs.ports?.size)
    }

    @Test
    fun givenHiddenService_whenUnixSocketPathNonUnixPath_setPortsFiltersThemOut() {
        if (!(PlatformUtil.isDarwin || PlatformUtil.isLinux)) return

        val hs = HiddenService()

        assertNull(hs.ports)
        hs.setPorts(setOf(
            HiddenService.Ports(Port(80)),
            HiddenService.UnixSocket(Port(80), Path("\\some\\non\\unix\\path"))
        ))

        assertEquals(1, hs.ports?.size)
    }
}
