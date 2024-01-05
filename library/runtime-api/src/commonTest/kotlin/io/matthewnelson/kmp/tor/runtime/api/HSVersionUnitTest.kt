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
package io.matthewnelson.kmp.tor.runtime.api

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HSVersionUnitTest {

    @Test
    fun givenVersion_whenNotSupported_thenThrowsException() {
        val lineItem = TorConfig.HiddenServiceVersion.build {
            assertFailsWith<IllegalArgumentException> { HSv(2) }
            assertFailsWith<IllegalArgumentException> { HSv(4) }
        }

        assertNull(lineItem)
    }

    @Test
    fun givenVersion_whenSupported_thenReturnsNonNull() {
        val lineItem = TorConfig.HiddenServiceVersion.build { HSv(3) }
        assertNotNull(lineItem)
    }
}
