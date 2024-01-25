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
package io.matthewnelson.kmp.tor.runtime.ctrl.api.builder

import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port
import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.Port.Proxy.Companion.toPortProxy
import java.lang.IllegalArgumentException
import kotlin.test.Test
import kotlin.test.assertFailsWith

// TODO: Move to commonMain
class PortUnitTest {

    @Test
    fun givenPortProxy_whenFindAvailable_thenSucceeds() {
        Port.Proxy.MIN.toPortProxy().findAvailable(1_000)
    }

    @Test
    fun givenFindAvailable_whenInvalidLimit_thenThrowsException() {
        val port = Port.Proxy.MIN.toPortProxy()
        assertFailsWith<IllegalArgumentException> { port.findAvailable(0) }
        assertFailsWith<IllegalArgumentException> { port.findAvailable(1_001) }
    }
}
