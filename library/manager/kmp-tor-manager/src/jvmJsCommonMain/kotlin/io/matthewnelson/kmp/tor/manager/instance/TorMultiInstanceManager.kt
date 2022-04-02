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
package io.matthewnelson.kmp.tor.manager.instance

import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.manager.KmpTorLoader
import io.matthewnelson.kmp.tor.manager.NetworkObserver
import io.matthewnelson.kmp.tor.manager.TorConfigProvider
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.internal.util.SynchronizedMutableMap
import io.matthewnelson.kmp.tor.manager.realTorManager
import kotlinx.coroutines.sync.Mutex
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Enables multi-instance operations of Tor by managing locks used by [realTorManager].
 *
 * When using multiple instances, you must ensure that [TorConfigProvider] directory
 * and file paths are unique to the given instance. If Tor is sharing directories between
 * instances, bad things happen.
 * */
object TorMultiInstanceManager {

    private val instanceLockMap = SynchronizedMutableMap<Mutex>()

    @JvmStatic
    fun newTorManagerInstance(
        instanceId: InstanceId,
        loader: KmpTorLoader,
        networkObserver: NetworkObserver?,
        requiredEvents: Set<TorEvent>?
    ): TorManager {
        val instanceLock: Mutex = instanceLockMap.withLock {
            get(instanceId.value) ?: Mutex()
                .also { newLock ->
                    put(instanceId.value, newLock)
                }
        }

        return realTorManager(
            loader = loader,
            instanceId = instanceId.value,
            processorLock = instanceLock,
            networkObserver = networkObserver,
            requiredEvents = requiredEvents
        )
    }

    @JvmSynthetic
    internal fun removeInstance(instanceId: InstanceId) {
        instanceLockMap.withLock {
            remove(instanceId.value)
        }
    }
}