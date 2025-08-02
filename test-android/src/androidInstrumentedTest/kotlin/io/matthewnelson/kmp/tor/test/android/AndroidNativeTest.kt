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
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.file.absoluteFile2
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Stdio
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class AndroidNativeTest {

    private val ctx = ApplicationProvider.getApplicationContext<Application>().applicationContext
    private val nativeLibraryDir = ctx.applicationInfo.nativeLibraryDir.toFile().absoluteFile2()

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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            println("Skipping...")
            return
        }

        val out = Process.Builder(executable = nativeLibraryDir.resolve(libName))
            .stdin(Stdio.Null)
            .output {
                maxBuffer = Int.MAX_VALUE / 2
                timeoutMillis = timeout.inWholeMilliseconds.toInt()
            }

        if (out.processInfo.exitCode == 0) return

        System.err.println(out.toString())
        System.err.println(out.stdout)
        System.err.println(out.stderr)
        System.err.println("--- ENVIRONMENT ---")
        out.processInfo.environment.forEach { (key, value) -> System.err.println("$key=$value") }
        throw AssertionError("Process.exitCode[${out.processInfo.exitCode}] != 0")
    }
}
