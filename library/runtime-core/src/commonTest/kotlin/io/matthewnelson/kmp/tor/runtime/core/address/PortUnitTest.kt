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

import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPort
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Companion.toPortOrNull
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeral
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Ephemeral.Companion.toPortEphemeralOrNull
import kotlin.test.*

class PortUnitTest {

    @Test
    fun givenMinToMax_whenToPort_thenIsSuccessful() {
        (Port.MIN..Port.MAX).forEach { port -> port.toPort() }
    }

    @Test
    fun givenMinToMax_whenToPortEphemeral_thenIsSuccessful() {
        (Port.Ephemeral.MIN..Port.Ephemeral.MAX).forEach { port -> port.toPortEphemeral() }
    }

    @Test
    fun givenMinMinus1_whenToPort_thenIsNull() {
        assertNull((Port.MIN - 1).toPortOrNull())
    }

    @Test
    fun givenMinMinus1_whenToPortEphemeral_thenIsNull() {
        assertNull((Port.Ephemeral.MIN - 1).toPortEphemeralOrNull())
    }

    @Test
    fun givenMaxPlus1_whenToPort_thenIsNull() {
        assertNull((Port.MAX + 1).toPortOrNull())
    }

    @Test
    fun givenMaxPlus1_whenToPortEphemeral_thenIsNull() {
        assertNull((Port.Ephemeral.MAX + 1).toPortEphemeralOrNull())
    }

    @Test
    fun givenURLWithPort_whenToPort_thenIsSuccessful() {
        assertEquals(80, "http://something.com:80".toPort().value)
    }

    @Test
    fun givenURLWithPort_whenToPortEphemeral_thenIsSuccessful() {
        assertEquals(8080, "http://something.com:8080/some/path".toPortEphemeral().value)
    }

    @Test
    fun givenIPv6Address_whenToPort_thenChecksForBrackets() {
        assertNull("::8080".toPortOrNull())
        assertNull("[::8080]:".toPortOrNull())

        // 2 or more colons, but invalid brackets
        assertNull(":]:8080".toPortOrNull())
        assertNull("::]:8080".toPortOrNull())
        assertNull("[::8080".toPortOrNull())

        assertEquals(8080, "[::]:8080".toPort().value)
    }

    @Test
    fun givenInt_whenPortEphemeralPossible_thenToPortReturnsPortEphemeral() {
        assertIs<Port.Ephemeral>(1024.toPort())
        assertIs<Port.Ephemeral>("http://something.com:1025/path".toPort())
    }
}
