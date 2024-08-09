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
package io.matthewnelson.kmp.tor.runtime.service.ui

import android.os.Build
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import io.matthewnelson.kmp.tor.runtime.service.TorServiceUI
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class KmpTorServiceUITest {

    private val factory = KmpTorServiceUI.Factory(
        iconReady = android.R.drawable.stat_notify_chat,
        iconNotReady = android.R.drawable.stat_notify_more,
        info = TorServiceUI.NotificationInfo.of(
            channelID = "Tor Channel ID",
            channelName = "Tor Channel Name",
            channelDescription = "Tor Channel Description",
            channelShowBadge = false,
            notificationID = 615,
        ),
        block = {
            // TODO

            defaultConfig {
                iconData = android.R.drawable.stat_notify_sync_noanim
                enableActionStop = true
                enableActionRestart = true
                displayName = DisplayName.Text.of("testing...")
            }
        }
    ).also { it.debug = true }

    private val serviceConfig = TorServiceConfig.Foreground.Builder(factory) {
        // TODO
    }

    @Test
    fun givenUIFactory_whenForeground_thenIsSuccessful() = runTest {
        if (Build.VERSION.SDK_INT < 21) {
            println("Skipping...")
            return@runTest
        }

        val env = serviceConfig.newEnvironment(
            dirName = "ui_startup",
            installer = { dir -> TorResources(dir) },
            block = {
                defaultEventExecutor = OnEvent.Executor.Immediate
            }
        ).also { it.debug = true }

        var logUIStateObserved = false
        val runtime = TorRuntime.Builder(env) {
            observerStatic(RuntimeEvent.LOG.DEBUG) { log ->
                if (log.contains("UIState[")) {
                    logUIStateObserved = true
                }
//                println(log)
            }
        }

        currentCoroutineContext().job.invokeOnCompletion {
            runtime.enqueue(Action.StopDaemon, {}, {})
        }

        runtime.startDaemonAsync()
        withContext(Dispatchers.IO) { delay(1.seconds) }
        runtime.stopDaemonAsync()
        assertTrue(logUIStateObserved)
    }
}
