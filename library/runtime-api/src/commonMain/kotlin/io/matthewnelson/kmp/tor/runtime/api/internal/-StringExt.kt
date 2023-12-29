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
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.api.internal

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base32.Base32
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArrayOrNull

@Suppress("NOTHING_TO_INLINE")
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

@Suppress("NOTHING_TO_INLINE")
internal inline fun String.findHostnameAndPortFromURL(): String {
    return substringAfter("://") // scheme
        .substringAfter('@') // username:password
        .substringBefore('/') // path
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun String.tryDecodeOrNull(
    expectedSize: Int,
    decoders: List<Decoder<*>> = listOf(
        Base16,
        Base32.Default,
        Base64.Default,
    )
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
