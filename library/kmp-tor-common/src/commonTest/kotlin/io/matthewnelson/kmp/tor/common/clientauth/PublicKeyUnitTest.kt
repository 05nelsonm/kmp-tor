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

import io.matthewnelson.encoding.base32.Base32
import io.matthewnelson.encoding.base64.Base64
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicKeyUnitTest {

    companion object {
        const val VALID_BASE32_PUBLIC_KEY = "LVPX6WEXPARBGHS44GZ7GUPA6LBVI25VY5RCE2N6ZRZRXJCJAVCQ"
        const val VALID_BASE64_PUBLIC_KEY = "XV9/WJd4IhMeXOGz81Hg8sNUa7XHYiJpvsxzG6RJBUU"
    }

    @Test
    fun givenValidBase32PublicKeyString_whenPadded_PublicKeyFromStringReturnsPublicKey() {
        val key = OnionClientAuth.PublicKey.fromString("$VALID_BASE32_PUBLIC_KEY====")
        assertTrue(key is OnionClientAuthPublicKey_B32_X25519)
    }

    @Test
    fun givenValidBase32PublicKeyString_whenNotPadded_PublicKeyFromStringReturnsPublicKey() {
        val key = OnionClientAuth.PublicKey.fromString(VALID_BASE32_PUBLIC_KEY)
        assertTrue(key is OnionClientAuthPublicKey_B32_X25519)
    }

    @Test
    fun givenValidBase64PublicKeyString_whenPadded_PublicKeyFromStringReturnsPublicKey() {
        val key = OnionClientAuth.PublicKey.fromString("$VALID_BASE64_PUBLIC_KEY=")
        assertTrue(key is OnionClientAuthPublicKey_B64_X25519)
    }

    @Test
    fun givenValidBase64PublicKeyString_whenNotPadded_PublicKeyFromStringReturnsPublicKey() {
        val key = OnionClientAuth.PublicKey.fromString(VALID_BASE64_PUBLIC_KEY)
        assertTrue(key is OnionClientAuthPublicKey_B64_X25519)
    }

    @Test
    fun givenInvalidBase32PublicKeyString_whenPadded_PublicKeyFromStringOrNullReturnsNull() {
        val key = OnionClientAuth.PublicKey.fromStringOrNull(VALID_BASE32_PUBLIC_KEY.dropLast(1))
        assertNull(key)
    }

    @Test
    fun givenInvalidBase64PublicKeyString_whenPadded_PublicKeyFromStringOrNullReturnsNull() {
        val key = OnionClientAuth.PublicKey.fromStringOrNull(VALID_BASE64_PUBLIC_KEY.dropLast(1))
        assertNull(key)
    }

    @Test
    fun givenBase32PublicKey_whenBase64withoutPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPublicKey_B32_X25519(VALID_BASE32_PUBLIC_KEY)
        val b64 = key.base64(padded = false)
        assertEquals(VALID_BASE64_PUBLIC_KEY, b64)
    }

    @Test
    fun givenBase32PublicKey_whenBase64withPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPublicKey_B32_X25519(VALID_BASE32_PUBLIC_KEY)
        val b64 = key.base64(padded = true)
        assertEquals(key.decode().encodeToString(Base64.Default), b64)
    }

    @Test
    fun givenBase32PublicKey_whenBase32withoutPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPublicKey_B32_X25519(VALID_BASE32_PUBLIC_KEY)
        val b32 = key.base32(padded = false)
        assertEquals(VALID_BASE32_PUBLIC_KEY, b32)
    }

    @Test
    fun givenBase32PublicKey_whenBase32withPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPublicKey_B32_X25519(VALID_BASE32_PUBLIC_KEY)
        val b32 = key.base32(padded = true)
        assertEquals(key.decode().encodeToString(Base32.Default), b32)
    }

    @Test
    fun givenBase64PublicKey_whenBase32withoutPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPublicKey_B64_X25519(VALID_BASE64_PUBLIC_KEY)
        val b32 = key.base32(padded = false)
        assertEquals(VALID_BASE32_PUBLIC_KEY, b32)
    }

    @Test
    fun givenBase64PublicKey_whenBase32withPaddingCalled_returnsBase32String() {
        val key = OnionClientAuthPublicKey_B64_X25519(VALID_BASE64_PUBLIC_KEY)
        val b32 = key.base32(padded = true)
        assertEquals(key.decode().encodeToString(Base32.Default), b32)
    }

    @Test
    fun givenBase64PublicKey_whenBase64withoutPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPublicKey_B64_X25519(VALID_BASE64_PUBLIC_KEY)
        val b64 = key.base64(padded = false)
        assertEquals(VALID_BASE64_PUBLIC_KEY, b64)
    }

    @Test
    fun givenBase64PublicKey_whenBase64withPaddingCalled_returnsBase64String() {
        val key = OnionClientAuthPublicKey_B64_X25519(VALID_BASE64_PUBLIC_KEY)
        val b64 = key.base64(padded = true)
        assertEquals(key.decode().encodeToString(Base64.Default), b64)
    }

    @Test
    fun givenBase32PublicKey_whenDescriptorCalled_returnsBase32PublicKeyDescriptor() {
        val key = OnionClientAuthPublicKey_B32_X25519(VALID_BASE32_PUBLIC_KEY)
        val descriptor = key.descriptor()
        assertEquals(
            "${OnionClientAuth.PublicKey.DESCRIPTOR}:${key.keyType}:$VALID_BASE32_PUBLIC_KEY",
            descriptor
        )
    }

    @Test
    fun givenBase64PublicKey_whenDescriptorCalled_returnsBase64PublicKeyDescriptor() {
        val key = OnionClientAuthPublicKey_B64_X25519(VALID_BASE64_PUBLIC_KEY)
        val descriptor = key.descriptor()
        assertEquals(
            "${OnionClientAuth.PublicKey.DESCRIPTOR}:${key.keyType}:$VALID_BASE64_PUBLIC_KEY",
            descriptor
        )
    }
}
