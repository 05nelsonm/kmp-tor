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

class OnionAddressV3UnitTest {

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
    fun givenV3OnionAddress_whenDecoded_doesNotThrowException() {
        OnionAddressV3(VALID_ONION_ADDRESS).decode()
    }

    @Test
    fun givenUrls_whenFromString_returnsOnionAddress() {
        for (url in FROM_STRING_TEST_DATA) {
            OnionAddressV3.fromString(url)
        }
    }

    companion object {
        const val VALID_ONION_ADDRESS = "6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd"

        val FROM_STRING_TEST_DATA = listOf(
            VALID_ONION_ADDRESS,
            VALID_ONION_ADDRESS.uppercase(),
            "$VALID_ONION_ADDRESS.onion",
            "some.subdomain.$VALID_ONION_ADDRESS",
            "some.subdomain.$VALID_ONION_ADDRESS.onion",
            "http://$VALID_ONION_ADDRESS.onion",
            "http://$VALID_ONION_ADDRESS.onion/path",
            "http://$VALID_ONION_ADDRESS.onion:8080/some/path/#some-fragment",
            "http://subdomain.$VALID_ONION_ADDRESS.onion:8080/some/path",
            "http://sub.domain.$VALID_ONION_ADDRESS.onion:8080/some/path",
            "http://username@$VALID_ONION_ADDRESS.onion",
            "http://username@$VALID_ONION_ADDRESS.onion:8080",
            "http://username@$VALID_ONION_ADDRESS.onion:8080/some/path",
            "http://username:password@$VALID_ONION_ADDRESS.onion",
            "http://username:password@$VALID_ONION_ADDRESS.onion:8080",
            "http://username:password@$VALID_ONION_ADDRESS.onion:8080/some/path",
            "http://some.sub.domain.$VALID_ONION_ADDRESS.onion:8080/some/path",
            "http://username@some.sub.domain.$VALID_ONION_ADDRESS.onion:8080/some/path",
            "http://username:password@some.sub.domain.$VALID_ONION_ADDRESS.onion:8080/some/path",
        )
    }
}
