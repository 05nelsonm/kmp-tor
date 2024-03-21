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
package io.matthewnelson.kmp.tor.runtime.core.address

import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress.V3.Companion.toOnionAddressV3
import io.matthewnelson.kmp.tor.runtime.core.address.OnionAddress.V3.Companion.toOnionAddressV3OrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OnionAddressV3UnitTest {

    @Test
    fun givenValidAddressString_whenToOnionAddressV3_thenIsSuccess() {
        ONION_ADDRESS_V3.toOnionAddressV3()
    }

    @Test
    fun givenInvalidAddress_whenStoredAsOnionAddress_thenThrowsException() {
        val msg = "invalid onion address did not throw exception as expected"

        assertNull("".toOnionAddressV3OrNull(), msg)
        assertNull("$ONION_ADDRESS_V3.".toOnionAddressV3OrNull(), msg)
        assertNull(ONION_ADDRESS_V3.dropLast(1).toOnionAddressV3OrNull(), msg)
    }

    @Test
    fun givenOnionAddress_whenDecoded_thenIsSuccess() {
        assertEquals(ONION_ADDRESS_V3, ONION_ADDRESS_V3.toOnionAddressV3().decode().toOnionAddressV3().value)
    }

    @Test
    fun givenUrls_whenToOnionAddressV3_thenIsSuccess() {
        TEST_DATA_ONION_V3.forEach { url ->
            val address = url.toOnionAddressV3()
            assertEquals(ONION_ADDRESS_V3, address.value)
        }
    }

    companion object {
        const val ONION_ADDRESS_V3 = "6yxtsbpn2k7exxiarcbiet3fsr4komissliojxjlvl7iytacrnvz2uyd"

        val TEST_DATA_ONION_V3 = listOf(
            ONION_ADDRESS_V3,
            ONION_ADDRESS_V3.uppercase(),
            "$ONION_ADDRESS_V3.onion",
            "some.subdomain.$ONION_ADDRESS_V3",
            "some.subdomain.$ONION_ADDRESS_V3.onion",
            "http://$ONION_ADDRESS_V3.onion",
            "http://$ONION_ADDRESS_V3.onion/path",
            "http://$ONION_ADDRESS_V3.onion:8080/some/path/#some-fragment",
            "http://subdomain.$ONION_ADDRESS_V3.onion:8080/some/path",
            "http://sub.domain.$ONION_ADDRESS_V3.onion:8080/some/path",
            "http://username@$ONION_ADDRESS_V3.onion",
            "http://username@$ONION_ADDRESS_V3.onion:8080",
            "http://username@$ONION_ADDRESS_V3.onion:8080/some/path",
            "http://username:password@$ONION_ADDRESS_V3.onion",
            "http://username:password@$ONION_ADDRESS_V3.onion:8080",
            "http://username:password@$ONION_ADDRESS_V3.onion:8080/some/path",
            "http://some.sub.domain.$ONION_ADDRESS_V3.onion:8080/some/path",
            "http://username@some.sub.domain.$ONION_ADDRESS_V3.onion:8080/some/path",
            "http://username:password@some.sub.domain.$ONION_ADDRESS_V3.onion:8080/some/path",
            "https://username:password@some.sub.domain.$ONION_ADDRESS_V3.onion:8080/some/path",
            "ws://username:password@some.sub.domain.$ONION_ADDRESS_V3.onion:8080/some/path",
            "wss://username:password@some.sub.domain.$ONION_ADDRESS_V3.onion:8080/some/path",
        )
    }
}
