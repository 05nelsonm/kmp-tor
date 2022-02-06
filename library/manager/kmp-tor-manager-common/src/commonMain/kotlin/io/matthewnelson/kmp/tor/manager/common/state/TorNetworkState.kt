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
package io.matthewnelson.kmp.tor.manager.common.state

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class TorNetworkState {
    object Disabled     : TorNetworkState() {
        private const val DISABLED = "Network: Disabled"
        override fun toString(): String = DISABLED
    }
    object Enabled      : TorNetworkState() {
        private const val ENABLED = "Network: Enabled"
        override fun toString(): String = ENABLED
    }
}

@Suppress("nothing_to_inline")
@OptIn(ExperimentalContracts::class)
inline fun TorNetworkState.isDisabled(): Boolean {
    contract {
        returns(true) implies(this@isDisabled is TorNetworkState.Disabled)
    }

    return this is TorNetworkState.Disabled
}

@Suppress("nothing_to_inline")
@OptIn(ExperimentalContracts::class)
inline fun TorNetworkState.isEnabled(): Boolean {
    contract {
        returns(true) implies(this@isEnabled is TorNetworkState.Enabled)
    }

    return this is TorNetworkState.Enabled
}
