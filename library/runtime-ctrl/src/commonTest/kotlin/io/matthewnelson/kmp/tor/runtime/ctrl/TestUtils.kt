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

import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.resource.tor.TorResources
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

public object TestUtils {

    private const val AUTH_HASH = "16:E2CD63EF9E0607F76072F91A7CD8696F6630A1647064694F255CC3577A"
    public val AUTH_PASS = """
            Hello
            World
            !

        """.trimIndent()

    public suspend fun startTor(ctrlPortArg: String): Process {
        val paths = INSTALLER.install()
        val homeDir = INSTALLER.installationDir
        val dataDir = homeDir.resolve("data")
        val cacheDir = homeDir.resolve("cache")
        val ctrlPortFile = dataDir.resolve("ctrl.txt")

        withContext(Dispatchers.Default) { delay(750.milliseconds) }

        val p = Process.Builder(paths.tor)
            .args("--DataDirectory")
            .args(dataDir.also { it.mkdirs() }.path)
            .args("--CacheDirectory")
            .args(cacheDir.also { it.mkdirs() }.path)
            .args("--ControlPortWriteToFile")
            .args(ctrlPortFile.path)
            .args("--GeoIPFile")
            .args(paths.geoip.path)
            .args("--GeoIPv6File")
            .args(paths.geoip6.path)
            .args("--DormantCanceledByStartup")
            .args("1")
            .args("--HashedControlPassword")
            .args(AUTH_HASH)
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

        currentCoroutineContext().job.invokeOnCompletion {
            p.destroy()
            ctrlPortFile.delete()
        }

        return withContext(Dispatchers.Default) {
            val mark = TimeSource.Monotonic.markNow()

            var exists = false
            while (!exists && mark.elapsedNow() < 3_000.milliseconds) {
                delay(25.milliseconds)
                exists = ctrlPortFile.exists()
            }

            if (!exists) {
                p.destroy()
                throw IllegalStateException("ControlPortFile timed out after 3s")
            }

            println("ControlPortFile found after ${mark.elapsedNow().inWholeMilliseconds}ms")
            p
        }
    }

    public val INSTALLER by lazy {
        TorResources(installationDir = SysTempDir.resolve("kmp_tor_ctrl"))
    }
}
