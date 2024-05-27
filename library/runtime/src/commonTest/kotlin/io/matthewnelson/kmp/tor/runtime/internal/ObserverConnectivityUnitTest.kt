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

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob.Companion.toImmediateErrorJob
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob.Companion.toImmediateSuccessJob
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.ensureStoppedOnTestCompletion
import io.matthewnelson.kmp.tor.runtime.test.TestUtils.testEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class ObserverConnectivityUnitTest {

    private class TestNetworkObserver: NetworkObserver() {

        @Volatile
        var isConnected: Boolean = true

        override fun isNetworkConnected(): Boolean = isConnected
        fun update(conn: Connectivity) { notify(conn) }
    }

    @Test
    fun givenConnectivityChange_whenFailure_thenRetriesAsExpected() = runTest {
        var attempt = 0
        var successAfter = 0
        var enqueued = 0
        val notified = mutableListOf<RuntimeEvent<*>>()
        var errorCause: Throwable? = null

        val observer = ObserverConnectivity(
            processor = object : TorCmd.Unprivileged.Processor {
                override fun <Success : Any> enqueue(
                    cmd: TorCmd.Unprivileged<Success>,
                    onFailure: OnFailure,
                    onSuccess: OnSuccess<Success>
                ): EnqueuedJob {
                    assertIs<TorCmd.Config.Set>(cmd)

                    enqueued++
                    errorCause?.let { cause ->
                        return onFailure.toImmediateErrorJob(
                            cmd.keyword,
                            cause,
                            UncaughtException.Handler.THROW
                        )
                    }

                    return if (attempt++ < successAfter) {
                        onFailure.toImmediateErrorJob(
                            cmd.keyword,
                            AssertionError(),
                            UncaughtException.Handler.THROW,
                        )
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        onSuccess.toImmediateSuccessJob(
                            cmd.keyword,
                            Reply.Success.OK as Success,
                            UncaughtException.Handler.THROW
                        )
                    }
                }
            },
            networkObserver = NetworkObserver.noOp(),
            NOTIFIER = object : RuntimeEvent.Notifier {
                override fun <Data : Any, E : RuntimeEvent<Data>> notify(event: E, data: Data) {
                    when (event) {
                        is RuntimeEvent.ERROR,
                        is RuntimeEvent.LOG.WARN -> {
                            notified.add(event)
                        }
                        else -> AssertionError("Unsupported RuntimeEvent[$event]")
                    }
                }
            },
            scope = this,
            executeDelay = 5.milliseconds,
        )

        // Success on 1st attempt
        attempt = 0
        enqueued = 0
        successAfter = 0
        notified.clear()

        observer.invoke(NetworkObserver.Connectivity.Connected)
        withContext(Dispatchers.Default) { delay(50.milliseconds) }
        assertEquals(1, enqueued)
        assertEquals(0, notified.size)

        // Success on 2nd attempt
        attempt = 0
        enqueued = 0
        successAfter = 1
        notified.clear()
        observer.invoke(NetworkObserver.Connectivity.Connected)

        withContext(Dispatchers.Default) { delay(50.milliseconds) }
        assertEquals(2, enqueued)
        assertEquals(1, notified.size)
        assertIs<RuntimeEvent.LOG.WARN>(notified[0])

        // Success on 3rd attempt
        attempt = 0
        enqueued = 0
        successAfter = 2
        notified.clear()
        observer.invoke(NetworkObserver.Connectivity.Connected)

        withContext(Dispatchers.Default) { delay(50.milliseconds) }
        assertEquals(3, enqueued)
        assertEquals(2, notified.size)
        assertIs<RuntimeEvent.LOG.WARN>(notified[0])
        assertIs<RuntimeEvent.LOG.WARN>(notified[1])

        // Failure after 3rd attempt
        attempt = 0
        enqueued = 0
        successAfter = 3
        notified.clear()
        observer.invoke(NetworkObserver.Connectivity.Connected)

        withContext(Dispatchers.Default) { delay(50.milliseconds) }
        assertEquals(3, enqueued)
        assertEquals(3, notified.size)
        assertIs<RuntimeEvent.LOG.WARN>(notified[0])
        assertIs<RuntimeEvent.LOG.WARN>(notified[1])
        assertIs<RuntimeEvent.ERROR>(notified[2])

        // Cancellation when InterruptedException
        attempt = 0
        enqueued = 0
        successAfter = 5
        errorCause = InterruptedException()
        notified.clear()
        observer.invoke(NetworkObserver.Connectivity.Connected)

        withContext(Dispatchers.Default) { delay(50.milliseconds) }
        assertEquals(1, enqueued)
        assertEquals(0, notified.size)

        // Cancellation when CancellationException
        errorCause = CancellationException()
        observer.invoke(NetworkObserver.Connectivity.Connected)

        withContext(Dispatchers.Default) { delay(50.milliseconds) }
        assertEquals(2, enqueued)
        assertEquals(0, notified.size)
    }

    @Test
    fun givenConnectivityChanges_whenMultiple_thenOnlyLastIsUsed() = runTest {
        val observer = TestNetworkObserver()
        observer.isConnected = false

        val cmds = mutableListOf<TorCmdJob>()
        val warnings = mutableListOf<String>()

        val runtime = TorRuntime.Builder(testEnv("obs_conn_no_net")) {
            networkObserver = observer
            observerStatic(RuntimeEvent.LOG.WARN) { warnings.add(it) }
            observerStatic(RuntimeEvent.EXECUTE.CMD) { cmds.add(it) }

//            observerStatic(RuntimeEvent.ERROR) { it.printStackTrace() }
//            observerStatic(RuntimeEvent.LIFECYCLE) { println(it) }
//            observerStatic(RuntimeEvent.LISTENERS) { println(it) }
//            observerStatic(RuntimeEvent.LOG.DEBUG) { println(it) }
//            observerStatic(RuntimeEvent.LOG.INFO) { println(it) }
//            observerStatic(RuntimeEvent.LOG.WARN) { println(it) }
//            observerStatic(RuntimeEvent.LOG.PROCESS) { println(it) }
//            observerStatic(RuntimeEvent.STATE) { println(it) }
        }.ensureStoppedOnTestCompletion()

        runtime.startDaemonAsync()

        var contains = false
        for (warning in warnings) {
            if (warning.contains("No Network Connectivity. Waiting...")) {
                contains = true
                break
            }
        }
        assertTrue(contains, "StartDaemon enabled network when connectivity was false")
        assertFalse(runtime.state().isNetworkEnabled)

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
        assertTrue(runtime.state().isNetworkEnabled)
    }
}
