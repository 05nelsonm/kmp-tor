/*
 * Copyright (c) 2021 Matthew Nelson
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
package io.matthewnelson.kmp.tor.common.clientauth

import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3UnitTest
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.internal.TorStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalTorApi::class)
class PrivateKeyUnitTest {

    companion object {
        const val VALID_BASE32_PRIVATE_KEY = "OBTDFCGTEKTGXPVR23DA7YFDEB5IZGLEHJH5GIIVBKGL5S2HNNRQ"
        const val VALID_BASE64_PRIVATE_KEY = "cGYyiNMipmu+sdbGD+CjIHqMmWQ6T9MhFQqMvstHa2M"
        const val VALID_ONION_ADDRESS = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
    }

    @Test
    fun givenValidBase32PrivateKeyString_whenPadded_PrivateKeyFromStringReturnsPrivateKey() {
        val key = OnionClientAuth.PrivateKey.fromString("$VALID_BASE32_PRIVATE_KEY====")
        assertTrue(key is OnionClientAuthPrivateKey_B32_X25519)
    }

    @Test
    fun givenValidBase32PrivateKeyString_whenNotPadded_PrivateKeyFromStringReturnsPrivateKey() {
        val key = OnionClientAuth.PrivateKey.fromString(VALID_BASE32_PRIVATE_KEY)
        assertTrue(key is OnionClientAuthPrivateKey_B32_X25519)
    }

    @Test
    fun givenValidBase64PrivateKeyString_whenPadded_PrivateKeyFromStringReturnsPrivateKey() {
        val key = OnionClientAuth.PrivateKey.fromString("$VALID_BASE64_PRIVATE_KEY=")
        assertTrue(key is OnionClientAuthPrivateKey_B64_X25519)
    }

    @Test
    fun givenValidBase64PrivateKeyString_whenNotPadded_PrivateKeyFromStringReturnsPrivateKey() {
        val key = OnionClientAuth.PrivateKey.fromString(VALID_BASE64_PRIVATE_KEY)
        assertTrue(key is OnionClientAuthPrivateKey_B64_X25519)
    }

    @Test
    fun givenInvalidBase32PrivateKeyString_whenPadded_PrivateKeyFromStringOrNullReturnsNull() {
        val key = OnionClientAuth.PrivateKey.fromStringOrNull(VALID_BASE32_PRIVATE_KEY.dropLast(1))
        assertNull(key)
    }

    @Test
    fun givenInvalidBase64PrivateKeyString_whenPadded_PrivateKeyFromStringOrNullReturnsNull() {
        val key = OnionClientAuth.PrivateKey.fromStringOrNull(VALID_BASE64_PRIVATE_KEY.dropLast(1))
        assertNull(key)
    }

    @Test
    fun givenPrivateKey_whenToStringCalled_valueIsRemoved() {
        val b32Key = OnionClientAuth.PrivateKey.fromString(VALID_BASE32_PRIVATE_KEY)
        val b64Key = OnionClientAuth.PrivateKey.fromString(VALID_BASE64_PRIVATE_KEY)
        assertPrivateKeyToStringDoesNotContainKey(b32Key)
        assertPrivateKeyToStringDoesNotContainKey(b64Key)
    }

    private fun assertPrivateKeyToStringDoesNotContainKey(key: OnionClientAuth.PrivateKey) {
        assertTrue(!key.toString().contains(key.value))
    }

    @Test
    fun givenBase32PrivateKey_whenBase64withoutPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPrivateKey_B32_X25519(VALID_BASE32_PRIVATE_KEY)
        val b64 = key.base64(padded = false)
        assertEquals(VALID_BASE64_PRIVATE_KEY, b64)
    }

    @Test
    fun givenBase32PrivateKey_whenBase64withPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPrivateKey_B32_X25519(VALID_BASE32_PRIVATE_KEY)
        val b64 = key.base64(padded = true)
        assertEquals(key.decode().encodeToString(TorStrings.base64), b64)
    }

    @Test
    fun givenBase32PrivateKey_whenBase32withoutPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPrivateKey_B32_X25519(VALID_BASE32_PRIVATE_KEY)
        val b32 = key.base32(padded = false)
        assertEquals(VALID_BASE32_PRIVATE_KEY, b32)
    }

    @Test
    fun givenBase32PrivateKey_whenBase32withPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPrivateKey_B32_X25519(VALID_BASE32_PRIVATE_KEY)
        val b32 = key.base32(padded = true)
        assertEquals(key.decode().encodeToString(TorStrings.base32), b32)
    }

    @Test
    fun givenBase64PrivateKey_whenBase32withoutPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPrivateKey_B64_X25519(VALID_BASE64_PRIVATE_KEY)
        val b32 = key.base32(padded = false)
        assertEquals(VALID_BASE32_PRIVATE_KEY, b32)
    }

    @Test
    fun givenBase64PrivateKey_whenBase32withPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPrivateKey_B64_X25519(VALID_BASE64_PRIVATE_KEY)
        val b32 = key.base32(padded = true)
        assertEquals(key.decode().encodeToString(TorStrings.base32), b32)
    }

    @Test
    fun givenBase64PrivateKey_whenBase64withoutPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPrivateKey_B64_X25519(VALID_BASE64_PRIVATE_KEY)
        val b64 = key.base64(padded = false)
        assertEquals(VALID_BASE64_PRIVATE_KEY, b64)
    }

    @Test
    fun givenBase64PrivateKey_whenBase64withPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPrivateKey_B64_X25519(VALID_BASE64_PRIVATE_KEY)
        val b64 = key.base64(padded = true)
        assertEquals(key.decode().encodeToString(TorStrings.base64), b64)
    }

    @Test
    fun givenBase32PrivateKey_whenDescriptorCalled_returnsBase32PrivateKeyDescriptor() {
        val key = OnionClientAuthPrivateKey_B32_X25519(VALID_BASE32_PRIVATE_KEY)
        val descriptor = key.descriptor(OnionAddressV3(VALID_ONION_ADDRESS))
        assertEquals(
            "$VALID_ONION_ADDRESS:${key.keyType}:${VALID_BASE32_PRIVATE_KEY}",
            descriptor
        )
    }

    @Test
    fun givenBase64PrivateKey_whenDescriptorCalled_returnsBase64PrivateKeyDescriptor() {
        val key = OnionClientAuthPrivateKey_B64_X25519(VALID_BASE64_PRIVATE_KEY)
        val descriptor = key.descriptor(OnionAddressV3(VALID_ONION_ADDRESS))
        assertEquals(
            "$VALID_ONION_ADDRESS:${key.keyType}:${VALID_BASE64_PRIVATE_KEY}",
            descriptor
        )
    }
}
