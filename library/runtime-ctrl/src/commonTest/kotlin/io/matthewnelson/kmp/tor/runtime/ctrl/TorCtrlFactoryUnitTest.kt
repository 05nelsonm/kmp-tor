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
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.address.LocalHost
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import io.matthewnelson.kmp.tor.runtime.core.util.findAvailableAsync
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
class TorCtrlFactoryUnitTest {

    @Test
    fun givenIPv4_whenConnect_thenIsSuccessful() = runTest {
        LocalHost.IPv4.runTCPTest()
    }

    @Test
    fun givenIPv6_whenConnect_thenIsSuccessful() = runTest {
        LocalHost.IPv6.runTCPTest()
    }

    @Test
    fun givenConnection_whenTorStops_thenDestroysItself() = runTest {
        LocalHost.IPv4.runTCPTest { process, _ ->
            process.destroy()
        }
    }

//    @Test
//    fun givenUnixDomainSocket_whenConnect_thenIsSuccessful() = runTest {
//        val uds = TestUtils.INSTALLER.installationDir
//            .resolve("data")
//            .resolve("ctrl.sock")
//
//        uds.delete()
//
//        val ctrlArg = try {
//            TorConfig.__ControlPort.Builder {
//                asUnixSocket { file = uds }
//            }.argument
//        } catch (_: UnsupportedOperationException) {
//            println("Skipping...")
//            return@runTest
//        }
//
//        val p = TestUtils.startTor(ctrlArg)
//
//        val ctrl = try {
//            TorCtrl.Factory(handler = UncaughtException.Handler.THROW)
//                .connectAsync(uds)
//        } finally {
//            p.destroy()
//        }
//
//        withContext(Dispatchers.Default) { delay(250.milliseconds) }
//
//        assertTrue(ctrl.isDestroyed())
//    }

    private suspend fun LocalHost.runTCPTest(
        block: suspend (process: Process, ctrl: TorCtrl) -> Unit = { process, ctrl ->
            // default test behavior is to disconnect ctrl listener first
            ctrl.destroy()
            process.destroy()
        }
    ) {
        val debugLogs = mutableListOf<String>()
        val lock = SynchronizedObject()
        val factory = TorCtrl.Factory(
            debugger = { synchronized(lock) { debugLogs.add(it) } },
            handler = UncaughtException.Handler.THROW
        )

        val host = resolve()
        val port = 9055.toPortProxy().findAvailableAsync(1_000, this)

        val address = ProxyAddress(host, port)

        val process = TestUtils.startTor(ctrlPortArg = address.toString())

        val ctrl = factory.connectAsync(address)
        var invocationDestroy = 0
        ctrl.invokeOnDestroy { invocationDestroy++ }

        block(process, ctrl)

        withContext(Dispatchers.Default) { delay(250.milliseconds) }

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
