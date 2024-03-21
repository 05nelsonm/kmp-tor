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
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxy
import io.matthewnelson.kmp.tor.runtime.core.address.Port.Proxy.Companion.toPortProxyOrNull
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class PortUnitTest {

    @Test
    fun givenMinToMax_whenToPort_thenIsSuccessful() {
        (Port.MIN..Port.MAX).forEach { port -> port.toPort() }
    }

    @Test
    fun givenMinToMax_whenToPortProxy_thenIsSuccessful() {
        (Port.Proxy.MIN..Port.Proxy.MAX).forEach { port -> port.toPortProxy() }
    }

    @Test
    fun givenMinMinus1_whenToPort_thenIsNull() {
        assertNull((Port.MIN - 1).toPortOrNull())
    }

    @Test
    fun givenMinMinus1_whenToPortProxy_thenIsNull() {
        assertNull((Port.Proxy.MIN - 1).toPortProxyOrNull())
    }

    @Test
    fun givenMaxPlus1_whenToPort_thenIsNull() {
        assertNull((Port.MAX + 1).toPortOrNull())
    }

    @Test
    fun givenMaxPlus1_whenToPortProxy_thenIsNull() {
        assertNull((Port.Proxy.MAX + 1).toPortProxyOrNull())
    }

    @Test
    fun givenURLWithPort_whenToPort_thenIsSuccessful() {
        "http://something.com:80".toPort()
    }

    @Test
    fun givenURLWithPort_whenToPortProxy_thenIsSuccessful() {
        "http://something.com:8080/some/path".toPortProxy()
    }

    @Test
    fun givenInt_whenPortProxyPossible_thenToPortReturnsPortProxy() {
        assertIs<Port.Proxy>(1024.toPort())
        assertIs<Port.Proxy>("http://some.com:1025/path".toPort())
    }
}
