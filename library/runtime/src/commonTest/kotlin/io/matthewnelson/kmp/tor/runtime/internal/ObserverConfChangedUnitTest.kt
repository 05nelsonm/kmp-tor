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
@file:Suppress("UnnecessaryVariable")

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.tor.runtime.test.TestTorListenersManager
import io.matthewnelson.kmp.tor.runtime.TorState
import kotlin.test.*

class ObserverConfChangedUnitTest {

    private class TestConfChangedObserver private constructor(
        val manager: TestTorListenersManager,
    ): ObserverConfChanged(manager, "") {
        constructor(): this(TestTorListenersManager())
        public override fun notify(data: String) { super.notify(data) }
    }

    private val observer = TestConfChangedObserver()

    @Test
    fun givenDisableNetwork_whenParsed_thenUpdatesTorStateManager() {
        // Default (Disabled = true) will have no '='
        observer.notify("DisableNetwork")

        // For posterity
        observer.notify("DisableNetwork=1")

        // Enabled
        observer.notify("DisableNetwork=0")

        // Multi-line CONF_CHANGED
        observer.notify("""
            __OwningControllerProcess
            DisableNetwork=0
        """.trimIndent())

        assertEquals(4, observer.manager.states.size)

        listOf(
            TorState.Network.Disabled,
            TorState.Network.Disabled,
            TorState.Network.Enabled,
            TorState.Network.Enabled,
        ).forEachIndexed { i, expected ->
            val (daemon, network) = observer.manager.states[i]
            assertNull(daemon)
            assertEquals(expected, network)
        }
    }

    @Test
    fun givenSocksPort_whenParsed_thenUpdatesTorListenersManager() {
        val expected1 = "unix:\"/tmp/kmp_tor_test/obs_conn_no_net/work/socks1.sock\" OnionTrafficOnly GroupWritable"
        val expected2 = "unix:\"/tmp/kmp_tor_test/obs_conn_no_net/work/socks2.sock\" OnionTrafficOnly"
        val expected3 = "unix:/tmp/kmp_tor_test/obs_conn_no_net/work/socks3.sock"
        val expected4 = "9050"

        observer.notify("__SocksPort=$expected1")

        observer.notify("""
            __SocksPort=$expected2
            SocksPort=$expected3
        """.trimIndent())

        observer.notify("SocksPort=$expected4")

        assertEquals(3, observer.manager.unixConf.size)
        assertEquals("Socks" to setOf(expected1), observer.manager.unixConf[0])
        assertEquals("Socks" to setOf(expected2, expected3), observer.manager.unixConf[1])
        assertEquals("Socks" to setOf(expected4), observer.manager.unixConf[2])
    }
}
