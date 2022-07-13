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
package io.matthewnelson.kmp.tor.manager.internal

import io.matthewnelson.kmp.tor.manager.common.state.TorStateManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.synchronized
import kotlinx.atomicfu.locks.SynchronizedObject

internal class TorStateMachine(
    private val dispatch: (old: TorManagerEvent.State, new: TorManagerEvent.State) -> Unit
) : SynchronizedObject(), TorStateManager {

    private val _currentState: AtomicRef<TorManagerEvent.State> = atomic(
        TorManagerEvent.State(TorState.Off, TorNetworkState.Disabled)
    )

    // Unused
    override val addressInfo: TorManagerEvent.AddressInfo
        get() = TorManagerEvent.AddressInfo.NULL_VALUES
    override val state: TorState get() = _currentState.value.torState
    override val networkState: TorNetworkState get() = _currentState.value.networkState

    internal fun updateState(state: TorState, networkState: TorNetworkState): Boolean {
        return synchronized(this) {
            val current = _currentState.value
            if (current.torState != state || current.networkState != networkState) {
                val new = TorManagerEvent.State(state, networkState)
                _currentState.value = new
                dispatch.invoke(current, new)
                true
            } else {
                false
            }
        }
    }

    internal fun updateState(state: TorState): Boolean {
        return synchronized(this) {
            val current = _currentState.value
            if (current.torState != state) {
                val new = current.copy(torState = state)
                _currentState.value = new
                dispatch.invoke(current, new)
                true
            } else {
                false
            }
        }
    }

    internal fun updateState(state: TorNetworkState): Boolean {
        return synchronized(this) {
            val current = _currentState.value
            if (current.networkState != state) {
                val new = current.copy(networkState = state)
                _currentState.value = new
                dispatch.invoke(current, new)
                true
            } else {
                false
            }
        }
    }

}
