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
@file:Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")

package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4OrNull
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V6.Companion.toIPAddressV6OrNull
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.builder.OnionClientAuthAddBuilder
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHost
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv4
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv6
import io.matthewnelson.kmp.tor.runtime.core.ctrl.ConfigEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.HiddenServiceEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.clientAuthTestKeyPairs
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.ensureStoppedOnTestCompletion
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(InternalKmpTorApi::class)
class TorCmdUnitTest {

    @Test
    fun givenConfigGet_whenKeyword_thenIsRecognizedByController() = runTest {
        val runtime = TorRuntime.Builder(testEnv("cmd_getconf_test")) {
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
        }.ensureStoppedOnTestCompletion()

        runtime.startDaemonAsync()

        val failures = mutableListOf<Throwable>()
        val lock = SynchronizedObject()

        val onFailure = OnFailure { t ->
            synchronized(lock) { failures.add(t) }
        }
        val onSuccess = OnSuccess<List<ConfigEntry>> { entries ->
            assertEquals(1, entries.size)
//            println(entries.first())
        }

        TorOption.entries.forEach { option ->
            runtime.enqueue(TorCmd.Config.Get(option), onFailure, onSuccess)
        }

        // Will suspend test until all previously enqueued jobs complete.
        // This also ensures that multi-keyword requests are functional.
        val resultMulti = runtime.executeAsync(TorCmd.Config.Get(
            TorOption.ConnectionPadding,
            TorOption.DataDirectory,
        ))

        synchronized(lock) {
            var e: AssertionError? = null

            failures.forEach { f ->
                if (e == null) {
                    e = AssertionError("Config.Get failures")
                }

                e!!.addSuppressed(f)
            }

            e?.let { err -> throw err }
        }

        assertEquals(2, resultMulti.size)
    }

    @Test
    fun givenMapAddress_whenMapping_thenIsAsExpected() = runTest {
        val runtime = TorRuntime.Builder(testEnv("cmd_mapaddress_test")) {
            required(TorEvent.ADDRMAP)
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.PROCESS.STDOUT) { println(it) }
        }.ensureStoppedOnTestCompletion()

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

        runtime.stopDaemonAsync()
    }

    @Test
    fun givenOnion_whenAddAndDelete_thenIsAsExpected() = runTest {
        val authKeys = clientAuthTestKeyPairs()

        var containsRedacted = false
        val runtime = TorRuntime.Builder(testEnv("cmd_onion_add")) {
            observerStatic(RuntimeEvent.LOG.DEBUG) { line ->
                if (line.endsWith("<< 250-PrivateKey=ED25519-V3:[REDACTED]")) {
                    containsRedacted = true
                }
//                println(line)
            }
        }.ensureStoppedOnTestCompletion()

        runtime.startDaemonAsync()

        val entry1 = runtime.executeAsync(TorCmd.Onion.Add(ED25519_V3) {
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

        val entry2 = runtime.executeAsync(TorCmd.Onion.Add(entry1.privateKey!!) {
            port(virtual = Port.HTTP)
            flags { DiscardPK = true }
        })

        // TorCmd.Onion.Add.destroy
        assertEquals(true, entry1.privateKey?.isDestroyed())

        // DiscardPK set. Should not have returned PrivateKey
        assertNull(entry2.privateKey)
        assertFalse(containsRedacted)

        runtime.executeAsync(TorCmd.Onion.Delete(entry1.publicKey))

        val entry3 = runtime.executeAsync(TorCmd.Onion.Add(keyCopy) {

            destroyKeyOnJobCompletion = false

            for (keys in authKeys) {
                clientAuth(keys.first)
            }
            port(virtual = Port.HTTP)
            flags { DiscardPK = true }
        })

        assertFalse(keyCopy.isDestroyed())
        assertFalse(containsRedacted)

        assertEquals(authKeys.size, entry3.clientAuth.size)
        authKeys.forEach { (public, _) ->
            assertTrue(entry3.clientAuth.contains(public))
        }
    }

    @Test
    fun givenOnionClientAuth_whenAdd_thenIsAsExpected() = runTest {
        val authKeys = clientAuthTestKeyPairs()

        var containsRedacted = false
        val runtime = TorRuntime.Builder(testEnv("cmd_oca_add")) {
            observerStatic(RuntimeEvent.LOG.DEBUG) { line ->
                if (line.contains("x25519:[REDACTED]")) {
                    containsRedacted = true
                }
//                println(line)
            }
        }.ensureStoppedOnTestCompletion()

        runtime.startDaemonAsync()

        val entry = runtime.executeAsync(TorCmd.Onion.Add(ED25519_V3) {
            port(virtual = Port.HTTP)
            flags { DiscardPK = true }
            for (auth in authKeys) {
                // PublicKey
                clientAuth(auth.first)
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
                    authKeys.first().second,
                ) {
                    clientName = nickname
                    destroyKeyOnJobCompletion = false
                }
            )

            assertTrue(containsRedacted)
            assertion(result)
        }

        listOf<ThisBlock<OnionClientAuthAddBuilder>>(
            // Should produce error because no auth dir
            // specified in config.
            ThisBlock { flags { Permanent = true } },
            ThisBlock { clientName = "12345678901234567" },
        ).forEach { block ->
            assertFailsWith<Reply.Error> {
                runtime.executeAsync(
                    TorCmd.OnionClientAuth.Add(
                        entry.publicKey.address() as OnionAddress.V3,
                        authKeys.last().second,
                    ) {
                        destroyKeyOnJobCompletion = false
                        this.apply(block)
                    }
                )
            }
        }

        runtime.executeAsync(TorCmd.OnionClientAuth.Remove(
            entry.publicKey as ED25519_V3.PublicKey
        )).let { result -> assertIs<Reply.Success.OK>(result) }
    }

    @Test
    fun givenOnionClientAuth_whenView_thenIsAsExpected() = runTest {
        val authKeys = clientAuthTestKeyPairs()

        val runtime = TorRuntime.Builder(testEnv("cmd_onion_view")) {
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
            config { environment ->
                TorOption.ClientOnionAuthDir.configure(directory =
                    environment.workDirectory
                        .resolve("auth_private_files")
                )
            }
        }.ensureStoppedOnTestCompletion()

        runtime.startDaemonAsync()

        val entries = mutableListOf<HiddenServiceEntry>().let { list ->
            val cmd = TorCmd.Onion.Add(ED25519_V3) {
                port(virtual = Port.HTTP)
                flags { DiscardPK = true }
            }

            repeat(authKeys.size) {
                val entry = runtime.executeAsync(cmd)
                runtime.executeAsync(TorCmd.Onion.Delete(entry.publicKey))
                list.add(entry)
            }

            list.toImmutableList()
        }

        assertTrue(authKeys.isNotEmpty())
        assertEquals(authKeys.size, entries.size)

        val addCommands = entries.mapIndexed { index, entry ->
            TorCmd.OnionClientAuth.Add(
                entry.publicKey.address() as OnionAddress.V3,
                authKeys.elementAt(index).second,
            ) {
                if (index == 0) return@Add

                clientName = "test$index"

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

        runtime.stopDaemonAsync()
    }
}
