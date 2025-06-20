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
        val process = Process.Builder(nativeLibraryDir.resolve(libName))
            .stdin(Stdio.Null)
            .useSpawn { p ->
                // Cannot use Stdio.Inherit. Must use System.out/err so that logcat picks it up
                p.stdoutFeed { line ->
                    println(line ?: "STDOUT: END")
                }.stderrFeed { line ->
                    System.err.println(line ?: "STDERR: END")
                }.waitFor(duration = timeout)
                p
            }

        if (process.waitFor() == 0) return

        buildString {
            appendLine("--- ENVIRONMENT ---")
            process.environment.forEach { (key, value) -> appendLine("$key=$value") }
        }.let { System.err.println(it) }
        throw AssertionError(process.toString())
    }
}
