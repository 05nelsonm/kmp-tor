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
package io.matthewnelson.kmp.tor.controller.common.events

interface TorEventProcessor<T: TorEvent.SealedListener> {

    /**
     * Add a [TorEvent.SealedListener] to receive events as they come in off the socket.
     *
     * @return True if listener has been added or was already present, false if it not
     * */
    fun addListener(listener: T): Boolean

    /**
     * Remove a [TorEvent.SealedListener]
     *
     * @return True if removed, false if it was not present to begin with.
     * */
    fun removeListener(listener: T): Boolean
}
