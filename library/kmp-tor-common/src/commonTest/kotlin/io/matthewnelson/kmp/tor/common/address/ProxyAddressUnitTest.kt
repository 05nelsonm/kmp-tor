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
import kotlin.test.assertEquals

class ProxyAddressUnitTest {

    @Test
    fun givenProxyAddress_whenIPAddressV4_toStringPrintsCorrectly() {
        val expected = "127.0.0.1:9050"
        val actual = ProxyAddress.fromString(expected).toString()
        assertEquals(expected, actual)
    }

    @Test
    fun givenProxyAddress_whenIPAddressV6_toStringPrintsCorrectly() {
        val expected = "[35f4:c60a:8296:4c90:79ad:3939:69d9:ba10]:9050"
        val actual = ProxyAddress.fromString(expected).toString()
        assertEquals(expected, actual)
    }
}
