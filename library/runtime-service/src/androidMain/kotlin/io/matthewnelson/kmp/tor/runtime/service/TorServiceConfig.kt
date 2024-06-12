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
package io.matthewnelson.kmp.tor.runtime.service

import android.content.Context
import android.content.res.Resources
import io.matthewnelson.kmp.tor.runtime.service.internal.ColorRes
import io.matthewnelson.kmp.tor.runtime.service.internal.DrawableRes
import io.matthewnelson.kmp.tor.runtime.service.internal.RealTorServiceConfig

/**
 * Configuration options for running [TorService] in the foreground.
 *
 * By default, [TorService] runs in the background and without any
 * configuration necessary. Configuration is passed via `<meta-data>`
 * tags within the `AndroidManifest.xml` `<application>` block.
 *
 * See the [README.md](https://github.com/05nelsonm/kmp-tor/blob/master/library/runtime-service/README.md)
 *
 * @see [getMetaData]
 * */
public abstract class TorServiceConfig internal constructor(
    @JvmField
    public val enableForeground: Boolean,
    @JvmField
    public val stopServiceOnTaskRemoved: Boolean,
    @JvmField
    public val ifForegroundExitProcessOnDestroyWhenTaskRemoved: Boolean,
    @JvmField
    public val notificationId: Int,
    @JvmField
    public val channelId: String,
    @JvmField
    public val channelName: String,
    @JvmField
    public val channelDescription: String,
    @JvmField
    public val channelShowBadge: Boolean,
    @JvmField
    public val iconNetworkEnabled: Int,
    @JvmField
    public val iconNetworkDisabled: Int,
    @JvmField
    public val iconDataXfer: Int,
    @JvmField
    public val iconError: Int,
    @JvmField
    public val colorWhenBootstrappedTrue: Int,
    @JvmField
    public val colorWhenBootstrappedFalse: Int,
    @JvmField
    public val visibility: Int,
    @JvmField
    public val enableActionRestart: Boolean,
    @JvmField
    public val enableActionStop: Boolean,
) {

    public companion object {

        @Throws(Resources.NotFoundException::class)
        public fun getMetaData(context: Context): TorServiceConfig {
            return RealTorServiceConfig.of(context)
        }
    }

    public final override fun equals(other: Any?): Boolean {
        return  other is TorServiceConfig                                       &&
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
                other.enableActionRestart == enableActionRestart                &&
                other.enableActionStop == enableActionStop
    }

    public final override fun hashCode(): Int {
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
        result = result * 31 + enableActionRestart.hashCode()
        result = result * 31 + enableActionStop.hashCode()
        return result
    }

    public final override fun toString(): String = buildString {
        appendLine("TorServiceConfig: [")
        append("    enableForeground: ")
        appendLine(enableForeground)
        append("    stopServiceOnTaskRemoved: ")
        appendLine(stopServiceOnTaskRemoved)
        append("    ifForegroundExitProcessOnDestroyWhenTaskRemoved: ")
        appendLine(ifForegroundExitProcessOnDestroyWhenTaskRemoved)
        append("    notificationId: ")
        appendLine(notificationId)
        append("    channelId: ")
        appendLine(channelId)
        append("    channelName: ")
        appendLine(channelName)
        append("    channelDescription: ")
        appendLine(channelDescription)
        append("    channelShowBadge: ")
        appendLine(channelShowBadge)
        append("    iconNetworkEnabled: ")
        appendLine(iconNetworkEnabled)
        append("    iconNetworkDisabled: ")
        appendLine(iconNetworkDisabled)
        append("    iconDataXfer: ")
        appendLine(iconDataXfer)
        append("    iconError: ")
        appendLine(iconError)
        append("    colorWhenBootstrappedTrue: ")
        appendLine(colorWhenBootstrappedTrue)
        append("    colorWhenBootstrappedFalse: ")
        appendLine(colorWhenBootstrappedFalse)
        append("    visibility: ")
        appendLine(visibility)
        append("    enableRestartAction: ")
        appendLine(enableActionRestart)
        append("    enableStopAction: ")
        appendLine(enableActionStop)
        append(']')
    }

    internal abstract class MetaData<E: RuntimeException> internal constructor() {

        protected abstract fun getBoolean(key: String, default: Boolean): Boolean
        protected abstract fun getIntOrZero(key: String): Int
        protected abstract fun getString(key: String): String?

        protected abstract fun ColorRes.isValid(): Boolean
        protected abstract fun DrawableRes.isValid(): Boolean

        protected abstract fun hasForegroundServicePermission(): Boolean

        protected abstract fun createException(message: String): E

        internal fun ifForegroundExitProcessOnDestroyWhenTaskRemoved(): Boolean {
            return getBoolean(KEY_IF_FOREGROUND_EXIT_PROCESS_ON_DESTROY_WHEN_TASK_REMOVED, true)
        }

        @Throws(RuntimeException::class)
        internal fun notificationId(): Int {
            val notificationId = getIntOrZero(KEY_NOTIFICATION_ID)
            if (notificationId !in 1..9999) {
                throw misconfigurationException(
                    KEY_NOTIFICATION_ID,
                    "@integer/tor_service_notification_id",
                    "<integer name=\"tor_service_notification_id\">**VALUE BETWEEN 1 AND 9999**</integer>",
                )
            }
            return notificationId
        }

        @Throws(RuntimeException::class)
        internal fun channelId(): String {
            val channelId = getString(KEY_CHANNEL_ID)
            if (channelId.isNullOrEmpty()) {
                throw misconfigurationException(
                    KEY_CHANNEL_ID,
                    "@string/tor_service_notification_channel_id",
                    "<string name=\"tor_service_notification_channel_id\">**NON-EMPTY VALUE**</string>",
                )
            }
            return channelId
        }

        @Throws(RuntimeException::class)
        internal fun channelName(): String {
            val channelName = getString(KEY_CHANNEL_NAME)
            if (channelName.isNullOrEmpty()) {
                throw misconfigurationException(
                    KEY_CHANNEL_NAME,
                    "@string/tor_service_notification_channel_name",
                    "<string name=\"tor_service_notification_channel_name\">**NON-EMPTY VALUE**</string>",
                )
            }
            return channelName
        }

        @Throws(RuntimeException::class)
        internal fun channelDescription(): String {
            val channelDescription = getString(KEY_CHANNEL_DESCRIPTION)
            if (channelDescription.isNullOrEmpty()) {
                throw misconfigurationException(
                    KEY_CHANNEL_DESCRIPTION,
                    "@string/tor_service_notification_channel_description",
                    "<string name=\"tor_service_notification_channel_description\">**NON-EMPTY VALUE**</string>",
                )
            }
            return channelDescription
        }

        internal fun channelShowBadge(): Boolean {
            return getBoolean(KEY_CHANNEL_SHOW_BADGE, false)
        }

        @Throws(RuntimeException::class)
        internal fun iconNetworkEnabled(): DrawableRes {
            val id = getIntOrZero(KEY_ICON_NETWORK_ENABLED)
            if (id < 1) {
                throw misconfigurationException(
                    KEY_ICON_NETWORK_ENABLED,
                    "@drawable/tor_service_notification_icon_network_enabled",
                    "<drawable name=\"tor_service_notification_icon_network_enabled\">@drawable/your_drawable_resource</drawable>",
                )
            }
            val res = DrawableRes.of(id)
            res.checkValid(KEY_ICON_NETWORK_ENABLED)
            return res
        }

        @Throws(RuntimeException::class)
        internal fun iconNetworkDisabled(): DrawableRes? {
            val id = getIntOrZero(KEY_ICON_NETWORK_DISABLED)
            if (id < 1) return null
            val res = DrawableRes.of(id)
            res.checkValid(KEY_ICON_NETWORK_DISABLED)
            return res
        }

        @Throws(RuntimeException::class)
        internal fun iconError(): DrawableRes {
            val id = getIntOrZero(KEY_ICON_ERROR)
            if (id < 1) {
                throw misconfigurationException(
                    KEY_ICON_ERROR,
                    "@drawable/tor_service_notification_icon_error",
                    "<drawable name=\"tor_service_notification_icon_error\">@drawable/your_drawable_resource</drawable>",
                )
            }
            val res = DrawableRes.of(id)
            res.checkValid(KEY_ICON_ERROR)
            return res
        }

        @Throws(RuntimeException::class)
        internal fun iconDataXfer(): DrawableRes? {
            val id = getIntOrZero(KEY_ICON_DATA_XFER)
            if (id < 1) return null
            val res = DrawableRes.of(id)
            res.checkValid(KEY_ICON_DATA_XFER)
            return res
        }

        @Throws(RuntimeException::class)
        internal fun colorWhenBootstrappedTrue(): ColorRes? {
            val id = getIntOrZero(KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE)
            if (id < 1) return null
            val res = ColorRes.of(id)
            res.checkValid(KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE)
            return res
        }

        @Throws(RuntimeException::class)
        internal fun colorWhenBootstrappedFalse(): ColorRes? {
            val id = getIntOrZero(KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE)
            if (id < 1) return null
            val res = ColorRes.of(id)
            res.checkValid(KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE)
            return res
        }

        @Throws(RuntimeException::class)
        internal fun visibility(): Int {
            val name = getString(KEY_VISIBILITY)

            val type = when (name?.lowercase()) {
                "public" -> /* Notification.VISIBILITY_PUBLIC */ 1
                "secret" -> /* Notification.VISIBILITY_SECRET */ -1
                null,
                "null",
                "private" -> /* Notification.VISIBILITY_PRIVATE */ 0
                else -> throw misconfigurationException(
                    KEY_VISIBILITY,
                    "@string/tor_service_notification_visibility",
                    "<string name=\"tor_service_notification_visibility\">**VALUE HERE**</string>",
                    """
                    Unknown value: $name
                    Accepted resource values: @null, null, public, secret, private
                    Defaults to private if unset or null
                    """,
                )
            }

            return type
        }

        internal fun enableActionRestart(): Boolean {
            return getBoolean(KEY_ACTION_ENABLE_RESTART, false)
        }

        internal fun enableActionStop(): Boolean {
            return getBoolean(KEY_ACTION_ENABLE_STOP, false)
        }

        @Throws(RuntimeException::class)
        private fun ColorRes.checkValid(key: String) {
            if (isValid()) return
            throw createException("""
                Color resource not found for: $key
    
                AndroidManifest.xml <meta-data> flag was present, but resource
                retrieval failed.
            """.trimIndent())
        }

        @Throws(RuntimeException::class)
        private fun DrawableRes.checkValid(key: String) {
            if (isValid()) return
            throw createException("""
                Drawable resource not found for: $key
    
                AndroidManifest.xml <meta-data> flag was present, but resource
                retrieval failed.
            """.trimIndent())
        }

        private fun misconfigurationException(
            key: String,
            androidValue: String,
            lineAttrs: String,
            defaults: String? = null
        ): E {
            val d = if (defaults.isNullOrBlank()) "" else defaults

            return createException("""
                AndroidManifest.xml <meta-data> missing or erroneous for: $key
                $d
                Please add the following to AndroidManifest.xml:
                ```
                <application>
                    <meta-data
                        android:name="$key"
                        android:value="$androidValue" />
                </application>
                ```

                And the following to res/values/attrs.xml:
                ```
                <resources>
                    $lineAttrs
                </resources>
                ```

                See the README.md:
                - https://github.com/05nelsonm/kmp-tor/blob/master/library/runtime-service/README.md
            """.trimIndent())
        }

        internal companion object {

            internal fun MetaData<*>?.enableForeground(): Boolean {
                val enable = this?.getBoolean(
                    KEY_ENABLE_FOREGROUND,
                    false
                ) ?: return false

                return enable && hasForegroundServicePermission()
            }

            internal fun MetaData<*>?.stopServiceOnTaskRemoved(): Boolean {
                return this?.getBoolean(KEY_STOP_SERVICE_ON_TASK_REMOVED, true) ?: true
            }

            private const val BASE = "io.matthewnelson.kmp.tor"

            internal const val KEY_ENABLE_FOREGROUND = "$BASE.enable_foreground"
            internal const val KEY_STOP_SERVICE_ON_TASK_REMOVED = "$BASE.stop_service_on_task_removed"
            internal const val KEY_IF_FOREGROUND_EXIT_PROCESS_ON_DESTROY_WHEN_TASK_REMOVED = "$BASE.if_foreground_exit_process_on_destroy_when_task_removed"

            internal const val KEY_NOTIFICATION_ID = "$BASE.notification_id"
            internal const val KEY_CHANNEL_ID = "$BASE.notification_channel_id"
            internal const val KEY_CHANNEL_NAME = "$BASE.notification_channel_name"
            internal const val KEY_CHANNEL_DESCRIPTION = "$BASE.notification_channel_description"
            internal const val KEY_CHANNEL_SHOW_BADGE = "$BASE.notification_channel_show_badge"

            internal const val KEY_ICON_NETWORK_ENABLED = "$BASE.notification_icon_network_enabled"
            internal const val KEY_ICON_NETWORK_DISABLED = "$BASE.notification_icon_network_disabled"
            internal const val KEY_ICON_DATA_XFER = "$BASE.notification_icon_data_xfer"
            internal const val KEY_ICON_ERROR = "$BASE.notification_icon_error"

            internal const val KEY_COLOR_WHEN_BOOTSTRAPPED_TRUE = "$BASE.notification_color_when_bootstrapped_true"
            internal const val KEY_COLOR_WHEN_BOOTSTRAPPED_FALSE = "$BASE.notification_color_when_bootstrapped_false"

            internal const val KEY_VISIBILITY = "$BASE.notification_visibility"

            internal const val KEY_ACTION_ENABLE_RESTART = "$BASE.notification_action_enable_restart"
            internal const val KEY_ACTION_ENABLE_STOP = "$BASE.notification_action_enable_stop"
        }
    }
}
