/*
 * Copyright (c) 2023 Matthew Nelson
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

package io.matthewnelson.kmp.tor.runtime.api.key

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.kmp.tor.runtime.api.address.OnionAddressV3UnitTest
import io.matthewnelson.kmp.tor.runtime.api.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKey
import io.matthewnelson.kmp.tor.runtime.api.key.ED25519_V3.PrivateKey.Companion.toED25519_V3PrivateKeyOrNull
import io.matthewnelson.kmp.tor.runtime.api.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKey
import io.matthewnelson.kmp.tor.runtime.api.key.ED25519_V3.PublicKey.Companion.toED25519_V3PublicKeyOrNull
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class ED25519_V3UnitTest: AddressKeyBaseUnitTest<ED25519_V3.PublicKey, ED25519_V3.PrivateKey>(
    keyType = ED25519_V3,
    expectedAlgorithm = "ED25519-V3",
) {

    override val publicKey: ED25519_V3.PublicKey = OnionAddressV3UnitTest.ONION_ADDRESS_V3.toED25519_V3PublicKey()
    override val privateKey: ED25519_V3.PrivateKey = PRIVATE_KEY_B64.toED25519_V3PrivateKey()

    @Test
    fun givenED25519V3PublicKey_whenEncodedString_thenToKeyIsSuccessful() {
        val expected = PUBLIC_KEY_B16.decodeToByteArray(Base16)

        assertContentEquals(expected, PUBLIC_KEY_B16.toED25519_V3PublicKey().encoded())
        assertContentEquals(expected, PUBLIC_KEY_B32.toED25519_V3PublicKey().encoded())
        assertContentEquals(expected, PUBLIC_KEY_B64.toED25519_V3PublicKey().encoded())
    }

    @Test
    fun givenED25519V3PrivateKey_whenEncodedString_thenToKeyIsSuccessful() {
        val expected = PRIVATE_KEY_B16.decodeToByteArray(Base16)

        assertContentEquals(expected, PRIVATE_KEY_B16.toED25519_V3PrivateKey().encoded())
        assertContentEquals(expected, PRIVATE_KEY_B32.toED25519_V3PrivateKey().encoded())
        assertContentEquals(expected, PRIVATE_KEY_B64.toED25519_V3PrivateKey().encoded())
    }

    @Test
    fun givenED25519V3PublicKey_whenBytes_thenToKeyIsSuccessful() {
        PUBLIC_KEY_B64.decodeToByteArray(Base64.Default).toED25519_V3PublicKey()
    }

    @Test
    fun givenED25519V3PrivateKey_whenBytes_thenToKeyIsSuccessful() {
        PRIVATE_KEY_B64.decodeToByteArray(Base64.Default).toED25519_V3PrivateKey()
    }

    @Test
    fun givenInvalidInput_whenToPublicKey_thenReturnsNull() {
        assertNull(PUBLIC_KEY_B16.dropLast(2).toED25519_V3PublicKeyOrNull())
        assertNull(PUBLIC_KEY_B16.dropLast(2).decodeToByteArray(Base16).toED25519_V3PublicKeyOrNull())
    }

    @Test
    fun givenInvalidInput_whenToPrivateKey_thenReturnsNull() {
        assertNull(PRIVATE_KEY_B16.dropLast(2).toED25519_V3PrivateKeyOrNull())
        assertNull(PRIVATE_KEY_B16.dropLast(2).decodeToByteArray(Base16).toED25519_V3PrivateKeyOrNull())
    }

    companion object {
        const val PRIVATE_KEY_B16 = "18968BE7C1BA78AA14501A07700EF59C0694B75E788D8987FDE5B221E60690431EDDED6CB13481DB52E9F7E5DB65E1ED375A14EB91D676ACF926370C9F6DD5ED"
        const val PRIVATE_KEY_B32 = "DCLIXZ6BXJ4KUFCQDIDXADXVTQDJJN26PCGYTB754WZCDZQGSBBR5XPNNSYTJAO3KLU7PZO3MXQ62N22CTVZDVTWVT4SMNYMT5W5L3I"
        const val PRIVATE_KEY_B64 = "GJaL58G6eKoUUBoHcA71nAaUt154jYmH/eWyIeYGkEMe3e1ssTSB21Lp9+XbZeHtN1oU65HWdqz5JjcMn23V7Q"

        const val PUBLIC_KEY_B16 = "F62F3905EDD2BE4BDD008882824F659478A7311292D0E4DD2BAAFE8C4C028B6B9D5303"
        const val PUBLIC_KEY_B32 = OnionAddressV3UnitTest.ONION_ADDRESS_V3
        const val PUBLIC_KEY_B64 = "9i85Be3SvkvdAIiCgk9llHinMRKS0OTdK6r+jEwCi2udUwM"
    }
}
