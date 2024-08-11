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
package io.matthewnelson.kmp.tor.runtime.service.ui.internal

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.service.ui.R
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.*
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentAction
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentBandwidth
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentBootstrap
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentMessage
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentNetworkWaiting

@Throws(Resources.NotFoundException::class)
internal fun Context.retrieveColor(res: ColorRes): ColorInt {
    if (res.id == 0) throw Resources.NotFoundException("id=0")

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        // API 23+
        getColor(res.id)
    } else {
        // API 22-
        @Suppress("DEPRECATION")
        resources.getColor(res.id)
    }.let { ColorInt(it) }
}

@Throws(Resources.NotFoundException::class)
internal fun Context.retrieveDrawable(res: DrawableRes): Drawable {
    if (res.id == 0) throw Resources.NotFoundException("id=0")

    val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        // API 21+
        getDrawable(res.id)
    } else {
        // API 20-
        @Suppress("DEPRECATION")
        resources.getDrawable(res.id)
    }

    if (drawable == null) {
        throw Resources.NotFoundException("$res returned null")
    }

    return drawable
}

internal fun Context.retrieveString(content: ContentText<*>): String = when (content) {
    is ContentAction -> when (content.value) {
        Action.StartDaemon -> R.string.kmp_tor_action_start
        Action.StopDaemon -> R.string.kmp_tor_action_stop
        Action.RestartDaemon -> R.string.kmp_tor_action_restart
    }.let { id -> getString(id) }
    is ContentBandwidth -> content.value
    is ContentBootstrap -> {
        getString(R.string.kmp_tor_bootstrapped_formated, content.value, "%")
    }
    is ContentMessage.NewNym.RateLimited.Raw -> content.value
    is ContentMessage.NewNym.RateLimited.Seconds -> {
        getString(R.string.kmp_tor_newnym_rate_limited, content.value)
    }
    is ContentMessage.NewNym.Success -> {
        getString(R.string.kmp_tor_newnym_success)
    }
    is ContentNetworkWaiting -> {
        getString(R.string.kmp_tor_waiting_on_network)
    }
}

internal fun Context.retrieveString(state: TorState.Daemon): String = when (state) {
    is TorState.Daemon.Off -> R.string.kmp_tor_tor_state_off
    is TorState.Daemon.On -> R.string.kmp_tor_tor_state_on
    is TorState.Daemon.Starting -> R.string.kmp_tor_tor_state_starting
    is TorState.Daemon.Stopping -> R.string.kmp_tor_tor_state_stopping
}.let { id -> getString(id) }
