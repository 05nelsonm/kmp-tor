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

import android.Manifest.permission.FOREGROUND_SERVICE
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import io.matthewnelson.kmp.tor.manager.internal.TorService
import io.matthewnelson.kmp.tor.manager.internal.ext.isPermissionGranted
import io.matthewnelson.kmp.tor.manager.internal.wrappers.ColorRes
import io.matthewnelson.kmp.tor.manager.internal.wrappers.DrawableRes
import io.matthewnelson.kmp.tor.manager.internal.wrappers.retrieve

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

private class RealTorServiceConfig(context: Context): TorServiceConfig() {

    override val enableForeground: Boolean

    override val stopServiceOnTaskRemoved: Boolean
    override val ifForegroundExitProcessOnDestroyWhenTaskRemoved: Boolean

    override val notificationId: Int
    override val channelId: String
    override val channelName: String
    override val channelDescription: String
    override val channelShowBadge: Boolean

    override val _iconNetworkEnabled: DrawableRes
    override val _iconNetworkDisabled: DrawableRes
    override val _iconDataXfer: DrawableRes
    override val _iconError: DrawableRes

    override val _colorWhenBootstrappedTrue: ColorRes
    override val _colorWhenBootstrappedFalse: ColorRes

    override val visibility: Int

    override val enableRestartAction: Boolean
    override val enableStopAction: Boolean

    private fun DrawableRes.isValid(context: Context): Boolean {
        return try {
            retrieve(context) != null
        } catch (e: Resources.NotFoundException) {
            false
        }
    }

    private fun ColorRes.isValid(context: Context): Boolean {
        return try {
            retrieve(context)
            true
        } catch (_: Resources.NotFoundException) {
            false
        }
    }

    init {
        val meta: Bundle? = context.applicationContext
            .packageManager
            .getApplicationInfo(context.applicationContext.packageName, PackageManager.GET_META_DATA)
            .metaData

        enableForeground = (meta?.getBoolean(KEY_ENABLE_FOREGROUND, false) ?: false).let { enable ->
            if (enable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.isPermissionGranted(FOREGROUND_SERVICE)
                } else {
                    enable
                }
            } else {
                enable
            }
        }

        stopServiceOnTaskRemoved = meta?.getBoolean(KEY_STOP_SERVICE_ON_TASK_REMOVED, true) ?: true

