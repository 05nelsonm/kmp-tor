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
package io.matthewnelson.kmp.tor.runtime.test

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysDirSep
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.process.Process

@Throws(IOException::class)
internal actual fun File.recursivelyDelete() {
    // lmao...
    val out = when {
        SysDirSep == '\\' -> Process.Builder(command = "rmdir").args("/s")
        // Use absolute path so macOS rm is utilized
        IsDarwinSimulator -> Process.Builder(command = "/bin/rm").args("-r")
        else -> Process.Builder(command = "rm").args("-r")
    }.args(path).createOutput { timeoutMillis = 500 }

    if (out.processInfo.exitCode == 0 && out.stderr.isBlank()) return

    throw IOException(out.toString() + "\n\n" + out.stdout + "\n\n" + out.stderr)
}
