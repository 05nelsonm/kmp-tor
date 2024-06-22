/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.runtime.service.internal.notification

import android.content.Context
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.service.R
import kotlinx.coroutines.CoroutineScope

internal abstract class AndroidAbstractNotification internal constructor(
    serviceScope: CoroutineScope,
    isServiceDestroyed: () -> Boolean,
    init: Any,
): AbstractNotification(
    serviceScope,
    isServiceDestroyed,
    init,
) {

    protected abstract val context: Context

    protected fun ButtonAction.provideString(): String = when (this) {
        ButtonAction.NewIdentity -> R.string.kmp_tor_notification_action_newnym
        ButtonAction.RestartTor -> R.string.kmp_tor_notification_action_restart
        ButtonAction.StopTor -> R.string.kmp_tor_notification_action_stop
    }.let { id -> context.getString(id) }

    protected final override fun Action.provideString(): String = when (this) {
        Action.StartDaemon -> R.string.kmp_tor_action_start
        Action.StopDaemon -> R.string.kmp_tor_action_stop
        Action.RestartDaemon -> R.string.kmp_tor_action_restart
    }.let { id -> context.getString(id) }

    protected final override fun TorState.Daemon.provideString(): String = when (this) {
        is TorState.Daemon.Off -> R.string.kmp_tor_tor_state_off
        is TorState.Daemon.On -> R.string.kmp_tor_tor_state_on
        is TorState.Daemon.Starting -> R.string.kmp_tor_tor_state_starting
        is TorState.Daemon.Stopping -> R.string.kmp_tor_tor_state_stopping
    }.let { id -> context.getString(id) }

    protected final override fun Byte.provideBootstrappedString(): BootstrappedString {
        val s = context.getString(R.string.kmp_tor_bootstrapped_format, this, "%")
        return BootstrappedString.of(s)
    }

    protected final override fun Int.provideNewNymRateLimitedString(): String {
        return context.getString(R.string.kmp_tor_newnym_rate_limited, this)
    }

    protected final override fun provideNewNymSuccessString(): String {
        return context.getString(R.string.kmp_tor_newnym_success)
    }

    protected final override fun provideNetworkWaitingString(): NetworkWaitingString {
        val s = context.getString(R.string.kmp_tor_waiting_on_network)
        return NetworkWaitingString.of(s)
    }
}
