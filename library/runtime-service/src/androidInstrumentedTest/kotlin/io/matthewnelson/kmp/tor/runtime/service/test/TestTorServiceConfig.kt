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
@file:Suppress("LocalVariableName")

package io.matthewnelson.kmp.tor.runtime.service.test

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import io.matthewnelson.kmp.tor.runtime.service.internal.ColorRes
import io.matthewnelson.kmp.tor.runtime.service.internal.DrawableRes

internal class TestTorServiceConfig
@Throws(Resources.NotFoundException::class)
internal constructor(
    enableForeground: Boolean = true,
    stopServiceOnTaskRemoved: Boolean = true,
    ifForegroundExitProcessOnDestroyWhenTaskRemoved: Boolean = true,
    notificationId: Int = 21,
    channelId: String = "TorService Channel Id",
    channelName: String = "TorService Channel Name",
    channelDescription: String = "TorService Channel Description",
    channelShowBadge: Boolean = false,
    _iconNetworkEnabled: DrawableRes = DrawableRes.of(android.R.drawable.stat_notify_chat),
    _iconNetworkDisabled: DrawableRes = DrawableRes.of(android.R.drawable.stat_notify_sync_noanim),
    _iconDataXfer: DrawableRes = DrawableRes.of(android.R.drawable.stat_notify_missed_call),
    _iconError: DrawableRes = DrawableRes.of(android.R.drawable.stat_notify_error),
    _colorWhenBootstrappedTrue: ColorRes = ColorRes.of(android.R.color.holo_purple),
    _colorWhenBootstrappedFalse: ColorRes = ColorRes.of(android.R.color.white),
    visibility: Visibility = Visibility.Private,
    enableActionRestart: Boolean = false,
    enableActionStop: Boolean = false,
): TorServiceConfig(
    enableForeground,
    stopServiceOnTaskRemoved,
    ifForegroundExitProcessOnDestroyWhenTaskRemoved,
    notificationId,
    channelId,
    channelName,
    channelDescription,
    channelShowBadge,
    _iconNetworkEnabled,
    _iconNetworkDisabled,
    _iconDataXfer,
    _iconError,
    _colorWhenBootstrappedTrue,
    _colorWhenBootstrappedFalse,
    visibility.value,
    enableActionRestart,
    enableActionStop,
    Synthetic.INIT,
) {

    internal enum class Visibility(val value: Int) {
        Public(1),
        Secret(-1),
        Private(0),
    }

    init {
        val context = ApplicationProvider.getApplicationContext<Application>()

        listOf(
            this._iconNetworkEnabled,
            this._iconNetworkDisabled,
            this._iconDataXfer,
            this._iconError,
        ).forEach { res ->
            res.retrieve(context)
        }
        listOf(
            this._colorWhenBootstrappedTrue,
            this._colorWhenBootstrappedFalse,
        ).forEach { res ->
            res.retrieve(context)
        }
    }
}
