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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.NetworkObserver
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TestUtils.ensureStoppedOnTestCompletion
import io.matthewnelson.kmp.tor.runtime.TorCmdJob
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ObserverConnectivityUnitTest {

    private class TestNetworkObserver: NetworkObserver() {

        @Volatile
        var isConnected: Boolean = true

        override fun isNetworkConnected(): Boolean = isConnected
        fun update(conn: Connectivity) { notify(conn) }
    }

    private fun env(
        testDir: String,
        block: TorRuntime.Environment.Builder.() -> Unit = {},
    ) = TorRuntime.Environment.Builder(
        SysTempDir.resolve("kmp_tor_test/$testDir/work"),
        SysTempDir.resolve("kmp_tor_test/$testDir/cache"),
        installer = { dir -> TorResources(dir) },
    ) {
        block(this)
    }

    @Test
    fun givenConnectivityChanges_whenMultiple_thenOnlyLastIsUsed() = runTest {
        val observer = TestNetworkObserver()
        observer.isConnected = false

        val cmds = mutableListOf<TorCmdJob>()
        val warnings = mutableListOf<String>()

        val runtime = TorRuntime.Builder(env("obs_conn_no_net")) {
            networkObserver = observer
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.LOG.PROCESS) { println(it) }
            observerStatic(RuntimeEvent.LOG.WARN) { warnings.add(it) }
            observerStatic(RuntimeEvent.EXECUTE.CMD) { cmds.add(it) }
        }.ensureStoppedOnTestCompletion()

        runtime.environment().debug = true

        runtime.startDaemonAsync()

        var contains = false
        for (warning in warnings) {
            if (warning.contains("No Network Connectivity. Waiting...")) {
                contains = true
                break
            }
        }
        assertTrue(contains, "StartDaemon enabled network when connectivity was false")

        observer.isConnected = true

        // This ensures that, in the event of multiple notifications
        // of connectivity changes on the device, that only the latest
        // is
        listOf(
            NetworkObserver.Connectivity.Connected,
            NetworkObserver.Connectivity.Disconnected,
            NetworkObserver.Connectivity.Disconnected,
            NetworkObserver.Connectivity.Connected,
            NetworkObserver.Connectivity.Disconnected,
            NetworkObserver.Connectivity.Connected,
        ).forEach { conn ->
            observer.update(conn)
            withContext(Dispatchers.Default) { delay(50.milliseconds) }
        }

        withContext(Dispatchers.Default) { delay(500.milliseconds) }

        assertEquals(1, cmds.count { it.name == "SETCONF" })
    }
}
