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

import io.matthewnelson.encoding.base64.Base64
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClientNameUnitTest {

    @Test
    fun givenStringWithSpaces_whenStoringAsClientName_returnsNull() {
        val actual = ClientName.fromStringOrNull("a b c")
        assertNull(actual)
    }

    @Test
    fun givenStringWithMultipleLines_whenStoringAsClientName_returnsNull() {
        val actual = ClientName.fromStringOrNull("a\nbc")
        assertNull(actual)
    }

    @Test
    fun givenEmptyString_whenStoringAsClientName_returnsNull() {
        val actual = ClientName.fromStringOrNull("")
        assertNull(actual)
    }

    @Test
    fun givenStringGreaterThan16Chars_whenStoringAsClientName_returnsNull() {
        val sb = StringBuilder()
        repeat(16) {
            sb.append(Base64.UrlSafe.CHARS[it])
        }

        val actual16 = ClientName.fromStringOrNull(sb.toString())
        assertNotNull(actual16)

        val actual17 = ClientName.fromStringOrNull(sb.append("s").toString())
        assertNull(actual17)
    }
}
