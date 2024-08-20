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

import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption.*
import io.matthewnelson.kmp.tor.runtime.core.internal.IsUnixLikeHost
import io.matthewnelson.kmp.tor.runtime.core.internal.UnixSocketsNotSupportedMessage
import kotlin.test.*

/**
 * All builder functionality on an individual port type
 * level is all inherited from the abstraction, so only
 * need to exercise those APIs and will ensure all
 * types that implement it are good.
 * */
class BuilderScopePortUnitTest {

    // must start with `/` so is absolute on unix host
    // which is all the Unix Socket tests run on.
    private val udsPath = "/some/path/s.sock".toFile()

    @Test
    fun givenControl_whenNonPersistent_thenUsesNonPersistentOption() {
        val setting = __ControlPort.asSetting {}
        assertTrue(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenControl_whenPersistent_thenUsesPersistentOption() {
        val setting = ControlPort.asSetting {}
        assertFalse(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenDNS_whenNonPersistent_thenUsesNonPersistentOption() {
        val setting = __DNSPort.asSetting {}
        assertTrue(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenDNS_whenPersistent_thenUsesPersistentOption() {
        val setting = DNSPort.asSetting {}
        assertFalse(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenHTTPTunnel_whenNonPersistent_thenUsesNonPersistentOption() {
        val setting = __HTTPTunnelPort.asSetting {}
        assertTrue(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenHTTPTunnel_whenPersistent_thenUsesPersistentOption() {
        val setting = HTTPTunnelPort.asSetting {}
        assertFalse(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenSocks_whenNonPersistent_thenUsesNonPersistentOption() {
        val setting = __SocksPort.asSetting {}
        assertTrue(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenSocks_whenPersistent_thenUsesPersistentOption() {
        val setting = SocksPort.asSetting {}
        assertFalse(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenTrans_whenNonPersistent_thenUsesNonPersistentOption() = runOnCondition(
        condition = IsUnixLikeHost,
    ) {
        val setting = __TransPort.asSetting {}
        assertTrue(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenTrans_whenPersistent_thenUsesPersistentOption() = runOnCondition(
        condition = IsUnixLikeHost,
    ) {
        val setting = TransPort.asSetting {}
        assertFalse(setting.items.first().isNonPersistent)
    }

    @Test
    fun givenPort_whenAuto_thenArgumentIsAuto() {
        val setting = ControlPort.asSetting { auto() }
        assertEquals("auto", setting.items.first().argument)
    }

    @Test
    fun givenPort_whenDistinct_thenArgumentIsDistinct() {
        val expected = 9055.toPortEphemeral()
        val setting = ControlPort.asSetting { port(expected) }
        assertEquals(expected.toString(), setting.items.first().argument)
    }

    @Test
    fun givenPort_whenDisabled_thenArgumentIsDisabled() {
        val default = SocksPort.asSetting {}
        // Assert the default setting for SocksPort is NOT
        // zero (disabled) which we are testing for.
        assertEquals("9050", default.items.first().argument)

        val setting = SocksPort.asSetting { disable() }
        assertEquals("0", setting.items.first().argument)
    }

    @Test
    fun givenPort_whenInitialized_thenReassignableDefaultsToTrue() {
        val setting = ControlPort.asSetting {}
        val actual = setting.extras[BuilderScopePort.EXTRA_REASSIGNABLE]
        assertIs<Boolean>(actual)
        assertTrue(actual)
    }

    @Test
    fun givenPort_whenReassignableDisallowed_thenIsChangedToFalse() {
        val setting = ControlPort.asSetting { reassignable(allow = false) }
        val actual = setting.extras[BuilderScopePort.EXTRA_REASSIGNABLE]
        assertIs<Boolean>(actual)
        assertFalse(actual)
    }

    @Test
    fun givenPort_whenUnixSocket_thenArgumentIsFormattedProperly() = runOnCondition(
        condition = UnixSocketsNotSupportedMessage == null,
    ) {
        val item = ControlPort.asSetting { unixSocket(udsPath) }.items.first()

        // Prefixed with `unix:"` (quote begin)
        assertTrue(item.argument.startsWith("unix:\""))
        // Close quoted
        assertTrue(item.argument.endsWith('\"'))
        // contains our "path"
        assertTrue(item.argument.contains(udsPath.path))
    }

    @Test
    fun givenPort_whenUnixSocket_thenUnixFlagsAdded() = runOnCondition(
        condition = UnixSocketsNotSupportedMessage == null,
    ) {
        val item = ControlPort.asSetting {
            unixSocket(udsPath)
            flagsUnix {
                GroupWritable = true
                WorldWritable = true
                RelaxDirModeCheck = true
            }
        }.items.first()

        assertEquals(3, item.optionals.size)
        assertTrue(item.optionals.contains("GroupWritable"))
        assertTrue(item.optionals.contains("WorldWritable"))
        // Should be added b/c this is Control
        assertTrue(item.optionals.contains("RelaxDirModeCheck"))
    }

    @Test
    fun givenPort_whenNotUnixSocket_thenUnixFlagsNotAdded() {
        val item = ControlPort.asSetting {
            auto()

            flagsUnix {
                GroupWritable = true
                WorldWritable = true
                RelaxDirModeCheck = true
            }
        }.items.first()

        assertTrue(item.optionals.isEmpty())
    }

    @Test
    fun givenSocksPort_whenSocksFlags_thenFlagsAdded() {
        val item = SocksPort.asSetting {
            flagsSocks {
                NoIPv4Traffic = true
            }
        }.items.first()

        assertEquals(1, item.optionals.size)
        assertEquals("NoIPv4Traffic", item.optionals.first())
    }

    @Test
    fun givenPort_whenIsolationFlags_thenFlagsAdded() {
        val item = DNSPort.asSetting {
            flagsIsolation {
                IsolateDestPort = true
            }
        }.items.first()

        assertEquals(1, item.optionals.size)
        assertEquals("IsolateDestPort", item.optionals.first())
    }

    @Test
    fun givenPort_whenAllFlags_thenAllAdded() = runOnCondition(
        condition = UnixSocketsNotSupportedMessage == null,
    ) {
        val item = SocksPort.asSetting {
            unixSocket("".toFile().resolve("s.sock"))
            flagsIsolation {
                IsolateClientAddr = true
                IsolateSOCKSAuth = true
                IsolateClientProtocol = true
                IsolateDestPort = true
                IsolateDestAddr = true
                KeepAliveIsolateSOCKSAuth = true
                SessionGroup(1)
            }
            flagsSocks {
                NoIPv4Traffic = true
                IPv6Traffic = true
                PreferIPv6 = true
                NoDNSRequest = true
                NoOnionTraffic = true
                OnionTrafficOnly = true
                CacheIPv4DNS = true
                CacheIPv6DNS = true
                CacheDNS = true
                UseIPv4Cache = true
                UseIPv6Cache = true
                UseDNSCache = true
                PreferIPv6Automap = true
                PreferSOCKSNoAuth = true
            }
            flagsUnix {
                GroupWritable = true
                WorldWritable = true

                // Won't be added, but w/e
                RelaxDirModeCheck = true
            }
        }.items.first()

        assertEquals(23, item.optionals.size)
    }

    @Test
    fun givenFlagBuilder_whenIsolation_thenAddsAndRemovesCorrectly() {
        val flags = LinkedHashSet<String>(1, 1.0f)

        fun configure(
            block: ThisBlock<BuilderScopePort.FlagsBuilderIsolation>,
        ) {
            BuilderScopePort.FlagsBuilderIsolation.configure(flags, block)
        }

        configure {
            IsolateClientAddr = true
            IsolateSOCKSAuth = true
            IsolateClientProtocol = true
            IsolateDestPort = true
            IsolateDestAddr = true
            KeepAliveIsolateSOCKSAuth = true
            SessionGroup(1)
        }

        assertEquals(7, flags.size)
        assertTrue(flags.contains("IsolateClientAddr"))
        assertTrue(flags.contains("IsolateSOCKSAuth"))
        assertTrue(flags.contains("IsolateClientProtocol"))
        assertTrue(flags.contains("IsolateDestPort"))
        assertTrue(flags.contains("IsolateDestAddr"))
        assertTrue(flags.contains("KeepAliveIsolateSOCKSAuth"))
        assertTrue(flags.contains("SessionGroup=1"))

        configure {}
        assertEquals(7, flags.size)

        configure {
            IsolateClientAddr = false
            IsolateSOCKSAuth = false
            IsolateClientProtocol = false
            IsolateDestPort = false
            IsolateDestAddr = false
            KeepAliveIsolateSOCKSAuth = false
            SessionGroup(-1)
        }

        assertTrue(flags.isEmpty())
    }

    @Test
    fun givenFlagBuilder_whenSocks_thenAddsAndRemovesCorrectly() {
        val flags = LinkedHashSet<String>(1, 1.0f)

        fun configure(
            block: ThisBlock<BuilderScopePort.FlagsBuilderSocks>,
        ) {
            BuilderScopePort.FlagsBuilderSocks.configure(flags, block)
        }

        configure {
            NoIPv4Traffic = true
            IPv6Traffic = true
            PreferIPv6 = true
            NoDNSRequest = true
            NoOnionTraffic = true
            OnionTrafficOnly = true
            CacheIPv4DNS = true
            CacheIPv6DNS = true
            CacheDNS = true
            UseIPv4Cache = true
            UseIPv6Cache = true
            UseDNSCache = true
            PreferIPv6Automap = true
            PreferSOCKSNoAuth = true
        }

        assertEquals(14, flags.size)
        assertTrue(flags.contains("NoIPv4Traffic"))
        assertTrue(flags.contains("IPv6Traffic"))
        assertTrue(flags.contains("PreferIPv6"))
        assertTrue(flags.contains("NoDNSRequest"))
        assertTrue(flags.contains("NoOnionTraffic"))
        assertTrue(flags.contains("OnionTrafficOnly"))
        assertTrue(flags.contains("CacheIPv4DNS"))
        assertTrue(flags.contains("CacheIPv6DNS"))
        assertTrue(flags.contains("CacheDNS"))
        assertTrue(flags.contains("UseIPv4Cache"))
        assertTrue(flags.contains("UseIPv6Cache"))
        assertTrue(flags.contains("UseDNSCache"))
        assertTrue(flags.contains("PreferIPv6Automap"))
        assertTrue(flags.contains("PreferSOCKSNoAuth"))

        configure {}
        assertEquals(14, flags.size)

        configure {
            NoIPv4Traffic = false
            IPv6Traffic = false
            PreferIPv6 = false
            NoDNSRequest = false
            NoOnionTraffic = false
            OnionTrafficOnly = false
            CacheIPv4DNS = false
            CacheIPv6DNS = false
            CacheDNS = false
            UseIPv4Cache = false
            UseIPv6Cache = false
            UseDNSCache = false
            PreferIPv6Automap = false
            PreferSOCKSNoAuth = false
        }
        assertTrue(flags.isEmpty())
    }

    @Test
    fun givenFlagBuilder_whenUnix_thenAddsAndRemovesCorrectly() {
        val flags = LinkedHashSet<String>(1, 1.0f)

        fun configure(
            isControl: Boolean = true,
            block: ThisBlock<BuilderScopePort.FlagsBuilderUnix>,
        ) {
            BuilderScopePort.FlagsBuilderUnix.configure(isControl, flags, block)
        }

        configure {
            GroupWritable = true
            WorldWritable = true
            RelaxDirModeCheck = true
        }
        assertEquals(3, flags.size)
        assertTrue(flags.contains("GroupWritable"))
        assertTrue(flags.contains("WorldWritable"))
        assertTrue(flags.contains("RelaxDirModeCheck"))

        configure {}
        assertEquals(3, flags.size)

        configure {
            RelaxDirModeCheck = false
            WorldWritable = false
            GroupWritable = false
        }
        assertTrue(flags.isEmpty())

        // Will not configure it for non-control port
        configure(isControl = false) { RelaxDirModeCheck = true }
        assertTrue(flags.isEmpty())
    }

    @Test
    fun givenRunOnCondition_whenTrue_thenRuns() {
        // Test our test helper
        var invocationRun = 0
        runOnCondition(condition = true) { invocationRun++ }
        assertEquals(1, invocationRun)
    }

    @Test
    fun givenRunOnCondition_whenFalse_thenDoesNotRun() {
        // Test our test helper
        var invocationRun = 0
        runOnCondition(message = null, condition = false) { invocationRun++ }
        assertEquals(0, invocationRun)
    }

    private inline fun runOnCondition(
        message: String? = "Skipping...",
        condition: Boolean,
        block: () -> Unit,
    ) {
        if (!condition) {
            if (message != null) println(message)
            return
        } else {
            block()
        }
    }
}
