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
package io.matthewnelson.kmp.tor.runtime.mobile

import android.os.Build
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import io.matthewnelson.kmp.tor.runtime.core.ItBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TorCtrlFactoryTest {

    @Test
    fun givenUnixDomainSocket_whenConnect_thenIsSuccessful() = runTest {
        if (Build.VERSION.SDK_INT < 21) {
            println("Skipping...")
            return@runTest
        }

        val uds = INSTALLER.installationDir
            .resolve("data")
            .resolve("ctrl.sock")

        val ctrlArg = TorConfig.__ControlPort.Builder {
            asUnixSocket { file = uds }
        }.argument

        val p = startTor(ctrlArg)

        var invocationIntercept = 0
        val ctrl = try {
            TorCtrl.Factory(
                interceptors = setOf(
                    TorCmdInterceptor.intercept<TorCmd.Authenticate> { _, cmd ->
                        invocationIntercept++
                        cmd
                    }
                ),
                debugger = { println(it) },
                handler = UncaughtException.Handler.THROW,
            ).connectAsync(uds)
        } catch (t: Throwable) {
            p.destroy()
            throw t
        }

        try {
            ctrl.executeAsync(TorCmd.Authenticate())
        } finally {
            p.destroy()
        }

        withContext(Dispatchers.Default) { delay(350.milliseconds) }

        assertTrue(ctrl.isDestroyed())
        assertEquals(1, invocationIntercept)
    }

    private suspend fun startTor(ctrlPortArg: String): Process {
        val paths = INSTALLER.install()
        val homeDir = INSTALLER.installationDir
        val dataDir = homeDir.resolve("data")
        val cacheDir = homeDir.resolve("cache")

        withContext(Dispatchers.Default) { delay(350.milliseconds) }

        val p = Process.Builder(paths.tor)
            .args("--DataDirectory")
            .args(dataDir.also { it.mkdirs() }.path)
            .args("--CacheDirectory")
            .args(cacheDir.also { it.mkdirs() }.path)
            .args("--GeoIPFile")
            .args(paths.geoip.path)
            .args("--GeoIPv6File")
            .args(paths.geoip6.path)
            .args("--DormantCanceledByStartup")
            .args("1")
            .args("--ControlPort")
            .args(ctrlPortArg)
            .args("--SocksPort")
            .args("0")
            .args("--DisableNetwork")
            .args("1")
            .args("--RunAsDaemon")
            .args("0")
            .args("--__OwningControllerProcess")
            .args(Process.Current.pid().toString())
            .destroySignal(Signal.SIGTERM)
            .environment("HOME", homeDir.path)
            .stdin(Stdio.Null)
            .stdout(Stdio.Inherit)
            .stderr(Stdio.Inherit)
            .spawn()

        currentCoroutineContext().job.invokeOnCompletion { p.destroy() }

        withContext(Dispatchers.Default) { delay(350.milliseconds) }

        return p
    }

    private companion object {

        private val INSTALLER by lazy {
            TorResources(installationDir = SysTempDir.resolve("kmp_tor_ctrl"))
        }
    }
}
