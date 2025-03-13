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
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val AUTH_HASH = "16:E2CD63EF9E0607F76072F91A7CD8696F6630A1647064694F255CC3577A"

internal val AUTH_PASS = """
            Hello
            World
            !

        """.trimIndent()

internal val LOADER_DIR = SysTempDir.resolve("kmp_tor_ctrl")

internal expect val LOADER: ResourceLoader.Tor

private object TestBinder: ResourceLoader.RuntimeBinder

internal suspend fun startTor(ctrlPortArgument: String): AutoCloseable {
    val loader = LOADER
    val geoipFiles = loader.extract()
    val dataDir = loader.resourceDir.resolve("data")
    val cacheDir = loader.resourceDir.resolve("cache")
    val ctrlPortTxt = dataDir.resolve("ctrl.txt")

    withContext(Dispatchers.Default) { delay(700.milliseconds) }

    val args = ArrayList<String>(25).apply {
        add("--ignore-missing-torrc")
        add("--DataDirectory")
        add(dataDir.also { it.mkdirs() }.path)
        add("--CacheDirectory")
        add(cacheDir.also { it.mkdirs() }.path)
        add("--ControlPortWriteToFile")
        add(ctrlPortTxt.path)
        add("--GeoIPFile")
        add(geoipFiles.geoip.path)
        add("--GeoIPv6File")
        add(geoipFiles.geoip6.path)
        add("--DormantCanceledByStartup")
        add("1")
        add("--HashedControlPassword")
        add(AUTH_HASH)
        add("--ControlPort")
        add(ctrlPortArgument)
        add("--SocksPort")
        add("0")
        add("--DisableNetwork")
        add("1")
        add("--RunAsDaemon")
        add("0")

        if (loader is ResourceLoader.Tor.Exec) {
            add("--__OwningControllerProcess")
            add(Process.Current.pid().toString())
        }
    }

    val result = when (loader) {
        is ResourceLoader.Tor.Exec -> loader.start(args)
        is ResourceLoader.Tor.NoExec -> loader.start(args)
    }

    val disposable = currentCoroutineContext().job.invokeOnCompletion {
        result.close()
        ctrlPortTxt.delete()
    }

    withContext(Dispatchers.Default) {
        val mark = TimeSource.Monotonic.markNow()

        val timeout = 3_000.milliseconds
        var exists = false
        while (!exists && mark.elapsedNow() < timeout) {
            delay(25.milliseconds)
            exists = ctrlPortTxt.exists()
        }

        if (!exists) {
            result.close()
            disposable.dispose()
            throw IllegalStateException("ControlPortFile timed out after ${timeout.inWholeMilliseconds}ms")
        }

        println("ControlPortFile found after ${mark.elapsedNow().inWholeMilliseconds}ms")
    }

    return result
}

private fun ResourceLoader.Tor.Exec.start(args: List<String>): AutoCloseable {
    val process = process(TestBinder) { tor, configureEnv ->
        Process.Builder(command = tor.path)
            .args(args)
            .environment("HOME", resourceDir.path)
            .environment(configureEnv)
            .destroySignal(Signal.SIGTERM)
            .stdin(Stdio.Null)
            .stdout(Stdio.Inherit)
            .stderr(Stdio.Inherit)
    }.spawn()

    return object : AutoCloseable {
        override fun close() { process.destroy() }
    }
}

private fun ResourceLoader.Tor.NoExec.start(args: List<String>): AutoCloseable {
    return withApi(TestBinder) {

        torRunMain(args)

        object : AutoCloseable {
            override fun close() {
                terminateAndAwaitResult()
            }
        }
    }
}
