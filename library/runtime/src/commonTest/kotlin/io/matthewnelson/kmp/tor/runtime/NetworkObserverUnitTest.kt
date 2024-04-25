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
package io.matthewnelson.kmp.tor.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import io.matthewnelson.kmp.tor.runtime.core.OnEvent

class NetworkObserverUnitTest {

    private class TestNetworkObserver: NetworkObserver() {
        var onObserversCount = 0
            private set

        override fun isNetworkConnected(): Boolean = true
        override fun onObserversNotEmpty() { onObserversCount++ }
        override fun onObserversEmpty() { onObserversCount-- }

        fun notifyInvoke() { notify(Connectivity.Connected) }
    }

    private val networkObserver = TestNetworkObserver()

    @Test
    fun givenAddObserver_whenMultiple_thenOnObserversNotEmptyInvokedOnFirstOnly() {
        assertEquals(0, networkObserver.onObserversCount)
        networkObserver.subscribe {  }
        assertEquals(1, networkObserver.onObserversCount)
        networkObserver.subscribe {  }
        assertEquals(1, networkObserver.onObserversCount)
    }

    @Test
    fun givenRemoveObserver_whenMultiple_thenOnObserversEmptyInvokedOnLastOnly() {
        assertEquals(0, networkObserver.onObserversCount)
        val o1 = OnEvent<NetworkObserver.Connectivity> {}
        val o2 = OnEvent<NetworkObserver.Connectivity> {}
        networkObserver.subscribe(o1)
        networkObserver.subscribe(o2)
        assertEquals(1, networkObserver.onObserversCount)
        networkObserver.unsubscribe(o1)
        assertEquals(1, networkObserver.onObserversCount)
        networkObserver.unsubscribe(o1)
        assertEquals(1, networkObserver.onObserversCount)
        networkObserver.unsubscribe(o2)
        assertEquals(0, networkObserver.onObserversCount)
    }

    @Test
    fun givenRemoveObserver_whenNoObservers_thenOnObserversEmptyNotInvoked() {
        assertEquals(0, networkObserver.onObserversCount)
        networkObserver.unsubscribe {  }
        assertEquals(0, networkObserver.onObserversCount)
    }

    @Test
    fun givenObserver_whenAddMultipleTimes_thenOnlyRegisteredOnce() {
        var invocations = 0
        val o1 = OnEvent<NetworkObserver.Connectivity> { invocations++ }
        networkObserver.subscribe(o1)
        networkObserver.subscribe(o1)
        networkObserver.notifyInvoke()
        assertEquals(1, invocations)
        networkObserver.unsubscribe(o1)
        networkObserver.notifyInvoke()
        assertEquals(1, invocations)
    }
}
