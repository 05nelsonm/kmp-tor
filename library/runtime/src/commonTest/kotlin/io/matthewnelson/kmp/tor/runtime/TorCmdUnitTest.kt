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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHost
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv4
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv6
import io.matthewnelson.kmp.tor.runtime.core.ctrl.ConfigEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.HiddenServiceEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.ctrl.builder.BuilderScopeClientAuthAdd
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V4.Companion.toIPAddressV4OrNull
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.V6.Companion.toIPAddressV6OrNull
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.runTorTest
import io.matthewnelson.kmp.tor.runtime.test.testClientAuthKeyPairs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class TorCmdUnitTest {

    @Test
    fun givenConfigGet_whenAllKeywords_thenAreRecognizedByController() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        val failures = mutableListOf<Throwable>()
        val lock = synchronizedObject()
        val onFailure = OnFailure { t -> synchronized(lock) { failures.add(t) } }
        val onSuccess = OnSuccess<List<ConfigEntry>> { entries ->
            assertEquals(1, entries.size)
//            println(entries.first())
        }

        TorOption.entries.map { option ->
            runtime.enqueue(TorCmd.Config.Get(option), onFailure, onSuccess)
        }.forEach { job ->
            // Wait for all jobs to complete
            while (job.isActive) {
                withContext(Dispatchers.Default) { delay(5.milliseconds) }
            }
        }

        synchronized(lock) {
            var e: AssertionError? = null

            failures.forEach { failure ->
                if (e == null) {
                    e = AssertionError("Config.Get failures")
                }

                e!!.addSuppressed(failure)
            }

            e?.let { throw it }
        }
    }

    @Test
    fun givenMapAddress_whenMapping_thenIsAsExpected() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        val expected = "torproject.org"

        val results = runtime.executeAsync(
            TorCmd.MapAddress(
                expected.mappingToAnyHost(),
                expected.mappingToAnyHostIPv4(),
                expected.mappingToAnyHostIPv6(),
            ),
        )

        listOf<(from: String) -> Unit>(
            { from ->
                assertTrue(from.endsWith(".virtual"))
            },
            { from ->
                val a = from.toIPAddressV4OrNull()
                assertNotNull(a)
                assertIsNot<IPAddress.V4.AnyHost>(a)
            },
            { from ->
                val a = from.toIPAddressV6OrNull()
                assertNotNull(a)
                assertIsNot<IPAddress.V6.AnyHost>(a)
            }
        ).forEachIndexed { index, assertFrom ->
            val result = results.elementAt(index)
            assertEquals(expected, result.to)
            assertFrom(result.from)
            assertFalse(result.isUnmapping)
        }

        runtime.executeAsync(
            TorCmd.Info.Get("address-mappings/control")
        ).let { mappings ->
            assertEquals(results.size, mappings.entries.first().value.lines().size)
        }

        runtime.executeAsync(
            TorCmd.MapAddress(results.map { result -> result.toUnmapping() })
        ).forEach { result ->
            assertTrue(result.isUnmapping)
        }

        runtime.executeAsync(
            TorCmd.Info.Get("address-mappings/control")
        ).let { mappings ->
            // Should all have been un mapped
            assertTrue(mappings.entries.first().value.isEmpty())
        }
    }

    @Test
    fun givenOnion_whenAddAndDelete_thenIsAsExpected() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        val keys = runtime.testClientAuthKeyPairs()

        var containsRedacted = false
        val observer = RuntimeEvent.LOG.DEBUG.observer { line ->
            if (line.endsWith("<< 250-PrivateKey=ED25519-V3:[REDACTED]")) {
                containsRedacted = true
            }
//            println(line)
        }
        runtime.subscribe(observer)

        val entry1 = runtime.executeAsync(TorCmd.Onion.Add.new(ED25519_V3) {
            port(virtual = Port.HTTP) {
                try {
                    target(unixSocket = runtime.environment()
                        .workDirectory
                        .resolve("test_hs.sock"))
                } catch (_: UnsupportedOperationException) {}
            }
            port(virtual = Port.HTTPS)
        })

        // DiscardPK not expressed
        assertNotNull(entry1.privateKey)
        // Also confirms TorCtrl parser received the
        // line `250-PrivateKey=`
        assertTrue(containsRedacted)

        // Reset and remove HS
        containsRedacted = false
        runtime.executeAsync(TorCmd.Onion.Delete(entry1.publicKey))

        assertEquals(false, entry1.privateKey?.isDestroyed())
        val keyCopy = entry1.privateKey?.encoded()?.toED25519_V3PrivateKeyOrNull()
        assertNotNull(keyCopy)

        val entry2 = runtime.executeAsync(TorCmd.Onion.Add.existing(entry1.privateKey!!) {
            port(virtual = Port.HTTP)
            flags { DiscardPK = true }
        })

        // TorCmd.Onion.Add.destroy
        assertEquals(true, entry1.privateKey?.isDestroyed())

        // DiscardPK set. Should not have returned PrivateKey
        assertNull(entry2.privateKey)
        assertFalse(containsRedacted)

        runtime.executeAsync(TorCmd.Onion.Delete(entry1.publicKey))

        val entry3 = runtime.executeAsync(TorCmd.Onion.Add.existing(keyCopy) {

            destroyKeyOnJobCompletion(false)

            for (key in keys) {
                // Public keys
                clientAuth(key.first)
            }
            port(virtual = Port.HTTP)
            flags { DiscardPK = true }
        })

        assertFalse(keyCopy.isDestroyed())
        assertFalse(containsRedacted)

        assertEquals(keys.size, entry3.clientAuth.size)
        keys.forEach { (public, _) ->
            assertTrue(entry3.clientAuth.contains(public))
        }
    }

    @Test
    fun givenOnionClientAuth_whenAdd_thenIsAsExpected() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        val keys = runtime.testClientAuthKeyPairs()

        @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
        var containsRedacted = false
        val observer = RuntimeEvent.LOG.DEBUG.observer { line ->
            if (line.contains("x25519:[REDACTED]")) {
                containsRedacted = true
            }
//            println(line)
        }
        runtime.subscribe(observer)

        val entry = runtime.executeAsync(TorCmd.Onion.Add.new(ED25519_V3) {
            port(virtual = Port.HTTP)
            flags { DiscardPK = true }
            for (key in keys) {
                // PublicKey
                clientAuth(key.first)
            }
        })

        listOf<Pair<String?, (Reply.Success) -> Unit>>(
            "" to { reply ->
                assertIs<Reply.Success.OK>(reply)
            },
            "123456789012346" to { reply ->
                // 251 Client for onion existed and replaced
                assertIsNot<Reply.Success.OK>(reply)
            },
            null to { reply ->
                // 251 Client for onion existed and replaced
                assertIsNot<Reply.Success.OK>(reply)
            }
        ).forEach { (nickname, assertion) ->
            containsRedacted = false

            val result = runtime.executeAsync(
                TorCmd.OnionClientAuth.Add(
                    entry.publicKey.address() as OnionAddress.V3,
                    // PrivateKey
                    keys.first().second,
                ) {
                    clientName(nickname)
                    destroyKeyOnJobCompletion(false)
                }
            )

            assertTrue(containsRedacted)
            assertion(result)
        }

        listOf<ThisBlock<BuilderScopeClientAuthAdd>>(
            // Should produce error because no TorOption.ClientOnionAuthDir specified.
            ThisBlock { flags { Permanent = true } },
            // Should produce error because nickname is too long.
            ThisBlock { clientName("12345678901234567") },
        ).forEach { block ->
            assertFailsWith<Reply.Error> {
                runtime.executeAsync(
                    TorCmd.OnionClientAuth.Add(
                        entry.publicKey.address() as OnionAddress.V3,
                        keys.last().second,
                    ) {
                        destroyKeyOnJobCompletion(false)
                        apply(block)
                    }
                )
            }
        }

        runtime.executeAsync(TorCmd.OnionClientAuth.Remove(
            entry.publicKey as ED25519_V3.PublicKey
        )).let { result -> assertIs<Reply.Success.OK>(result) }
    }

    @Test
    fun givenOnionClientAuth_whenView_thenIsAsExpected() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        runtime.executeAsync(TorCmd.Config.Set {
            val dir = runtime
                .environment()
                .workDirectory
                .resolve("auth_private_files")
                .mkdirs2(mode = "700", mustCreate = false)

            TorOption.ClientOnionAuthDir.configure(dir)
        })

        val keys = runtime.testClientAuthKeyPairs()
        val entries = mutableListOf<HiddenServiceEntry>().let { list ->
            val cmd = TorCmd.Onion.Add.new(ED25519_V3) {
                port(virtual = Port.HTTP)
                flags { DiscardPK = true }
            }

            repeat(keys.size) {
                val entry = runtime.executeAsync(cmd)
                runtime.executeAsync(TorCmd.Onion.Delete(entry.publicKey))
                list.add(entry)
            }

            list.toImmutableList()
        }

        assertTrue(keys.isNotEmpty())
        assertEquals(keys.size, entries.size)

        val addCommands = entries.mapIndexed { index, entry ->
            TorCmd.OnionClientAuth.Add(
                entry.publicKey.address() as OnionAddress.V3,
                keys.elementAt(index).second,
            ) {
                if (index == 0) return@Add

                clientName("test$index")

                if (index == 2) {
                    flags { Permanent = true }
                }
            }
        }

        addCommands.forEach { cmd -> runtime.executeAsync(cmd) }

        runtime.executeAsync(
            TorCmd.OnionClientAuth.View(
                entries.first().publicKey as ED25519_V3.PublicKey,
            )
        ).let { result -> assertEquals(1, result.size) }

        val all = runtime.executeAsync(TorCmd.OnionClientAuth.View.ALL)
        all.forEach { println(it) }

        val filtered = entries.map { it.publicKey.address() }.let { addresses ->
            // Could contain more entries from the filesystem, so need to
            // filter for those ONLY added for the hidden services for
            // this test.
            val filtered = all.filter { addresses.contains(it.address) }
            assertEquals(addresses.size, filtered.size)
            filtered
        }

        for (cmd in addCommands) {
            val authEntry = filtered.first { it.address == cmd.address }
            assertEquals(cmd.clientName, authEntry.clientName)
            assertEquals(cmd.flags, authEntry.flags)
        }

        // Remove all to check empty return
        all.forEach { entry ->
            val address = entry.address as OnionAddress.V3
            runtime.executeAsync(TorCmd.OnionClientAuth.Remove(address))
        }

        runtime.executeAsync(TorCmd.OnionClientAuth.View.ALL).let { result ->
            assertEquals(0, result.size)
        }

        runtime.executeAsync(TorCmd.OnionClientAuth.View(
            entries.first().publicKey as ED25519_V3.PublicKey
        )).let { result -> assertEquals(0, result.size)  }
    }

    @Test
    fun givenOnionAdd_whenFromCurve25519KeyPair_thenPublicKeyMatchesTor() = runTorTest { runtime ->
        runtime.startDaemonAsync()

        Array(50) { ED25519_V3.generateKeyPair() }.forEach { generated ->
            // Add existing Hidden Service using generated private key
            val entry = runtime.executeAsync(TorCmd.Onion.Add.existing(generated.second) {
                port(Port.HTTP)
                destroyKeyOnJobCompletion(false)
            })

            // Shut it down
            runtime.executeAsync(TorCmd.Onion.Delete(entry.publicKey))

            // Ensure returned onion address matches what was generated
            assertEquals(entry.publicKey, generated.first)
        }
    }
}
