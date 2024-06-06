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
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHost
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv4
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.mappingToAnyHostIPv6
import io.matthewnelson.kmp.tor.runtime.core.ctrl.AddressMapping.Companion.unmappingFrom
import io.matthewnelson.kmp.tor.runtime.core.ctrl.ConfigEntry
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.test.TestUtils
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
}
