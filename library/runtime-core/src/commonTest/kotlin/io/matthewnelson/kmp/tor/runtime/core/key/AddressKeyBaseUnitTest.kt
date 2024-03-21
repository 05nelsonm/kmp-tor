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
package io.matthewnelson.kmp.tor.runtime.core.key

import kotlin.test.Test
import kotlin.test.assertEquals

abstract class AddressKeyBaseUnitTest<T: AddressKey.Public, V: AddressKey.Private>(
    protected val keyType: KeyType.Address<T, V>,
    expectedAlgorithm: String,
): KeyBaseUnitTest(expectedAlgorithm) {

    abstract override val publicKey: T
    abstract override val privateKey: V

    @Test
    fun givenAddressKey_whenAlgorithm_thenIsAsExpected() {
        assertEquals(expectedAlgorithm, keyType.algorithm())
    }

    // TODO: Factory function tests
}
