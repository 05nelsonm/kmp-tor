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
package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.runtime.core.internal.containsNon0Byte
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PrivateKey.Companion.toX25519PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.X25519.PublicKey.Companion.toX25519PublicKey
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class X25519UnitTest: AuthKeyBaseUnitTest<X25519.PublicKey, X25519.PrivateKey>(
    keyType = X25519,
    expectedAlgorithm = "x25519",
) {

    override val publicKey: X25519.PublicKey = PUBLIC_KEY_B64.toX25519PublicKey()
    override val privateKey: X25519.PrivateKey = PRIVATE_KEY_B64.toX25519PrivateKey()

    @Test
    fun givenX25519PublicKey_whenEncodedString_thenToKeyIsSuccessful() {
        val expected = PUBLIC_KEY_B16.decodeToByteArray(Base16)

        assertContentEquals(expected, PUBLIC_KEY_B16.toX25519PublicKey().encoded())
        assertContentEquals(expected, PUBLIC_KEY_B32.toX25519PublicKey().encoded())
        assertContentEquals(expected, PUBLIC_KEY_B64.toX25519PublicKey().encoded())
    }

    @Test
    fun givenX25519PrivateKey_whenEncodedString_thenToKeyIsSuccessful() {
        val expected = PRIVATE_KEY_B16.decodeToByteArray(Base16)

        assertContentEquals(expected, PRIVATE_KEY_B16.toX25519PrivateKey().encoded())
        assertContentEquals(expected, PRIVATE_KEY_B32.toX25519PrivateKey().encoded())
        assertContentEquals(expected, PRIVATE_KEY_B64.toX25519PrivateKey().encoded())
    }

    @Test
    fun givenX25519PublicKey_whenBytes_thenToKeyIsSuccessful() {
        PUBLIC_KEY_B64.decodeToByteArray(Base64.Default).toX25519PublicKey()
    }

    @Test
    fun givenX25519PrivateKey_whenBytes_thenToKeyIsSuccessful() {
        PRIVATE_KEY_B64.decodeToByteArray(Base64.Default).toX25519PrivateKey()
    }

    @Test
    fun givenInvalidInput_whenToPublicKey_thenThrowsException() {
        val blank = PUBLIC_KEY_B16.toX25519PublicKey().encoded().apply { fill(0) }
        assertInvalidKey(listOf("Key is blank")) { blank.toX25519PublicKey() }
        assertInvalidKey(listOf("Key is blank")) { blank.encodeToString(Base16()).toX25519PublicKey() }
        assertInvalidKey(listOf("Tried base 16/32/64")) { PUBLIC_KEY_B16.dropLast(2).toX25519PublicKey() }
        assertInvalidKey(listOf("Invalid array size")) { PUBLIC_KEY_B16.dropLast(2).decodeToByteArray(Base16).toX25519PublicKey() }
    }

    @Test
    fun givenInvalidInput_whenToPrivateKey_thenReturnsNull() {
        val blank = PRIVATE_KEY_B16.toX25519PrivateKey().encoded().apply { fill(0) }
        assertInvalidKey(listOf("Key is blank")) { blank.toX25519PrivateKey() }
        assertInvalidKey(listOf("Key is blank")) { blank.encodeToString(Base16()).toX25519PrivateKey() }
        assertInvalidKey(listOf("Tried base 16/32/64")) { PRIVATE_KEY_B16.dropLast(2).toX25519PrivateKey() }
        assertInvalidKey(listOf("Invalid array size")) { PRIVATE_KEY_B16.dropLast(2).decodeToByteArray(Base16).toX25519PrivateKey() }
    }

    @Test
    fun givenSeed_whenGenerateKeyPair_thenFunctionsAsExected() {
        val seed = ByteArray(X25519.PrivateKey.BYTE_SIZE) { it.toByte() }
        val kp = X25519.generateKeyPair(seed, offset = 0, clear = true)
        assertEquals("R5AMLLNWR4SWESXFWIKOU5T2N3EU3AU5HV5V4GWRXJXT4IJYFBPQ", kp.first.base32())
        assertFalse(seed.containsNon0Byte(X25519.PrivateKey.BYTE_SIZE))
    }

    companion object {
        // Do not use outside testing purposes. These are exposed keys.
        const val PRIVATE_KEY_B16 = "70663288D322A66BBEB1D6C60FE0A3207A8C99643A4FD321150A8CBECB476B63"
        const val PRIVATE_KEY_B32 = "OBTDFCGTEKTGXPVR23DA7YFDEB5IZGLEHJH5GIIVBKGL5S2HNNRQ"
        const val PRIVATE_KEY_B64 = "cGYyiNMipmu+sdbGD+CjIHqMmWQ6T9MhFQqMvstHa2M"

        const val PUBLIC_KEY_B16 = "5D5F7F58977822131E5CE1B3F351E0F2C3546BB5C7622269BECC731BA4490545"
        const val PUBLIC_KEY_B32 = "LVPX6WEXPARBGHS44GZ7GUPA6LBVI25VY5RCE2N6ZRZRXJCJAVCQ"
        const val PUBLIC_KEY_B64 = "XV9/WJd4IhMeXOGz81Hg8sNUa7XHYiJpvsxzG6RJBUU"
    }
}
