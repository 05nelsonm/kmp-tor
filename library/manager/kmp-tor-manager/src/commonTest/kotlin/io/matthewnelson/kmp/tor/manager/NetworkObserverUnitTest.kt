/*
 * Copyright (c) 2022 Matthew Nelson
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
package io.matthewnelson.kmp.tor.manager

import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkObserverUnitTest {

    private class TestNetworkObserver: NetworkObserver() {
        var attachedCalledCounter = 0
            private set

        override fun isNetworkConnected(): Boolean {
            return true
        }

        override fun onManagerAttach() {
            attachedCalledCounter++
        }

        override fun onManagerDetach() {
            attachedCalledCounter--
        }

        fun changeConnectivity(connectivity: Connectivity) {
            dispatchConnectivityChange(connectivity)
        }
    }

    private val networkObserver = TestNetworkObserver()

    @Test
    fun givenAttachment_whenMultipleInstances_onAttachCalledOnlyOnFirstAttachment() {
        networkObserver.attach(instanceId = "instance1") {}
        assertEquals(1, networkObserver.attachedCalledCounter)

        networkObserver.attach(instanceId = "instance2") {}
        assertEquals(1, networkObserver.attachedCalledCounter)
    }

    @Test
    fun givenDetachment_whenMultipleInstances_onDetachCalledOnlyOnLastDetachment() {
        networkObserver.attach(instanceId = "instance1") {}
        networkObserver.attach(instanceId = "instance2") {}
        assertEquals(1, networkObserver.attachedCalledCounter)

        networkObserver.detach(instanceId = "instance1")
        assertEquals(1, networkObserver.attachedCalledCounter)

        networkObserver.detach(instanceId = "instance2")
        assertEquals(0, networkObserver.attachedCalledCounter)
    }

    @Test
    fun givenUnattachedInstance_whenDetached_onDetachIsNotCalled() {
        assertEquals(0, networkObserver.attachedCalledCounter)
        networkObserver.detach("instance")
        assertEquals(0, networkObserver.attachedCalledCounter)
    }

    @Test
    fun givenConnectivityDispatch_whenMultipleInstancesAttached_allAreUpdated() {
        var instance1State = NetworkObserver.Connectivity.Disconnected
        var instance2State = NetworkObserver.Connectivity.Disconnected

        networkObserver.attach(instanceId = "instance1") {
            instance1State = it
        }
        networkObserver.attach(instanceId = "instance2") {
            instance2State = it
        }

        networkObserver.changeConnectivity(NetworkObserver.Connectivity.Connected)
        assertEquals(NetworkObserver.Connectivity.Connected, instance1State)
        assertEquals(NetworkObserver.Connectivity.Connected, instance2State)

        networkObserver.changeConnectivity(NetworkObserver.Connectivity.Disconnected)
        assertEquals(NetworkObserver.Connectivity.Disconnected, instance1State)
        assertEquals(NetworkObserver.Connectivity.Disconnected, instance2State)
    }
}
