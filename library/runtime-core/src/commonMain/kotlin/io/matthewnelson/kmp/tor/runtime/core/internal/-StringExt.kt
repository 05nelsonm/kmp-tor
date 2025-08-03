/*
 * Copyright (c) 2022 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("NOTHING_TO_INLINE")

package io.matthewnelson.kmp.tor.runtime.core.internal

import io.matthewnelson.encoding.core.Decoder
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull

internal inline fun String.stripBaseEncoding(): String {
    var limit = length

    // Disregard padding and/or whitespace from end of string
    while (limit > 0) {
        val c = this[limit - 1]
        if (c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t') {
            break
        }
        limit--
    }

    return this.substring(0, limit).trimStart()
}

internal inline fun String.tryDecodeOrNull(
    expectedSize: Int,
    decoders: List<Decoder<*>>,
): ByteArray? {
    decoders.forEach { decoder ->
        val bytes = decodeToByteArrayOrNull(decoder) ?: return@forEach
        if (bytes.size == expectedSize) {
            return bytes
        } else {
            bytes.fill(0)
        }
    }

    return null
}

internal inline fun String.isSingleLine(): Boolean {
    val i = indexOfFirst { c -> c == '\r' || c == '\n' }
    return i == -1
}
