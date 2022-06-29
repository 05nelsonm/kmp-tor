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

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.controller.common.internal.isUnixPath
import kotlin.test.*

@Suppress("ClassName")
@OptIn(InternalTorApi::class)
class TorConfig_Setting_UnixSockets_UnitTest {

    private val unixPath = Path("/some/unix/path").also { assertTrue(it.isUnixPath) }

    @Test
    fun givenUnixSocketControl_whenUnsupported_unableToSet() {
        if (PlatformUtil.hasControlUnixDomainSocketSupport) return

        val control = UnixSockets.Control()
        control.set(FileSystemFile(unixPath))

        assertNull(control.value)
    }

    @Test
    fun givenUnixSocketControl_whenSupported_ableToSet() {
        if (!PlatformUtil.hasControlUnixDomainSocketSupport) return

        val control = UnixSockets.Control()
        control.set(FileSystemFile(unixPath))

        assertEquals(unixPath, control.value?.path)
    }

    @Test
    fun givenUnixSocketSocks_whenUnsupported_unableToSet() {
        if (PlatformUtil.isLinux) return

        val socks = UnixSockets.Socks()
        socks.set(FileSystemFile(unixPath))

        assertNull(socks.value)
    }

    @Test
    fun givenUnixSocketSocks_whenSupported_ableToSet() {
        if (!PlatformUtil.isLinux) return

        val socks = UnixSockets.Socks()
        socks.set(FileSystemFile(unixPath))

        assertEquals(unixPath, socks.value?.path)
    }

    @Test
    fun givenUnixSocket_whenPathsSame_equalsEachOther() {
        if (!PlatformUtil.hasControlUnixDomainSocketSupport) return

        val control = UnixSockets.Control()
        control.set(FileSystemFile(unixPath))

        val socks = UnixSockets.Socks()
        socks.set(FileSystemFile(unixPath))
        socks.setUnixFlags(setOf(
            UnixSockets.Flag.GroupWritable
        ))

        assertTrue(control.equals(socks))

        val set = mutableSetOf<UnixSockets>()
        set.add(control)
        set.add(socks)

        assertEquals(1, set.size)
        assertTrue(set.first() is UnixSockets.Control)
    }

    @Test
    fun givenUnixSocketControl_whenPathDoseNotStartWithUnixFileSeparator_valueNotSet() {
        if (!PlatformUtil.hasControlUnixDomainSocketSupport) return

        val control = UnixSockets.Control()
        control.set(FileSystemFile(Path("0")))
        assertNull(control.value)

        control.set(FileSystemFile(Path("9051")))
        assertNull(control.value)

        control.set(FileSystemFile(Path("")))
        assertNull(control.value)
    }

    @Test
    fun givenUnixSocketControl_whenCloned_matchesOriginal() {
        if (!PlatformUtil.hasControlUnixDomainSocketSupport) return

        val control = UnixSockets.Control()
        control.set(FileSystemFile(unixPath))
        control.setUnixFlags(setOf(
            UnixSockets.Control.Flag.WorldWritable
        ))

        val clone = control.clone()

        assertNotNull(control.value)
        assertEquals(control.value, clone.value)

        assertNotNull(control.unixFlags)
        assertEquals(control.unixFlags, clone.unixFlags)
    }


    @Test
    fun givenUnixSocketSocks_whenPathDoseNotStartWithUnixFileSeparator_valueNotSet() {
        if (!PlatformUtil.hasControlUnixDomainSocketSupport) return

        val socks = UnixSockets.Socks()
        socks.set(FileSystemFile(Path("0")))
        assertNull(socks.value)

        socks.set(FileSystemFile(Path("9051")))
        assertNull(socks.value)

        socks.set(FileSystemFile(Path("")))
        assertNull(socks.value)
    }

    @Test
    fun givenUnixSocketSocks_whenCloned_matchesOriginal() {
        if (!PlatformUtil.hasControlUnixDomainSocketSupport) return

        val socks = UnixSockets.Socks()
        socks.set(FileSystemFile(unixPath))
        socks.setFlags(setOf(
            Ports.Socks.Flag.OnionTrafficOnly
        ))
        socks.setUnixFlags(setOf(
            UnixSockets.Control.Flag.WorldWritable
        ))
        socks.setIsolationFlags(setOf(
            Ports.IsolationFlag.IsolateSOCKSAuth
        ))

        val clone = socks.clone()

        assertNotNull(socks.value)
        assertEquals(socks.value, clone.value)

        assertNotNull(socks.flags)
        assertEquals(socks.flags, clone.flags)

        assertNotNull(socks.unixFlags)
        assertEquals(socks.unixFlags, clone.unixFlags)

        assertNotNull(socks.isolationFlags)
        assertEquals(socks.isolationFlags, clone.isolationFlags)
    }
}
