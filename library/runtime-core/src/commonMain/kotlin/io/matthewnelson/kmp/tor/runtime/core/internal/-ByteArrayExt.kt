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

import org.kotlincrypto.error.GeneralSecurityException
import org.kotlincrypto.random.CryptoRand
import org.kotlincrypto.random.DelicateCryptoRandApi

@Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
internal inline fun ByteArray.containsNon0Byte(len: Int): Boolean = containsNon0Byte(offset = 0, len)

@Throws(IllegalArgumentException::class, IndexOutOfBoundsException::class)
internal inline fun ByteArray.containsNon0Byte(offset: Int, len: Int): Boolean {
    if (offset < 0) throw IndexOutOfBoundsException("offset[$offset] < 0")
    if (len <= 0) throw IllegalArgumentException("len[$len] <= 0")
    if (offset + len > size) throw IndexOutOfBoundsException("offset[$offset] + len[$len] > size[$size]")

    var z = 0
    var i = 0
    while (i < len) {
        z += if (this[offset + i++] == 0.toByte()) 1 else 0
    }
    return z < len
}

@Throws(GeneralSecurityException::class, IllegalArgumentException::class, IndexOutOfBoundsException::class)
internal inline fun ByteArray.asSingleCryptoRand(offset: Int, len: Int, clear: Boolean): CryptoRand {
    if (!containsNon0Byte(offset, len)) throw GeneralSecurityException("array indices are blank (all 0 bytes)")

    val source = this
    var consumed = false

    @OptIn(DelicateCryptoRandApi::class)
    return object : CryptoRand() {
        override fun nextBytes(buf: ByteArray): ByteArray {
            check(!consumed) { "bytes have already been consumed" }
            require(buf.size == len) { "buf.size[${buf.size}] != len[$len]" }
            consumed = true
            for (i in 0..<len) {
                buf[i] = source[i + offset]
                if (!clear) continue
                source[i + offset] = 0
            }
            return buf
        }
    }
}
