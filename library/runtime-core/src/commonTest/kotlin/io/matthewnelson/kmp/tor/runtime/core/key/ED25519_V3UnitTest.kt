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
@file:Suppress("ClassName")

package io.matthewnelson.kmp.tor.runtime.core.key

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.runtime.core.net.OnionAddressV3UnitTest
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKey
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKeyOrNull
import io.matthewnelson.kmp.tor.runtime.core.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKey
import kotlin.test.*

class ED25519_V3UnitTest: AddressKeyBaseUnitTest<ED25519_V3.PublicKey, ED25519_V3.PrivateKey>(
    keyType = ED25519_V3,
    expectedAlgorithm = "ED25519-V3",
) {

    override val publicKey: ED25519_V3.PublicKey = OnionAddressV3UnitTest.ONION_ADDRESS_V3.toED25519_V3PublicKey()
    override val privateKey: ED25519_V3.PrivateKey = PRIVATE_KEY_B64.toED25519_V3PrivateKey()

    @Test
    fun givenOnionAddress_whenEncodedString_thenToKeyIsSuccessful() {
        // Should be first 32 bytes of an onion address
        val expected = ONION_ADDRESS_B16.decodeToByteArray(Base16).copyOf(ED25519_V3.PublicKey.BYTE_SIZE)

        assertContentEquals(expected, ONION_ADDRESS_B16.toED25519_V3PublicKey().encoded())
        assertContentEquals(expected, ONION_ADDRESS_B32.toED25519_V3PublicKey().encoded())
        assertContentEquals(expected, ONION_ADDRESS_B64.toED25519_V3PublicKey().encoded())
    }

    @Test
    fun givenED25519V3PrivateKey_whenEncodedString_thenToKeyIsSuccessful() {
        val expected = PRIVATE_KEY_B16.decodeToByteArray(Base16)

        assertContentEquals(expected, PRIVATE_KEY_B16.toED25519_V3PrivateKey().encoded())
        assertContentEquals(expected, PRIVATE_KEY_B32.toED25519_V3PrivateKey().encoded())
        assertContentEquals(expected, PRIVATE_KEY_B64.toED25519_V3PrivateKey().encoded())
    }

    @Test
    fun givenOnionAddress_whenBytes_thenToKeyIsSuccessful() {
        ONION_ADDRESS_B64.decodeToByteArray(Base64.Default).toED25519_V3PublicKey()
    }

    @Test
    fun givenED25519PublicKey_whenToKey_thenComputesOnionAddressChecksum() {
        val expected = ONION_ADDRESS_B16.toED25519_V3PublicKey().address()
        val key = ONION_ADDRESS_B16.dropLast(2 * 3) // 3 bytes (2 checksum, 1 version byte)
        assertEquals(expected, key.toED25519_V3PublicKey().address())
        assertEquals(expected, key.decodeToByteArray(Base16).toED25519_V3PublicKey().address())
    }

    @Test
    fun givenED25519V3PrivateKey_whenBytes_thenToKeyIsSuccessful() {
        PRIVATE_KEY_B64.decodeToByteArray(Base64.Default).toED25519_V3PrivateKey()
    }

    @Test
    fun givenInvalidInput_whenToPublicKey_thenThrowsException() {
        // Check minimum length check is happening
        assertInvalidKey(listOf("Invalid length", "vs min[43]")) { "".toED25519_V3PublicKey() }

        // Check to see if it dropped into url decoding right away b/c presence of colon
        assertInvalidKey(listOf("v3 Onion addresses are 56 characters")) {
            "ws://${ONION_ADDRESS_B32.dropLast(1)}".toED25519_V3PublicKey()
        }
        assertInvalidKey(listOf("v3 Onion addresses are 56 characters")) {
            "wss://${ONION_ADDRESS_B32.dropLast(1)}".toED25519_V3PublicKey()
        }
        assertInvalidKey(listOf("v3 Onion addresses are 56 characters")) {
            "http://${ONION_ADDRESS_B32.dropLast(1)}".toED25519_V3PublicKey()
        }
        assertInvalidKey(listOf("v3 Onion addresses are 56 characters")) {
            "https://${ONION_ADDRESS_B32.dropLast(1)}".toED25519_V3PublicKey()
        }

        assertInvalidKey(listOf("Invalid array size")) {
            ONION_ADDRESS_B16.dropLast(2).decodeToByteArray(Base16).toED25519_V3PublicKey()
        }

        assertInvalidKey(listOf("Key is blank")) {
            ByteArray(ED25519_V3.PublicKey.BYTE_SIZE).toED25519_V3PublicKey()
        }
        assertInvalidKey(listOf("Key is blank")) {
            ByteArray(ED25519_V3.PublicKey.BYTE_SIZE).encodeToString(Base16()).toED25519_V3PublicKey()
        }

        // 35 byte array tried direct conversion to OnionAddress.V3
        assertInvalidKey(listOf("Invalid version byte")) {
            val b = ONION_ADDRESS_B16.decodeToByteArray(Base16)
            b[b.lastIndex]++
            b.toED25519_V3PublicKey()
        }
    }

    @Test
    fun givenInvalidInput_whenToPrivateKey_thenReturnsNull() {
        val blank = PRIVATE_KEY_B16.toED25519_V3PrivateKey().encoded().apply { fill(0) }
        assertInvalidKey(listOf("Key is blank")) { blank.toED25519_V3PrivateKey() }
        assertInvalidKey(listOf("Key is blank")){ blank.encodeToString(Base16()).toED25519_V3PrivateKey() }
        assertNull(PRIVATE_KEY_B16.dropLast(2).toED25519_V3PrivateKeyOrNull())
        assertNull(PRIVATE_KEY_B16.dropLast(2).decodeToByteArray(Base16).toED25519_V3PrivateKeyOrNull())
    }

    companion object {
        const val PRIVATE_KEY_B16 = "18968BE7C1BA78AA14501A07700EF59C0694B75E788D8987FDE5B221E60690431EDDED6CB13481DB52E9F7E5DB65E1ED375A14EB91D676ACF926370C9F6DD5ED"
        const val PRIVATE_KEY_B32 = "DCLIXZ6BXJ4KUFCQDIDXADXVTQDJJN26PCGYTB754WZCDZQGSBBR5XPNNSYTJAO3KLU7PZO3MXQ62N22CTVZDVTWVT4SMNYMT5W5L3I"
        const val PRIVATE_KEY_B64 = "GJaL58G6eKoUUBoHcA71nAaUt154jYmH/eWyIeYGkEMe3e1ssTSB21Lp9+XbZeHtN1oU65HWdqz5JjcMn23V7Q"

        const val ONION_ADDRESS_B16 = "F62F3905EDD2BE4BDD008882824F659478A7311292D0E4DD2BAAFE8C4C028B6B9D5303"
        const val ONION_ADDRESS_B32 = OnionAddressV3UnitTest.ONION_ADDRESS_V3
        const val ONION_ADDRESS_B64 = "9i85Be3SvkvdAIiCgk9llHinMRKS0OTdK6r+jEwCi2udUwM"
    }
}
