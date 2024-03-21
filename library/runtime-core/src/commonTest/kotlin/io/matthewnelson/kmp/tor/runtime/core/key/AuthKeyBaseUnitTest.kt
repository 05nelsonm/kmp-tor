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

import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress.Companion.toOnionAddress
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddressV3UnitTest.Companion.ONION_ADDRESS_V3
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class AuthKeyBaseUnitTest<T: AuthKey.Public, V: AuthKey.Private>(
    protected val keyType: KeyType.Auth<T, V>,
    expectedAlgorithm: String,
): KeyBaseUnitTest(expectedAlgorithm) {

    abstract override val publicKey: T
    abstract override val privateKey: V

    @Test
    fun givenAuthKey_whenAlgorithm_thenIsAsExpected() {
        assertEquals(expectedAlgorithm, keyType.algorithm())
    }

    @Test
    fun givenPublicKey_whenBase32Descriptor_thenIsAsExpected() {
        val expected = "descriptor:" + expectedAlgorithm + ":" + publicKey.base32()
        assertEquals(expected, publicKey.descriptorBase32())
    }

    @Test
    fun givenPublicKey_whenBase64Descriptor_thenIsAsExpected() {
        val expected = "descriptor:" + expectedAlgorithm + ":" + publicKey.base64()
        assertEquals(expected, publicKey.descriptorBase64())
    }

    @Test
    fun givenPrivateKey_whenBase32Descriptor_thenIsAsExpected() {
        val expected = ONION_ADDRESS_V3 + ":" + expectedAlgorithm + ":" + privateKey.base32()
        assertEquals(expected, privateKey.descriptorBase32(ONION_ADDRESS_V3.toOnionAddress()))
    }

    @Test
    fun givenPrivateKey_whenBase64Descriptor_thenIsAsExpected() {
        val expected = ONION_ADDRESS_V3 + ":" + expectedAlgorithm + ":" + privateKey.base64()
        assertEquals(expected, privateKey.descriptorBase64(ONION_ADDRESS_V3.toOnionAddress()))
    }

    // TODO: Factory function tests
}
