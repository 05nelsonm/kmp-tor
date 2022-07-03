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
package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.helpers.TorTestHelper
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.InterruptedException
import io.matthewnelson.kmp.tor.manager.util.PortUtil
import kotlinx.coroutines.*
import kotlin.test.*

@OptIn(InternalTorApi::class)
class TorManagerIntegrationTest: TorTestHelper() {

    private class TestListener: TorManagerEvent.Listener() {
        var dispatchCount = 0
            private set
        private var lastInfo: TorManagerEvent.AddressInfo? = null

        fun unixSocksOrThrow(): Set<Path> {
            return unixSocksOrNull() ?: throw Error("unix SocksPorts were not dispatched")
        }
        fun unixSocksOrNull(): Set<Path>? {
            return lastInfo?.unixSocks
        }

        override fun managerEventAddressInfo(info: TorManagerEvent.AddressInfo) {
            lastInfo = info
            dispatchCount++
        }
    }

    @Test
    fun givenMultipleControllerActions_whenStartStopOrRestarted_actionsAreInterrupted() = runBlocking {

        // queueing start here will wait for TorTestHelper start to finish, and will immediately
        // return success as it's already started.
        manager.start()

        // Restarting will give us enough time to load up commands to
        // be executed _after_ restart completes
        manager.restartQuietly()

        val getVersion = TorControlInfoGet.KeyWord.Status.Version.Current()
        val jobs = ArrayList<Job>(5)
        var failures = 0
        repeat(5) { index ->
            launch {
                val result = manager.infoGet(getVersion)
                result.onSuccess {
                    // Don't fail, as the first one may make it through before being interrupted
                    println("Controller Action $index was processed when it should have been interrupted")
                }
                result.onFailure { ex ->
                    assertTrue(ex is InterruptedException)
                    println("Job$index: ${ex.message}")
                    failures++
                }
            }.let { job ->
                jobs.add(job)
            }
        }

        delay(50L)

        // Cancellation of callers job should not
        // produce any results
        jobs[3].cancelAndJoin()

        manager.stop().getOrThrow()

        for (job in jobs) {
            job.cancelAndJoin()
        }

        assertTrue(failures > 0)

        Unit
    }

    @Test
    fun givenTorManager_whenUnixSocksPortOpenClose_addressInfoIsProperlyDispatched() = runBlocking {
        // Only run if support for domain sockets is had
        if (!PlatformUtil.isLinux) return@runBlocking

        val initialSocksInstances = awaitLastValidatedTorConfig().torConfig.settings
            .filterIsInstance<UnixSockets.Socks>()

        assertFalse(initialSocksInstances.isEmpty())

        val listener = TestListener()
        manager.addListener(listener)

        awaitBootstrap()
        delay(100L)

        // Ensure first dispatch contains unix SocksPort
        // for the given config entry.
        val dispatch0 = listener.unixSocksOrThrow()

        assertEquals(initialSocksInstances.size, dispatch0.size)
        for ((i, path) in dispatch0.withIndex()) {
            assertEquals(initialSocksInstances[i].value?.path, path)
        }

        val socks1 = initialSocksInstances.first().clone()
        socks1.set(FileSystemFile(configProvider.workDir.builder {
                addSegment(DataDirectory.DEFAULT_NAME)
                addSegment("socks_test_1.sock")
            }))
        socks1.setFlags(setOf(
            Ports.Socks.Flag.OnionTrafficOnly
        ))

        val socks2 = socks1.clone()
        socks2.set(FileSystemFile(configProvider.workDir.builder {
            addSegment(DataDirectory.DEFAULT_NAME)
            addSegment("socks_test_2.sock")
        }))

        var throwable: Throwable? = null
        try {
            // Run 1: Set Socks1 and Socks2 ports
            manager.configSet(setOf(socks1, socks2)).getOrThrow()
            val entries1 = manager.configGet(socks1.keyword).getOrThrow()

            assertEquals(2, entries1.size)
            assertTrue(entries1[0].value.contains(socks1.value?.path?.value!!))
            assertTrue(entries1[1].value.contains(socks2.value?.path?.value!!))

            delay(250L)

            val dispatch1 = listener.unixSocksOrThrow()

            assertEquals(2, dispatch1.size)
            assertEquals(socks1.value?.path, dispatch1.elementAt(0))
            assertEquals(socks2.value?.path, dispatch1.elementAt(1))

            // Run 2: Set Socks1
            manager.configSet(socks1).getOrThrow()

            val entries2 = manager.configGet(socks1.keyword).getOrThrow()
            assertEquals(1, entries2.size)
            assertTrue(entries2.first().value.contains(socks1.value?.path?.value!!))

            delay(250L)

            val dispatch2 = listener.unixSocksOrThrow()
            assertEquals(1, dispatch2.size)
            assertEquals(socks1.value?.path, dispatch2.first())

            // Run 3: Non-unix SocksPort
            val port = PortUtil.findNextAvailableTcpPort(Port(9055), limit = 90)
            manager.configSet(Ports.Socks().set(AorDorPort.Value(PortProxy(port.value)))).getOrThrow()

            val entries3 = manager.configGet(socks1.keyword).getOrThrow()
            assertEquals(1, entries3.size)
            assertEquals(port.value.toString(), entries3.first().value)

            delay(250L)

            val dispatch3 = listener.unixSocksOrNull()
            assertNull(dispatch3)

            // Run 4: Set Socks2
            manager.configSet(socks2).getOrThrow()

            val entries4 = manager.configGet(socks2.keyword).getOrThrow()
            assertEquals(1, entries4.size)
            assertTrue(entries4.first().value.contains(socks2.value?.path?.value!!))

            delay(250L)

            val dispatch4 = listener.unixSocksOrThrow()
            assertEquals(1, dispatch4.size)
            assertEquals(socks2.value?.path, dispatch4.first())

            // Run 5: Disable Network
            val disableNet = DisableNetwork()
            manager.configSet(disableNet.set(TorF.True))

            val dispatch5 = listener.unixSocksOrNull()
            assertNull(dispatch5)

            // Run 6: Enable Network
            manager.configSet(disableNet.set(TorF.False))

            delay(250L)
            val dispatch6 = listener.unixSocksOrThrow()
            assertEquals(1, dispatch6.size)
            assertEquals(socks2.value?.path, dispatch6.first())

            assertEquals(7, listener.dispatchCount)
        } catch (t: Throwable) {
            throwable = t
        } finally {
            manager.configLoad(awaitLastValidatedTorConfig().torConfig)
            manager.removeListener(listener)

            if (throwable != null) {
                throw throwable
            }
        }

        Unit
    }
}
