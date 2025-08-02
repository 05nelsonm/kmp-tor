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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.absoluteFile2
import io.matthewnelson.kmp.file.normalize
import io.matthewnelson.kmp.file.path

@Throws(IOException::class)
internal inline fun File.absoluteNormalizedFile(): File = absoluteFile2().normalize()

@Throws(IOException::class, UnsupportedOperationException::class)
internal fun File.toUnixSocketPath(): String {
    UnixSocketsNotSupportedMessage?.let { throw UnsupportedOperationException(it) }

    val path = absoluteNormalizedFile().path

    // FreeBSD -> MAX 102 chars
    // Darwin  -> MAX 102 chars
    // Linux   -> MAX 106 chars
    // Windows -> MAX 106 chars
    // else    -> MAX 106 chars
    if (path.length > (AFUnixPathBufSize - 2)) {
        throw UnsupportedOperationException("path too long")
    }

    if (!path.isSingleLine()) {
        throw UnsupportedOperationException("path cannot be multiple lines")
    }

    return "unix:\"${path}\""
}
