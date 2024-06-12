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
@file:Suppress("PropertyName", "LocalVariableName")

package io.matthewnelson.kmp.tor.runtime.service.internal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.enableForeground
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig.MetaData.Companion.stopServiceOnTaskRemoved
import kotlin.concurrent.Volatile

internal class RealTorServiceConfig private constructor(
    enableForeground: Boolean,
    stopServiceOnTaskRemoved: Boolean,
    ifForegroundExitProcessOnDestroyWhenTaskRemoved: Boolean,
    notificationId: Int,
    channelId: String,
    channelName: String,
    channelDescription: String,
    channelShowBadge: Boolean,
    internal val _iconNetworkEnabled: DrawableRes,
    internal val _iconNetworkDisabled: DrawableRes,
    internal val _iconDataXfer: DrawableRes,
    internal val _iconError: DrawableRes,
    internal val _colorWhenBootstrappedTrue: ColorRes,
    internal val _colorWhenBootstrappedFalse: ColorRes,
    visibility: Int,
    enableActionRestart: Boolean,
    enableActionStop: Boolean,
): TorServiceConfig(
    enableForeground,
    stopServiceOnTaskRemoved,
    ifForegroundExitProcessOnDestroyWhenTaskRemoved,
    notificationId,
    channelId,
    channelName,
    channelDescription,
    channelShowBadge,
    _iconNetworkEnabled.id,
    _iconNetworkDisabled.id,
    _iconDataXfer.id,
    _iconError.id,
    _colorWhenBootstrappedTrue.id,
    _colorWhenBootstrappedFalse.id,
    visibility,
    enableActionRestart,
    enableActionStop,
) {

    internal companion object {

        @Volatile
        private var _instance: RealTorServiceConfig? = null

        @JvmSynthetic
        @Throws(Resources.NotFoundException::class)
        internal fun of(ctx: Context): RealTorServiceConfig = _instance ?: synchronized(Companion) {
            _instance ?: ctx.applicationContext.toRealTorServiceConfig()
                .also { _instance = it }
        }

        @Throws(Resources.NotFoundException::class)
        private fun Context.toRealTorServiceConfig(): RealTorServiceConfig {
            val bundle: Bundle? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+
                val flags = PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())

                packageManager.getApplicationInfo(packageName, flags).metaData
            } else {
                // API 32-
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
            }

            val metaData = if (bundle != null) {
                val ctx = this

                object : MetaData<Resources.NotFoundException>() {
                    override fun getBoolean(key: String, default: Boolean): Boolean {
                        return bundle.getBoolean(key, default)
                    }
                    override fun getIntOrZero(key: String): Int {
                        return bundle.getInt(key, 0)
                    }
                    override fun getString(key: String): String? {
                        return bundle.getString(key)
                    }

                    override fun ColorRes.isValid(): Boolean = try {
                        retrieve(ctx)
                        true
                    } catch (_: Resources.NotFoundException) {
                        false
                    }
                    override fun DrawableRes.isValid(): Boolean = try {
                        retrieve(ctx)
                        true
                    } catch (_: Resources.NotFoundException) {
                        false
                    }

                    override fun hasForegroundServicePermission(): Boolean {
                        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // API 28+
                            isPermissionGranted(Manifest.permission.FOREGROUND_SERVICE)
                        } else {
                            true
                        }
                    }

                    override fun createException(message: String): Resources.NotFoundException {
                        return Resources.NotFoundException(message)
                    }
                }
            } else {
                null
            }

            val enableForeground = metaData.enableForeground()
            val stopServiceOnTaskRemoved: Boolean = metaData.stopServiceOnTaskRemoved()

            if (!enableForeground) {
                return RealTorServiceConfig(
                    enableForeground = false,
                    stopServiceOnTaskRemoved = stopServiceOnTaskRemoved,
                    ifForegroundExitProcessOnDestroyWhenTaskRemoved = false,
                    notificationId = 0,
                    channelId = "",
                    channelName = "",
                    channelDescription = "",
                    channelShowBadge = false,
                    _iconNetworkEnabled = DrawableRes.ZERO,
                    _iconNetworkDisabled = DrawableRes.ZERO,
                    _iconDataXfer = DrawableRes.ZERO,
                    _iconError = DrawableRes.ZERO,
                    _colorWhenBootstrappedTrue = ColorRes.ZERO,
                    _colorWhenBootstrappedFalse = ColorRes.ZERO,
                    visibility = 0,
                    enableActionRestart = false,
                    enableActionStop = false,
                )
            }

            if (metaData == null) {
                throw Resources.NotFoundException("""
                    Something went terribly wrong.

                    AndroidManifest.xml <meta-data> flag for running TorService in
                    the foreground has been set to `true`, but ApplicationInfo
                    meta-data is null.

                    This should _never_ happen...
                """.trimIndent())
            }

            val ifForegroundExitProcessOnDestroyWhenTaskRemoved = metaData.ifForegroundExitProcessOnDestroyWhenTaskRemoved()
            val notificationId = metaData.notificationId()

            val channelId = metaData.channelId()
            val channelName = metaData.channelName()
            val channelDescription = metaData.channelDescription()
            val channelShowBadge = metaData.channelShowBadge()

            val _iconNetworkEnabled = metaData.iconNetworkEnabled()
            val _iconNetworkDisabled = metaData.iconNetworkDisabled() ?: _iconNetworkEnabled
            val _iconError = metaData.iconError()
            val _iconDataXfer = metaData.iconDataXfer() ?: _iconNetworkEnabled

            val _colorWhenBootstrappedTrue = metaData.colorWhenBootstrappedTrue() ?: ColorRes.of(android.R.color.white)
            val _colorWhenBootstrappedFalse = metaData.colorWhenBootstrappedFalse() ?: ColorRes.of(android.R.color.white)

            val visibility = metaData.visibility()

            val enableActionRestart = metaData.enableActionRestart()
            val enableActionStop = metaData.enableActionStop()

            return RealTorServiceConfig(
                enableForeground = true,
                stopServiceOnTaskRemoved = stopServiceOnTaskRemoved,
                ifForegroundExitProcessOnDestroyWhenTaskRemoved = ifForegroundExitProcessOnDestroyWhenTaskRemoved,
                notificationId = notificationId,
                channelId = channelId,
                channelName = channelName,
                channelDescription = channelDescription,
                channelShowBadge = channelShowBadge,
                _iconNetworkEnabled = _iconNetworkEnabled,
                _iconNetworkDisabled = _iconNetworkDisabled,
                _iconDataXfer = _iconDataXfer,
                _iconError = _iconError,
                _colorWhenBootstrappedTrue = _colorWhenBootstrappedTrue,
                _colorWhenBootstrappedFalse = _colorWhenBootstrappedFalse,
                visibility = visibility,
                enableActionRestart = enableActionRestart,
                enableActionStop = enableActionStop,
            )
        }
    }
}
