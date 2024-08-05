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

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build

internal class NotificationBuilder private constructor(
    appContext: Context,
    channelID: String,
    contentIntent: PendingIntent?,
) {

    private val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, channelID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }.apply {
        setOngoing(true)
        setOnlyAlertOnce(true)
        setWhen(System.currentTimeMillis())
        setContentIntent(contentIntent)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) {
            // API 33-
            @Suppress("DEPRECATION")
            setSound(null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // API 17+
            setShowWhen(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            // API 20+
            setGroup("TorService")
            setGroupSummary(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API 21+
            setCategory(Notification.CATEGORY_PROGRESS)
            setVisibility(Notification.VISIBILITY_PUBLIC)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+
            setStyle(Notification.DecoratedCustomViewStyle())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+
            setTimeoutAfter(10L)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+
            setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
    }

    internal fun <T: Any?> withLock(
        block: Notification.Builder.() -> T
    ): T = synchronized(this) { block(instance) }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            context: Context,
            channelID: String,
            contentIntent: PendingIntent?,
        ): NotificationBuilder = NotificationBuilder(
            context.applicationContext,
            channelID,
            contentIntent,
        )
    }
}
