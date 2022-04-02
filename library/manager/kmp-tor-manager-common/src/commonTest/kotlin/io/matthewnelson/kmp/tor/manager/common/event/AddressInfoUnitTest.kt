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
package io.matthewnelson.kmp.tor.manager.common.event

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.AddressInfo
import kotlin.test.*

class AddressInfoUnitTest {

    companion object {
        const val ADDRESS = "127.0.0.1"
        const val PORT = "48494"
    }
    private val info = AddressInfo(dns = setOf("$ADDRESS:$PORT"))

    @Test
    fun givenPortInfo_whenNoConstructorArgs_portsAreNull() {
        assertTrue(AddressInfo.NULL_VALUES.isNull)
    }

    @Test
    fun givenNonNullPortInfo_whenSplit_returnsSuccessful() {
        val result = info.splitDns()
        result.onFailure { ex ->
            fail(cause = ex)
        }
        result.onSuccess { set ->
            assertEquals(ADDRESS, set.first().address)
            assertEquals(PORT.toInt(), set.first().port.value)
        }
    }

    @Test
    fun givenNullPortInfo_whenSplit_returnsNull() {
        assertNull(info.http)
        val result = info.splitHttp()
        assertTrue(result.isFailure)
    }
}
