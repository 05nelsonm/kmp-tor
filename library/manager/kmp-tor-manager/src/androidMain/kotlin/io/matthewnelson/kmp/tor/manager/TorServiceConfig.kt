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
@file:Suppress("SpellCheckingInspection")

package io.matthewnelson.kmp.tor.manager

import android.content.Context
import android.content.res.Resources
import io.matthewnelson.kmp.tor.manager.internal.RealTorServiceConfig
import io.matthewnelson.kmp.tor.manager.internal.TorService
import io.matthewnelson.kmp.tor.manager.internal.wrappers.ColorRes
import io.matthewnelson.kmp.tor.manager.internal.wrappers.DrawableRes

/**
 * Configuration options for running [TorService] in the Foreground.
 *
 * By default, [TorService] runs in the background and w/o any configuration necessary.
 *
 * Configuration settings are done via <meta-data> tags within your AndroidManifest.xml's
 * <application> tag.
 *
 * To set config options, see examples here:
 * - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
 * - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
 *
 * @see [RealTorServiceConfig]
 * */
@Suppress("PropertyName")
abstract class TorServiceConfig internal constructor() {
    abstract val enableForeground: Boolean

    abstract val stopServiceOnTaskRemoved: Boolean
    abstract val ifForegroundExitProcessOnDestroyWhenTaskRemoved: Boolean

    abstract val notificationId: Int
    abstract val channelId: String
    abstract val channelName: String
    abstract val channelDescription: String
    abstract val channelShowBadge: Boolean

    internal abstract val _iconNetworkEnabled: DrawableRes
    internal abstract val _iconNetworkDisabled: DrawableRes
    internal abstract val _iconDataXfer: DrawableRes
    internal abstract val _iconError: DrawableRes
    val iconNetworkEnabled: Int get() = _iconNetworkEnabled.id
    val iconNetworkDisabled: Int get() = _iconNetworkDisabled.id
    val iconDataXfer: Int get() = _iconDataXfer.id
    val iconError: Int get() = _iconError.id

    internal abstract val _colorWhenBootstrappedTrue: ColorRes
    internal abstract val _colorWhenBootstrappedFalse: ColorRes
    val colorWhenBootstrappedTrue: Int get() = _colorWhenBootstrappedTrue.id
    val colorWhenBootstrappedFalse: Int get() = _colorWhenBootstrappedFalse.id

    abstract val visibility: Int

    abstract val enableRestartAction: Boolean
    abstract val enableStopAction: Boolean

    override fun toString(): String {
        return """
            TorServiceConfig(
                enableForeground=$enableForeground,
                stopServiceOnTaskRemoved=$stopServiceOnTaskRemoved,
                ifForegroundExitProcessOnDestroyWhenTaskRemoved=$ifForegroundExitProcessOnDestroyWhenTaskRemoved,
                notificationId=$notificationId,
                channelId=$channelId,
                channelName=$channelName,
                channelDescription=$channelDescription,
                channelShowBadge=$channelShowBadge,
                iconNetworkEnabled=$_iconNetworkEnabled,
                iconNetworkDisabled=$_iconNetworkDisabled,
                iconDataXfer=$_iconDataXfer,
                iconError=$_iconError,
                colorWhenBootstrappedTrue=$_colorWhenBootstrappedTrue,
                colorWhenBootstrappedFalse=$_colorWhenBootstrappedFalse,
                visibility=$visibility,
                enableRestartAction=$enableRestartAction,
                enableStopAction=$enableStopAction,
            )
        """.trimIndent()
    }

    override fun equals(other: Any?): Boolean {
        return  other != null                                                   &&
                other is TorServiceConfig                                       &&
                other.enableForeground == enableForeground                      &&
                other.stopServiceOnTaskRemoved == stopServiceOnTaskRemoved      &&
                other.ifForegroundExitProcessOnDestroyWhenTaskRemoved == ifForegroundExitProcessOnDestroyWhenTaskRemoved &&
                other.notificationId == notificationId                          &&
                other.channelId == channelId                                    &&
                other.channelName == channelName                                &&
                other.channelDescription == channelDescription                  &&
                other.channelShowBadge == channelShowBadge                      &&
                other.iconNetworkEnabled == iconNetworkEnabled                  &&
                other.iconNetworkDisabled == iconNetworkDisabled                &&
                other.iconDataXfer == iconDataXfer                              &&
                other.iconError == iconError                                    &&
                other.colorWhenBootstrappedTrue == colorWhenBootstrappedTrue    &&
                other.colorWhenBootstrappedFalse == colorWhenBootstrappedFalse  &&
                other.visibility == visibility                                  &&
                other.enableRestartAction == enableRestartAction                &&
                other.enableStopAction == enableStopAction
    }

    override fun hashCode(): Int {
        var result = 17
        result = result * 31 + enableForeground.hashCode()
        result = result * 31 + stopServiceOnTaskRemoved.hashCode()
        result = result * 31 + ifForegroundExitProcessOnDestroyWhenTaskRemoved.hashCode()
        result = result * 31 + notificationId.hashCode()
        result = result * 31 + channelId.hashCode()
        result = result * 31 + channelName.hashCode()
        result = result * 31 + channelDescription.hashCode()
        result = result * 31 + channelShowBadge.hashCode()
        result = result * 31 + iconNetworkEnabled.hashCode()
        result = result * 31 + iconNetworkDisabled.hashCode()
        result = result * 31 + iconDataXfer.hashCode()
        result = result * 31 + iconError.hashCode()
        result = result * 31 + colorWhenBootstrappedTrue.hashCode()
        result = result * 31 + colorWhenBootstrappedFalse.hashCode()
        result = result * 31 + visibility.hashCode()
        result = result * 31 + enableRestartAction.hashCode()
        result = result * 31 + enableStopAction.hashCode()
        return result
    }

    companion object {
        @Volatile
        private var instance: RealTorServiceConfig? = null

        @JvmStatic
        @Throws(Resources.NotFoundException::class)
        fun getMetaData(context: Context): TorServiceConfig =
            instance ?: synchronized(this) {
                instance ?: RealTorServiceConfig(context)
                    .also { instance = it }
            }
    }
}
