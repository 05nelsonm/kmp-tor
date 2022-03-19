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
package io.matthewnelson.kmp.tor.common.address

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateKeyUnitTest {

    companion object {
        const val VALID_PRIVATE_KEY = "GJaL58G6eKoUUBoHcA71nAaUt154jYmH/eWyIeYGkEMe3e1ssTSB21Lp9+XbZeHtN1oU65HWdqz5JjcMn23V7Q"
    }

    @Test
    fun givenValidPrivateKeyString_whenStoredAsOnionAddressPrivateKeyV3ED25519_doesNotThrow() {
        OnionAddressV3PrivateKey_ED25519(VALID_PRIVATE_KEY)
    }

    @Test
    fun givenValidPrivateKeyString_whenPadded_PrivateKeyFromStringReturnsPrivateKey() {
        val key = OnionAddress.PrivateKey.fromString("$VALID_PRIVATE_KEY==")
        assertTrue(key is OnionAddressV3PrivateKey_ED25519)
    }

    @Test
    fun givenValidPrivateKeyString_whenNotPadded_PrivateKeyFromStringReturnsPrivateKey() {
        val key = OnionAddress.PrivateKey.fromString(VALID_PRIVATE_KEY)
        assertTrue(key is OnionAddressV3PrivateKey_ED25519)
    }

    @Test
    fun givenPrivateKey_whenToStringCalled_valueIsRemoved() {
        val key = OnionAddress.PrivateKey.fromString(VALID_PRIVATE_KEY)
        assertFalse(key.toString().contains(key.value))
    }
}
