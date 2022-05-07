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

import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic

sealed interface TorState {

    val bootstrap: Int
    val isBootstrapped: Boolean

    object Off                                                      : TorState {
        override val bootstrap: Int = 0
        override val isBootstrapped: Boolean = false
        private const val OFF = "Tor: Off"
        override fun toString(): String = OFF
    }

    @SealedValueClass
    @OptIn(ExperimentalTorApi::class)
    sealed interface On                                             : TorState {
        companion object {
            @JvmStatic
            operator fun invoke(bootstrap: Int): On {
                return RealOn(bootstrap)
            }
        }
    }

    @JvmInline
    private value class RealOn(override val bootstrap: Int)         : On {
        override val isBootstrapped: Boolean get() = bootstrap >= 100
        override fun toString(): String = ON

        companion object {
            private const val ON = "Tor: On"
        }
    }

    object Starting                                                 : TorState {
        override val bootstrap: Int get() = Off.bootstrap
        override val isBootstrapped: Boolean get() = Off.isBootstrapped
        private const val STARTING = "Tor: Starting"
        override fun toString(): String = STARTING
    }

    object Stopping                                                 : TorState {
        override val bootstrap: Int get() = Off.bootstrap
        override val isBootstrapped: Boolean get() = Off.isBootstrapped
        private const val STOPPING = "Tor: Stopping"
        override fun toString(): String = STOPPING
    }
}

@Suppress("nothing_to_inline")
@OptIn(ExperimentalContracts::class)
inline fun TorState.isOff(): Boolean {
    contract {
        returns(true) implies(this@isOff is TorState.Off)
    }

    return this is TorState.Off
}

@Suppress("nothing_to_inline")
@OptIn(ExperimentalContracts::class)
inline fun TorState.isOn(): Boolean {
    contract {
        returns(true) implies(this@isOn is TorState.On)
    }

    return this is TorState.On
}

@Suppress("nothing_to_inline")
@OptIn(ExperimentalContracts::class)
inline fun TorState.isStarting(): Boolean {
    contract {
        returns(true) implies(this@isStarting is TorState.Starting)
    }

    return this is TorState.Starting
}

@Suppress("nothing_to_inline")
@OptIn(ExperimentalContracts::class)
inline fun TorState.isStopping(): Boolean {
    contract {
        returns(true) implies(this@isStopping is TorState.Stopping)
    }

    return this is TorState.Stopping
}