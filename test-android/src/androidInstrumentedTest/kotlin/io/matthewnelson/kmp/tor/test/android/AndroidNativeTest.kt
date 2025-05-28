/*
 * Copyright (c) 2025 Matthew Nelson
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
package io.matthewnelson.kmp.tor.test.android

import android.app.Application
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AndroidNativeTest {

    private val ctx = ApplicationProvider.getApplicationContext<Application>().applicationContext
    private val nativeLibraryDir = ctx.applicationInfo.nativeLibraryDir.toFile().absoluteFile

    @Test
    fun givenAndroidNative_whenExecuteRuntimeTestBinary_thenIsSuccessful() {
        run(libName = "libTestRuntime.so", timeout = 7.minutes)
    }

    @Test
    fun givenAndroidNative_whenExecuteRuntimeCoreTestBinary_thenIsSuccessful() {
        run(libName = "libTestRuntimeCore.so", timeout = 3.minutes)
    }

    @Test
    fun givenAndroidNative_whenExecuteRuntimeCtrlTestBinary_thenIsSuccessful() {
        run(libName = "libTestRuntimeCtrl.so", timeout = 5.minutes)
    }

    private fun run(libName: String, timeout: Duration) {
        val envBeforeOs = osEnvironment()
        val envBeforeProcess = Process.Current.environment()

        buildString {
            appendEnvironment(envBeforeProcess, "Process.Current")
            appendEnvironment(envBeforeOs, "Os.environ")
        }.let { println(it) }

        val process = Process.Builder(nativeLibraryDir.resolve(libName))
            .stdin(Stdio.Null)
            .spawn { p ->
                p.stdoutFeed { line ->
                    println(line ?: "STDOUT: END")
                }.stderrFeed { line ->
                    System.err.println(line ?: "STDERR: END")
                }.waitFor(duration = timeout)
                p
            }

        println("RUN_LENGTH[${process.startTime.elapsedNow().inWholeSeconds}s]")
        if (process.waitFor() == 0) return

        val envNowProcess = Process.Current.environment()
        val envNowOs = osEnvironment()

        val msg = buildString {
            appendLine(process.toString())
            appendLine()
            appendEnvironment(process.environment, "Process - pid=${process.pid()}")
            appendEnvironment(envBeforeProcess, "Process.Current - before test")
            appendEnvironment(envNowProcess, "Process.Current - now")
            appendEnvironment(envBeforeOs, "Os.environ - before test")
            appendEnvironment(envNowOs, "Os.environ - now")
        }
        throw AssertionError(msg)
    }

    private fun StringBuilder.appendEnvironment(env: Map<String, String>, header: String) {
        appendLine("--- ENVIRONMENT[$header] ---")
        env.forEach { (key, value) ->
            append("    ").append(key).append('=').appendLine(value)
        }
        appendLine("------------------------------")
    }

    private fun osEnvironment(): Map<String, String> {
        val env = Os.environ()
        val map = LinkedHashMap<String, String>(env.size, 1.0f)
        env.forEach { kvp ->
            val i = kvp.indexOf('=')
            map[kvp.substring(0, i)] = kvp.substring(i + 1, kvp.length)
        }
        return map
    }
}
