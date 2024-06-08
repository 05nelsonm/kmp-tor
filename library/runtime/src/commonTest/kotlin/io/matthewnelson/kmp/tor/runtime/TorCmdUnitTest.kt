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

import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4OrNull
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V6.Companion.toIPAddressV6OrNull
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHost
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv4
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv6
import io.matthewnelson.kmp.tor.runtime.core.ctrl.ConfigEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.TestUtils
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

        TestUtils.KEYWORDS.forEach { kw ->
            runtime.enqueue(TorCmd.Config.Get(kw), onFailure, onSuccess)
        }

        // Will suspend test until all previously enqueued jobs complete.
        // This also ensures that multi-keyword requests are functional.
        val resultMulti = runtime.executeAsync(TorCmd.Config.Get(
            TorConfig.ConnectionPadding,
            TorConfig.DataDirectory,
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
            port {
                virtual = 80.toPort()

                try {
                    targetAsUnixSocket {
                        file = runtime.environment().workDirectory
                            .resolve("test_hs.sock")
                    }
                } catch (_: UnsupportedOperationException) {}
            }
            port { virtual = 443.toPort() }
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
            port { virtual = 80.toPort() }
            flags { DiscardPK = true }
        })

        // TorCmd.Onion.Add.destroy
        assertEquals(true, entry1.privateKey?.isDestroyed())

        // DiscardPK set. Should not have returned PrivateKey
        assertNull(entry2.privateKey)
        assertFalse(containsRedacted)

        runtime.executeAsync(TorCmd.Onion.Delete(entry1.publicKey))

        val entry3 = runtime.executeAsync(TorCmd.Onion.Add(
            addressKey = keyCopy,
            destroyKeyOnJobCompletion = false,
        ) {
            for (keys in authKeys) {
                clientAuth(keys.first)
            }
            port { virtual = 80.toPort() }
            flags { DiscardPK = true }
        })

        assertFalse(keyCopy.isDestroyed())
        assertFalse(containsRedacted)

        assertEquals(authKeys.size, entry3.clientAuth.size)
        authKeys.forEach { (public, _) ->
            assertTrue(entry3.clientAuth.contains(public))
        }

        runtime.stopDaemonAsync()
    }

    @Test
    fun givenOnionClientAuth_whenAdd_thenIsAsExpected() = runTest {
        val authKeys = clientAuthTestKeyPairs()

        val runtime = TorRuntime.Builder(testEnv("cmd_oca_add")) {
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
        }.ensureStoppedOnTestCompletion()

        runtime.startDaemonAsync()

        val entry = runtime.executeAsync(TorCmd.Onion.Add(ED25519_V3) {
            port { virtual = 80.toPort() }
            flags { DiscardPK = true }
        })

        listOf<Pair<String?, (Reply.Success) -> Unit>>(
            "bob" to { reply ->
                assertIs<Reply.Success.OK>(reply)
            },
            null to { reply ->
                // 251 Client for onion existed and replaced
                assertIsNot<Reply.Success.OK>(reply)
            }
        ).forEach { (clientName, assertion) ->
            val result = runtime.executeAsync(
                TorCmd.OnionClientAuth.Add(
                    entry.publicKey.address() as OnionAddress.V3,
                    authKeys.first().first,
                    clientName
                ) {}
            )

            assertion(result)
        }

        assertFailsWith<Reply.Error> {
            runtime.executeAsync(
                TorCmd.OnionClientAuth.Add(
                    entry.publicKey.address() as OnionAddress.V3,
                    authKeys.last().first,
                    null,
                ) {
                    // Should produce error b/c no directory.
                    Permanent = true
                }
            )
        }

        runtime.executeAsync(TorCmd.OnionClientAuth.Remove(
            entry.publicKey as ED25519_V3.PublicKey
        )).let { result -> assertIs<Reply.Success.OK>(result) }

        runtime.stopDaemonAsync()
    }
}
