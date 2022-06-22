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
package io.matthewnelson.kmp.tor.manager.internal.util

import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.AddressInfo
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.State
import io.matthewnelson.kmp.tor.manager.common.state.TorStateManager
import io.matthewnelson.kmp.tor.manager.common.state.isEnabled
import io.matthewnelson.kmp.tor.manager.internal.ext.onStateChange
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class AddressInfoHandler(
    private val torManagerScope: CoroutineScope,
    private val stateManager: TorStateManager,
    private val dispatch: (AddressInfo) -> Unit
) {

    private val _addressInfo: AtomicRef<AddressInfo> = atomic(AddressInfo.NULL_VALUES)
    @get:JvmSynthetic
    internal val addressInfo: AddressInfo get() = _addressInfo.value

    private val addressInfoJob: AtomicRef<Job?> = atomic(null)

    @JvmSynthetic
    internal fun onStateChange(old: State, new: State) {
        _addressInfo.update { info ->
            info.onStateChange(old, new)?.let { newInfo ->
                addressInfoJob.value?.cancel()
                dispatch.invoke(newInfo)
                newInfo
            } ?: info
        }
    }

    @JvmSynthetic
    internal fun dispatchNewAddressInfo(addressInfo: AddressInfo) {
        _addressInfo.update { addressInfo }

        if (stateManager.state.isBootstrapped && stateManager.networkState.isEnabled()) {
            addressInfoJob.update { job ->
                job?.cancel()
                torManagerScope.launch {
                    delay(100L)
                    dispatch.invoke(addressInfo)
                }
            }
        }
    }
}
