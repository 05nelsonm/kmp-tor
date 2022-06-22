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
import io.matthewnelson.kmp.tor.manager.*
import io.matthewnelson.kmp.tor.manager.internal.util.synchronous.SynchronizedMutableMap
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

    private val instanceLockMap = SynchronizedMutableMap<InstanceLockHolder>()

    private data class InstanceLockHolder(val instanceCount: Int, val lock: Mutex)

    /**
     * Method for retrieving a new instance of [TorManager] for the given
     * [InstanceId].
     *
     * @param [networkObserver] optional for observing device connectivity to
     *  push connection/disconnection changes to [RealTorManager] so it can
     *  set TorConfig setting `DisableNetwork` to `true` or `false`.
     * @param [requiredEvents] events that are required for your implementation
     *  to function properly. These events will be set at every Tor start, and
     *  added to any calls to [TorManager.setEvents] during Tor runtime.
     * */
    @JvmStatic
    fun newTorManagerInstance(
        instanceId: InstanceId,
        loader: KmpTorLoader,
        networkObserver: NetworkObserver? = null,
        requiredEvents: Set<TorEvent>? = null,
    ): TorManager {
        val instanceLock: Mutex = instanceLockMap.withLock {
            val holder = get(instanceId.value)

            if (holder == null) {
                val newHolder = InstanceLockHolder(1, Mutex())
                put(instanceId.value, newHolder)
                newHolder.lock
            } else {
                put(instanceId.value, holder.copy(instanceCount = holder.instanceCount + 1))
                holder.lock
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
    internal fun removeLockForInstanceId(instanceId: InstanceId) {
        instanceLockMap.withLock {
            val holder = remove(instanceId.value) ?: return@withLock

            if (holder.instanceCount > 1) {
                put(instanceId.value, holder.copy(instanceCount = holder.instanceCount - 1))
            } else {
                KmpTorLoader.removeInstanceRunLock(instanceId.value)
            }
        }
    }
}
