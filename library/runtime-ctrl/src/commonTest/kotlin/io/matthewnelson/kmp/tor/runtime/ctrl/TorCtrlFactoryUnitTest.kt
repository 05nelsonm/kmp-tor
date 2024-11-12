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
package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.net.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.net.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.util.findNextAvailableAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalStdlibApi::class, InternalKmpTorApi::class)
class TorCtrlFactoryUnitTest {

    @Test
    fun givenIPv4_whenConnect_thenIsSuccessful() = runTest {
        LocalHost.IPv4.runTCPTest(9055.toPortEphemeral())
    }

    @Test
    fun givenIPv6_whenConnect_thenIsSuccessful() = runTest {
        LocalHost.IPv6.runTCPTest(9155.toPortEphemeral())
    }

    @Test
    fun givenConnection_whenTorStops_thenDestroysItself() = runTest {
        LocalHost.IPv4.runTCPTest(9255.toPortEphemeral()) { runtime, _ ->
            runtime.close()
        }
    }

    @Test
    fun givenUnixDomainSocket_whenConnect_thenIsSuccessful() = runTest {
        val uds = LOADER.resourceDir
            .resolve("data")
            .resolve("ctrl.sock")

        val ctrlArg = try {
            TorOption.__ControlPort.asSetting {
                unixSocket(value = uds)
            }.items.first().argument
        } catch (_: UnsupportedOperationException) {
            println("Skipping...")
            return@runTest
        }

        val runtime = startTor(ctrlArg)
        val factory = TorCtrl.Factory(handler = UncaughtException.Handler.THROW)

        val ctrl = try {
            try {
                factory.connectAsync(uds)
            } catch (_: Throwable) {
                withContext(Dispatchers.Default) { delay(350.milliseconds) }
                factory.connectAsync(uds)
            }
        } finally {
            runtime.close()
        }

        withContext(Dispatchers.Default) { delay(350.milliseconds) }

        assertTrue(ctrl.isDestroyed())
    }

    private suspend fun LocalHost.runTCPTest(
        startPort: Port.Ephemeral,
        block: suspend (runtime: AutoCloseable, ctrl: TorCtrl) -> Unit = { runtime, ctrl ->
            // default test behavior is to disconnect ctrl listener first
            ctrl.destroy()
            runtime.close()
        }
    ) {
        val debugLogs = mutableListOf<String>()
        val lock = SynchronizedObject()
        val factory = TorCtrl.Factory(
            debugger = { synchronized(lock) { debugLogs.add(it) } },
            handler = UncaughtException.Handler.THROW
        )

        val host = resolve()
        val port = startPort.findNextAvailableAsync(100, this)

        val address = IPSocketAddress(host, port)

        val runtime = startTor(address.toString())

        val ctrl = try {
            factory.connectAsync(address)
        } catch (_: Throwable) {
            withContext(Dispatchers.Default) { delay(350.milliseconds) }
            factory.connectAsync(address)
        }

        val latch = Job(currentCoroutineContext().job)
        var invocationDestroy = 0
        ctrl.invokeOnDestroy {
            invocationDestroy++
            latch.complete()
        }

        block(runtime, ctrl)

        latch.join()

        assertEquals(1, invocationDestroy)

        synchronized(lock) {
            listOf(
                "Starting Read",
                "End Of Stream",
                "Stopped Reading",

                // Ensures that, even if destroy was not called
                // externally (e.g. Tor stopped and closed the
                // connection), that TorCtrl.destroy was invoked
                "Connection Closed",

                "Scope Cancelled",
            ).forEach { log ->

                for (dLog in debugLogs) {
                    if (dLog.contains(log)) {
                        return@forEach
                    }
                }

                fail("Debug logs did not contain $log")
            }
        }
    }
}
