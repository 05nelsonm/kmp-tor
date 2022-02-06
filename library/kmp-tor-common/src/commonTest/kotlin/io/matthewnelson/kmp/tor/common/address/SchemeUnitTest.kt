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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SchemeUnitTest {

    @Test
    fun givenString_whenSchemeHttp_returnsSchemeAndRemainingSubString() {
        val address = "${Scheme.HTTP}${OnionAddressV3UnitTest.VALID_ONION_ADDRESS}"
        val (schemeActual, addressActual) = address.separateSchemeFromAddress()
        assertEquals(OnionAddressV3UnitTest.VALID_ONION_ADDRESS, addressActual)
        assertEquals(Scheme.HTTP, schemeActual)
    }

    @Test
    fun givenString_whenSchemeHttps_returnsSchemeAndRemainingSubString() {
        val address = "${Scheme.HTTPS}${OnionAddressV3UnitTest.VALID_ONION_ADDRESS}"
        val (schemeActual, addressActual) = address.separateSchemeFromAddress()
        assertEquals(OnionAddressV3UnitTest.VALID_ONION_ADDRESS, addressActual)
        assertEquals(Scheme.HTTPS, schemeActual)
    }

    @Test
    fun givenString_whenSchemeWs_returnsSchemeAndRemainingSubString() {
        val address = "${Scheme.WS}${OnionAddressV3UnitTest.VALID_ONION_ADDRESS}"
        val (schemeActual, addressActual) = address.separateSchemeFromAddress()
        assertEquals(OnionAddressV3UnitTest.VALID_ONION_ADDRESS, addressActual)
        assertEquals(Scheme.WS, schemeActual)
    }

    @Test
    fun givenString_whenSchemeWss_returnsSchemeAndRemainingSubString() {
        val address = "${Scheme.WSS}${OnionAddressV3UnitTest.VALID_ONION_ADDRESS}"
        val (schemeActual, addressActual) = address.separateSchemeFromAddress()
        assertEquals(OnionAddressV3UnitTest.VALID_ONION_ADDRESS, addressActual)
        assertEquals(Scheme.WSS, schemeActual)
    }

    @Test
    fun givenString_whenSchemeNotPresent_returnsNullSchemeAndRemainingSubString() {
        val address = OnionAddressV3UnitTest.VALID_ONION_ADDRESS
        val (schemeActual, addressActual) = address.separateSchemeFromAddress()
        assertEquals(OnionAddressV3UnitTest.VALID_ONION_ADDRESS, addressActual)
        assertNull(schemeActual)
    }
}
