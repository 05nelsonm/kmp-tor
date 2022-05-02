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
package io.matthewnelson.kmp.tor.controller.common.control.usecase

import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import kotlin.jvm.JvmField

/**
 * "SIGNAL" SP Signal CRLF
 * Signal = "RELOAD" / "SHUTDOWN" / "DUMP" / "DEBUG" / "HALT" /
 *          "HUP" / "INT" / "USR1" / "USR2" / "TERM" / "NEWNYM" /
 *          "CLEARDNSCACHE" / "HEARTBEAT" / "ACTIVE" / "DORMANT"
 *
 * https://torproject.gitlab.io/torspec/control-spec/#signal
 *
 * Take note that [Result.success] is returned for successfully
 * _delivering_ the [Signal] to Tor via the control port (Tor responded
 * with "250 OK"). What Tor does with it is sometimes dispatched as
 * a [TorEvent.LogMsg.Notice].
 *
 * For example, when [Signal.NewNym] is passed successfully, Tor
 * will sometimes rate limit your [Signal.NewNym] requests. In
 * this event, [signal] returns [Result.success], but via
 * [TorEvent.SealedListener.onEvent] a rate limit message will be sent.
 *
 * TorManager handles the [Signal.NewNym] example above w/o breaking
 * success/failure functionality by still returning [Result.success]
 * but with either [NEW_NYM_SUCCESS], or the actual
 * [NEW_NYM_RATE_LIMITED] message that Tor dispatched via
 * [TorEvent.LogMsg.Notice].
 * */
interface TorControlSignal {

    suspend fun signal(signal: Signal): Result<Any?>

    enum class Signal(@JvmField val value: String) {
        Reload(value = "RELOAD"),
        Shutdown(value = "SHUTDOWN"),
        Dump(value = "DUMP"),
        Debug(value = "DEBUG"),
        Halt(value = "HALT"),
        NewNym(value = "NEWNYM"),
        ClearDnsCache(value = "CLEARDNSCACHE"),
        Heartbeat(value = "HEARTBEAT"),
        SetActive(value = "ACTIVE"),
        SetDormant(value = "DORMANT");

        override fun toString(): String {
            return value
        }
    }

    companion object {
        const val NEW_NYM_SUCCESS = "You've changed Tor identities!"

        // actual: Rate limiting NEWNYM request: delaying by 8 second(s)
        // use:    output.startsWith(NEW_NYM_RATE_LIMITED)
        const val NEW_NYM_RATE_LIMITED = "Rate limiting NEWNYM request: delaying by "
    }

}
