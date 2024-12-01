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

import android.app.Application
import android.content.res.Resources
import io.matthewnelson.kmp.tor.controller.TorController
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.manager.common.TorControlManager
import io.matthewnelson.kmp.tor.manager.common.TorOperationManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.state.TorStateManager
import io.matthewnelson.kmp.tor.manager.internal.*
import io.matthewnelson.kmp.tor.manager.internal.DEPRECATION_NOTICE
import io.matthewnelson.kmp.tor.manager.internal.RealTorManagerAndroid
import io.matthewnelson.kmp.tor.manager.internal.TorService

/**
 * [TorManager]'s primary responsibility is to ensure synchronous execution of
 * Tor operations.
 *
 * By implementing [TorControlManager], [TorManager] facilitates pass-through
 * interaction with [TorController] (which is connected to automatically upon
 * every [start]).
 *
 * Interactions with [TorController] are single locking, first-in-first-out
 * ordered. This can be problematic if needing to abruptly [stop] or [restart]
 * Tor. To ensure that immediate reaction to those special events can be had,
 * [TorManager] implements a double locking queue system such that suspending
 * methods waiting to interact with [TorController] can be interrupted and
 * cancelled immediately.
 *
 * [TorManager] also handles a lot of conveniences such as:
 *  - Parsing [TorEvent]s in order to notify attached listeners with more usefully
 *  formatted data.
 *  - Tracks and dispatches State
 *  - Threading (all methods can safely be called from the Main thread)
 *  - etc.
 *
 * @see [RealTorManagerAndroid]
 * @see [RealTorManager]
 * @see [Destroyable]
 * @see [TorControlManager]
 * @see [TorOperationManager]
 * @see [TorStateManager]
 * @see [TorEventProcessor]
 * */
@Deprecated(message = DEPRECATION_NOTICE)
actual interface TorManager:
    Destroyable,
    TorControlManager,
    TorOperationManager,
    TorStateManager,
    TorEventProcessor<TorManagerEvent.SealedListener>
{
    actual val instanceId: String

    actual fun debug(enable: Boolean)

    companion object {

        /**
         * Android method for retrieving an instance of [RealTorManagerAndroid], which
         * wraps [RealTorManager] in an [android.app.Service].
         *
         * If you have elected for [TorService] to operate in the Foreground (See
         * [TorServiceConfig]), this should be called from [Application.onCreate] to
         * ensure appropriate application foreground/background tracking is had.
         *
         * @param [requiredEvents] events that are required for your implementation
         *  to function properly. These events will be set at every Tor start, and
         *  added to any calls to [TorManager.setEvents] during Tor runtime.
         *
         * @throws [Resources.NotFoundException] if you've enabled [TorService] to
         *  run in the foreground and your AndroidManifest.xml <meta-data> attributes
         *  are incorrect. By default, [TorService] runs in the background and needs
         *  no configuration, thus, this will _not_ throw an exception.
         *
         * @see [RealTorManagerAndroid]
         * @see [TorServiceConfig]
         * */
        @JvmStatic
        @JvmOverloads
        @Throws(Resources.NotFoundException::class)
        fun newInstance(
            application: Application,
            loader: KmpTorLoader,
            requiredEvents: Set<TorEvent>? = null
        ): TorManager =
            RealTorManagerAndroid(application, loader)
                .also {
                    TorService.addEvents(requiredEvents)
                }
    }
}
