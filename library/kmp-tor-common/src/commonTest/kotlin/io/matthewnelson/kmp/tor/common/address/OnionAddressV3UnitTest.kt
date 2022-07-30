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
package io.matthewnelson.kmp.tor.common.address

import kotlin.test.Test
import kotlin.test.assertNotNull

class OnionAddressV3UnitTest {

    companion object {
        const val VALID_ONION_ADDRESS = "6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd"
    }

    @Test
    fun givenValidAddressString_whenStoredAsOnionAddress_doesNotThrow() {
        OnionAddressV3(VALID_ONION_ADDRESS)
    }

    @Test
    fun givenInvalidAddressString_whenStoredAsOnionAddress_throwsException() {
        val msg = "invalid onion address did not throw exception as expected"

        try {
            OnionAddressV3("")
            error(msg)
        } catch (_: Exception) {}

        try {
            OnionAddressV3("$VALID_ONION_ADDRESS.")
            error(msg)
        } catch (_: Exception) {}

        try {
            OnionAddressV3(VALID_ONION_ADDRESS.dropLast(1))
            error(msg)
        } catch (_: Exception) {}
    }

    @Test
    fun givenV3OnionAddress_whenDecoded_returnsSuccess() {
        OnionAddressV3(VALID_ONION_ADDRESS).decode()
    }

    @Test
    fun givenUrlString_whenAddressIsValidOnionAddress_returnsNotNull() {
        val url = "http://$VALID_ONION_ADDRESS.onion:1234/some/path"
        val actual = OnionAddressV3.fromStringOrNull(url)
        assertNotNull(actual)
    }

}
