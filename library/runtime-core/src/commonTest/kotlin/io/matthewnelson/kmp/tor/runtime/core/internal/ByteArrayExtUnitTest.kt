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
package io.matthewnelson.kmp.tor.runtime.core.internal

import org.kotlincrypto.error.GeneralSecurityException
import kotlin.test.*

class ByteArrayExtUnitTest {

    @Test
    fun givenAll0Bytes_whenContainsNon0Byte_thenReturnsFalse() {
        val b = ByteArray(35) { 0 }
        assertFalse(b.containsNon0Byte(len = b.size))
    }

    @Test
    fun givenNotAll0Bytes_whenContainsNon0Byte_thenReturnsFalse() {
        val b = ByteArray(35) { 0 }
        b[0] = 5.toByte()
        assertTrue(b.containsNon0Byte(len = b.size))
        assertFalse(b.containsNon0Byte(offset = 1, len = b.size - 1))
    }

    @Test
    fun givenSingleCryptoRand_whenOffsetNegative_thenThrowsException() {
        assertFailsWith<IndexOutOfBoundsException> {
            ByteArray(1) { it.toByte() }.asSingleCryptoRand(offset = -1, len = 1, clear = true)
        }
    }

    @Test
    fun givenSingleCryptoRand_whenLenExceedsAvailable_thenThrowsException() {
        assertFailsWith<IndexOutOfBoundsException> {
            ByteArray(1) { it.toByte() }.asSingleCryptoRand(offset = 0, len = 2, clear = true)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            ByteArray(2) { it.toByte() }.asSingleCryptoRand(offset = 1, len = 2, clear = true)
        }
    }

    @Test
    fun givenSingleCryptoRand_whenAllZeroBytes_thenThrowsException() {
        assertFailsWith<GeneralSecurityException> {
            ByteArray(20).asSingleCryptoRand(offset = 0, len = 1, clear = true)
        }
    }

    @Test
    fun givenSingleCryptoRand_whenBufSizeNotEqualLen_thenThrowsException() {
        val random = ByteArray(2) { it.toByte() }.asSingleCryptoRand(offset = 0, len = 2, clear = true)
        assertFailsWith<IllegalArgumentException> { random.nextBytes(ByteArray(1)) }
    }

    @Test
    fun givenSingleCryptoRand_whenAlreadyConsumed_thenThrowsException() {
        val random = ByteArray(2) { it.toByte() }.asSingleCryptoRand(offset = 0, len = 2, clear = true)
        random.nextBytes(ByteArray(2))
        assertFailsWith<IllegalStateException> { random.nextBytes(ByteArray(2)) }
    }

    @Test
    fun givenSingleCryptoRand_whenClearTrue_thenIndicesAreCleared() {
        val source = ByteArray(6) { (it + 1).toByte() }
        val random = source.asSingleCryptoRand(offset = 2, len = 2, clear = true)
        val actual = random.nextBytes(ByteArray(2))
        assertContentEquals(byteArrayOf(1, 2, 0, 0, 5, 6), source)
        assertEquals(3, actual[0])
        assertEquals(4, actual[1])
    }
}
