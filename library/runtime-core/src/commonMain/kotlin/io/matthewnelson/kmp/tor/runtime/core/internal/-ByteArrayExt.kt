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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

@Throws(IndexOutOfBoundsException::class)
internal inline fun ByteArray.containsNon0Byte(limit: Int): Boolean {
    var z = 0
    var i = 0
    while (i < limit) {
        z += if (this[i++] == 0.toByte()) 1 else 0
    }
    return z < limit
}