        if (!enableForeground) {
            ifForegroundExitProcessOnDestroyWhenTaskRemoved = false
            notificationId = 0
            channelId = ""
            channelName = ""
            channelDescription = ""
            channelShowBadge = false
            _iconNetworkEnabled = DrawableRes(0)
            _iconNetworkDisabled = DrawableRes(0)
            _iconDataXfer = DrawableRes(0)
            _iconError = DrawableRes(0)
            _colorWhenBootstrappedTrue = ColorRes(android.R.color.white)
            _colorWhenBootstrappedFalse = ColorRes(android.R.color.white)
            visibility = 0
            enableRestartAction = false
            enableStopAction = false
        } else {
            if (meta == null) {
                throw Resources.NotFoundException(
                    """
                        Something went terribly wrong.

                        AndroidManifest.xml <meta-data> flag for running TorService in
                        the foreground has been set to `true`, but ApplicationInfo
                        meta-data is null.

                        This should _never_ happen...

                        See Examples Here:
                        - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                        - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                    """.trimIndent()
                )
            }

            ifForegroundExitProcessOnDestroyWhenTaskRemoved = meta.getBoolean(
                KEY_IF_FOREGROUND_EXIT_PROCESS_ON_DESTROY_WHEN_TASK_REMOVED,
                true
            )

            notificationId = meta.getInt(KEY_NOTIFICATION_ID, 0).let { id ->
                if (id !in 1..9999) {
                    throw Resources.NotFoundException(
                        """
                            AndroidManifest.xml <meta-data> missing or erroneous for: $KEY_NOTIFICATION_ID
                            
                            Please add the following to AndroidManifest.xml:
                            ```
                            <application>
                                <meta-data
                                    android:name="$KEY_NOTIFICATION_ID"
                                    android:value="@integer/tor_service_notification_id" />
                            </application>
                            ```
                            
                            And the following to value/attrs.xml:
                            ```
                            <resources>
                                <integer name="tor_service_notification_id">**VALUE BETWEEN 1 AND 9999**</integer>
                            </resources>
                            ```
                            
                            See Examples Here:
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                        """.trimIndent()
                    )
                }

                id
            }

            channelId = meta.getString(KEY_CHANNEL_ID).let { id ->
                if (id.isNullOrEmpty()) {
                    throw Resources.NotFoundException(
                        """
                            AndroidManifest.xml <meta-data> missing or erroneous for: $KEY_CHANNEL_ID
                            
                            Please add the following to AndroidManifest.xml:
                            ```
                            <application>
                                <meta-data
                                    android:name="$KEY_CHANNEL_ID"
                                    android:value="@string/tor_service_notification_channel_id" />
                            </application>
                            ```
                            
                            And the following to value/attrs.xml:
                            ```
                            <resources>
                                <string name="tor_service_notification_channel_id">**NON-EMPTY VALUE**</string>
                            </resources>
                            ```
                            
                            See Examples Here:
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                        """.trimIndent()
                    )
                }

                id
            }

            channelName = meta.getString(KEY_CHANNEL_NAME).let { name ->
                if (name.isNullOrEmpty()) {
                    throw Resources.NotFoundException(
                        """
                            AndroidManifest.xml <meta-data> missing or erroneous for: $KEY_CHANNEL_NAME
                            
                            Please add the following to AndroidManifest.xml:
                            ```
                            <application>
                                <meta-data
                                    android:name="$KEY_CHANNEL_NAME"
                                    android:value="@string/tor_service_notification_channel_name" />
                            </application>
                            ```
                            
                            And the following to value/attrs.xml:
                            ```
                            <resources>
                                <string name="tor_service_notification_channel_name">**NON-EMPTY VALUE**</string>
                            </resources>
                            ```
                            
                            See Examples Here:
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                        """.trimIndent()
                    )
                }

                name
            }

            channelDescription = meta.getString(KEY_CHANNEL_DESCRIPTION).let { description ->
                if (description.isNullOrEmpty()) {
                    throw Resources.NotFoundException(
                        """
                            AndroidManifest.xml <meta-data> missing or erroneous for: $KEY_CHANNEL_DESCRIPTION
                            
                            Please add the following to AndroidManifest.xml:
                            ```
                            <application>
                                <meta-data
                                    android:name="$KEY_CHANNEL_DESCRIPTION"
                                    android:value="@string/tor_service_notification_channel_description" />
                            </application>
                            ```
                            
                            And the following to value/attrs.xml:
                            ```
                            <resources>
                                <string name="tor_service_notification_channel_description">**NON-EMPTY VALUE**</string>
                            </resources>
                            ```
                            
                            See Examples Here:
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                        """.trimIndent()
                    )
                }

                description
            }

            channelShowBadge = meta.getBoolean(KEY_CHANNEL_SHOW_BADGE, false)

            _iconNetworkEnabled = meta.getInt(KEY_ICON_NETWORK_ENABLED, 0).let { resId ->
                if (resId < 1) {
                    throw Resources.NotFoundException(
                        """
                            AndroidManifest.xml <meta-data> missing or erroneous for: $KEY_ICON_NETWORK_ENABLED
                            
                            Please add the following to AndroidManifest.xml:
                            ```
                            <application>
                                <meta-data
                                    android:name="$KEY_ICON_NETWORK_ENABLED"
                                    android:resource="@drawable/tor_service_notification_icon_network_enabled" />
                            </application>
                            ```
                            
                            And the following to value/attrs.xml:
                            ```
                            <resources>
                                <drawable name="tor_service_notification_icon_network_enabled">@drawable/your_drawable_resource</drawable>
                            </resources>
                            ```
                            
                            See Examples Here:
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                        """.trimIndent()
                    )
                }

                if (!DrawableRes(resId).isValid(context)) {
                    throw Resources.NotFoundException(
                        """
                            Drawable resource not found for: $KEY_ICON_NETWORK_ENABLED
                            
                            AndroidManifest.xml <meta-data> flag was present, but resource
                            retrieval failed.
                        """.trimIndent()
                    )
                }

                DrawableRes(resId)
            }

            _iconError = meta.getInt(KEY_ICON_ERROR, 0).let { resId ->
                if (resId < 1) {
                    throw Resources.NotFoundException(
                        """
                            AndroidManifest.xml <meta-data> missing or erroneous for: $KEY_ICON_ERROR
                            
                            Please add the following to AndroidManifest.xml:
                            ```
                            <application>
                                <meta-data
                                    android:name="$KEY_ICON_ERROR"
                                    android:resource="@drawable/tor_service_notification_icon_error" />
                            </application>
                            ```
                            
                            And the following to value/attrs.xml:
                            ```
                            <resources>
                                <drawable name="tor_service_notification_icon_error">@drawable/your_drawable_resource</drawable>
                            </resources>
                            ```
                            
                            See Examples Here:
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                            - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                        """.trimIndent()
                    )
                }

                if (!DrawableRes(resId).isValid(context)) {
                    throw Resources.NotFoundException(
                        """
                            Drawable resource not found for: $KEY_ICON_ERROR
                            
                            AndroidManifest.xml <meta-data> flag was present, but resource
                            retrieval failed.
                        """.trimIndent()
                    )
                }

                DrawableRes(resId)
            }

            _iconNetworkDisabled = meta.getInt(KEY_ICON_NETWORK_DISABLED, 0).let { resId ->
                when {
                    resId < 1 -> _iconNetworkEnabled
                    !DrawableRes(resId).isValid(context) -> {
                        throw Resources.NotFoundException(
                            """
                                Drawable resource not found for: $KEY_ICON_NETWORK_DISABLED
                                
                                AndroidManifest.xml <meta-data> flag was present, but resource
                                retrieval failed.
                            """.trimIndent()
                        )
                    }
                    else -> DrawableRes(resId)
                }
            }

            _iconDataXfer = meta.getInt(KEY_ICON_DATA_XFER, 0).let { resId ->
                when {
                    resId < 1 -> _iconNetworkEnabled
                    !DrawableRes(resId).isValid(context) -> {
                        throw Resources.NotFoundException(
                            """
                                Drawable resource not found for: $KEY_ICON_DATA_XFER
                                
                                AndroidManifest.xml <meta-data> flag was present, but resource
                                retrieval failed.
                            """.trimIndent()
                        )
                    }
                    else -> DrawableRes(resId)
                }
            }

            _colorWhenBootstrappedTrue = meta.getInt(KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE, 0).let { resId ->
                when {
                    resId < 1 -> ColorRes(android.R.color.white)
                    !ColorRes(resId).isValid(context) -> {
                        throw Resources.NotFoundException(
                            """
                                Color resource not found for: $KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE
                                
                                AndroidManifest.xml <meta-data> flag was present, but resource
                                retrieval failed.
                            """.trimIndent()
                        )
                    }
                    else -> ColorRes(resId)
                }
            }

            _colorWhenBootstrappedFalse = meta.getInt(KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE, 0).let { resId ->
                when {
                    resId < 1 -> ColorRes(android.R.color.white)
                    !ColorRes(resId).isValid(context) -> {
                        throw Resources.NotFoundException(
                            """
                                Color resource not found for: $KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE
                                
                                AndroidManifest.xml <meta-data> flag was present, but resource
                                retrieval failed.
                            """.trimIndent()
                        )
                    }
                    else -> ColorRes(resId)
                }
            }

            visibility = meta.getString(KEY_VISIBILITY).let { visibility ->
                when (visibility?.lowercase()) {
                    "public" -> /* Notification.VISIBILITY_PUBLIC */ 1
                    "secret" -> /* Notification.VISIBILITY_SECRET */ -1
                    null, "private" -> /* Notification.VISIBILITY_PRIVATE */ 0
                    else -> {
                        throw Resources.NotFoundException(
                            """
                                AndroidManifest.xml <meta-data> missing or erroneous for: $KEY_VISIBILITY
                                
                                Incorrect value set: $visibility
                                Accepted values: null, public, secret, private
                                Defaults to private if unset or null
                                
                                Please add the following to AndroidManifest.xml:
                                ```
                                <application>
                                    <meta-data
                                        android:name="$KEY_VISIBILITY"
                                        android:value="@string/tor_service_notification_visibility" />
                                </application>
                                ```
                                
                                And the following to value/attrs.xml:
                                ```
                                <resources>
                                    <string name="tor_service_notification_visibility">**VALUE HERE**</string>
                                </resources>
                                ```
                                
                                See Examples Here:
                                - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/AndroidManifest.xml
                                - https://github.com/05nelsonm/kmp-tor/blob/master/samples/android/src/main/res/values/attrs.xml
                            """.trimIndent()
                        )
                    }
                }
            }

            enableRestartAction = meta.getBoolean(KEY_ACTION_ENABLE_RESTART, false)
            enableStopAction = meta.getBoolean(KEY_ACTION_ENABLE_STOP, false)
        }
    }

    companion object {
        private const val BASE = "io.matthewnelson.kmp.tor"

        const val KEY_ENABLE_FOREGROUND = "$BASE.enable_foreground"
        const val KEY_STOP_SERVICE_ON_TASK_REMOVED = "$BASE.stop_service_on_task_removed"
        const val KEY_IF_FOREGROUND_EXIT_PROCESS_ON_DESTROY_WHEN_TASK_REMOVED = "$BASE.if_foreground_exit_process_on_destroy_when_task_removed"

        const val KEY_NOTIFICATION_ID = "$BASE.notification_id"
        const val KEY_CHANNEL_ID = "$BASE.notification_channel_id"
        const val KEY_CHANNEL_NAME = "$BASE.notification_channel_name"
        const val KEY_CHANNEL_DESCRIPTION = "$BASE.notification_channel_description"
        const val KEY_CHANNEL_SHOW_BADGE = "$BASE.notification_channel_show_badge"

        const val KEY_ICON_NETWORK_ENABLED = "$BASE.notification_icon_network_enabled"
        const val KEY_ICON_NETWORK_DISABLED = "$BASE.notification_icon_network_disabled"
        const val KEY_ICON_DATA_XFER = "$BASE.notification_icon_data_xfer"
        const val KEY_ICON_ERROR = "$BASE.notification_icon_error"

        const val KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE = "$BASE.notification_color_when_bootstrapped_true"
        const val KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE = "$BASE.notification_color_when_bootstrapped_false"

        const val KEY_VISIBILITY = "$BASE.notification_visibility"

        const val KEY_ACTION_ENABLE_RESTART = "$BASE.notification_action_enable_restart"
        const val KEY_ACTION_ENABLE_STOP = "$BASE.notification_action_enable_stop"
    }
}
