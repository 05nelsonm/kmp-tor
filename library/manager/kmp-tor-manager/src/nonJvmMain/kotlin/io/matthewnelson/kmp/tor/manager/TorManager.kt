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
package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.manager.common.TorControlManager
import io.matthewnelson.kmp.tor.manager.common.TorOperationManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.state.TorStateManager

actual interface TorManager:
    Destroyable,
    TorControlManager,
    TorOperationManager,
    TorStateManager,
    TorEventProcessor<TorManagerEvent.SealedListener>
{

    actual fun debug(enable: Boolean)

    companion object {

        /**
         * @param [networkObserver] optional for observing device connectivity to
         *  push connection/disconnection changes to [RealTorManager] so it can
         *  set TorConfig setting `DisableNetwork` to `true` or `false`.
         * @param [requiredEvents] events that are required for your implementation
         *  to function properly. These events will be set at every Tor start, and
         *  added to any calls to [TorManager.setEvents] during Tor runtime.
         * */
        fun newInstance(
            loader: KmpTorLoader,
            networkObserver: NetworkObserver? = null,
            requiredEvents: Set<TorEvent>? = null
        ): TorManager =
            realTorManager(
                loader,
                networkObserver = networkObserver,
                requiredEvents = requiredEvents,
            )
    }
}
