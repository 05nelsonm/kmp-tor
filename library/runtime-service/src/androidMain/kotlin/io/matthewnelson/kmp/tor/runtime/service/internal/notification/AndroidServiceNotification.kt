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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.os.Build
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentAction
import io.matthewnelson.kmp.tor.runtime.service.internal.retrieveColor
import io.matthewnelson.kmp.tor.runtime.service.internal.retrieveString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

internal class AndroidServiceNotification
@Throws(IllegalStateException::class)
private constructor(
    private val service: Context,
    private val config: TorServiceConfig,
    serviceScope: CoroutineScope,
): ServiceNotification(
    serviceScope,
    Synthetic.INIT
) {

    @Volatile
    private var builder: Notification.Builder
    private val manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val startTime = System.currentTimeMillis()

    override fun InstanceView.remove() {
        // TODO("Not yet implemented")
    }

    override fun InstanceView.render(old: NotificationState, new: NotificationState) {
        // TODO: Only update active view (more than 1 instance)
        if (new.isActionRefreshNeeded(old)) {
            notify(service.newBuilder(new))
            return
        }

        val builder = builder

        new.diff(
            other = old,
            setColor = { res ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    // API 21+
                    builder.setColor(service.retrieveColor(res))
                }
            },
            setContentText = { text ->
                builder.setContentText(service.retrieveString(text))
            },
            setContentTitle = { title ->
                builder.setContentTitle(service.retrieveString(title))
            },
            setProgress = { progress ->
                // TODO
            },
            setIcon = { icon ->
                builder.setSmallIcon(icon.id)
            },
        )

        notify(builder)
    }

    private fun Context.newBuilder(state: NotificationState): Notification.Builder {
        // TODO: RemoteViews

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26+
            Notification.Builder(this, config.channelId)
        } else {
            // API 25-
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.apply {
            setContentText(retrieveString(state.text))
            setContentTitle(retrieveString(state.title))
            setOngoing(true)
            setOnlyAlertOnce(true)
            setSmallIcon(state.icon.id)
            setWhen(startTime)

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
                setColor(retrieveColor(state.color))
                setVisibility(config.visibility)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+
                setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                setTimeoutAfter(10L)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
        }

        // TODO: ButtonActions
        // TODO: Progress

        run {
            val name = packageName ?: return@run

            val launchIntent = packageManager
                ?.getLaunchIntentForPackage(name)
                ?: return@run

            val contentIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                FLAGS_PENDING_INTENT
            )

            builder.setContentIntent(contentIntent)
        }

        return builder
    }

    private fun notify(builder: Notification.Builder) {
        this.builder = builder
        manager?.notify(config.notificationId, builder.build())
    }

    init {
        builder = service.newBuilder(NotificationState.of(
            actions = emptySet(),
            color = config.defaults().colorWhenBootstrappedFalse,
            icon = config.defaults().iconNetworkDisabled,
            progress = Progress.Indeterminate,
            text = ContentAction.of(Action.StartDaemon),
            title = TorState.Daemon.Starting,
            fid = object : FileID { override val fid: String = "" }
        ))

        run {
            if (isChannelSetup) return@run
            isChannelSetup = true

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@run

            // API 26+
            val channel = NotificationChannel(
                config.channelId,
                config.channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setShowBadge(config.channelShowBadge)
                description = config.channelDescription
                setSound(null, null)
            }

            manager?.createNotificationChannel(channel)
        }

        if (service is Service) {
            val notification = builder.build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+
                service.startForeground(
                    config.notificationId,
                    notification,
                    // TODO: API 34+ Issue #457
                    0,
                )
            } else {
                // API 28-
                service.startForeground(config.notificationId, notification)
            }

            serviceScope.coroutineContext.job.invokeOnCompletion {
                // TODO: need to do this before onDestroy is called,
                //  after all connections are unbound.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // API 33+
                    service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    // API 32-
                    @Suppress("DEPRECATION")
                    service.stopForeground(true)
                }
            }
        } else {
            // Testing
            notify(builder)
            isChannelSetup = false
        }
    }

    internal companion object {

        private var isChannelSetup = false

        private val FLAGS_PENDING_INTENT = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            // API 30-
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun of(
            service: Context,
            config: TorServiceConfig,
            serviceScope: CoroutineScope,
        ): AndroidServiceNotification? {
            if (!config.enableForeground) return null

            return AndroidServiceNotification(
                service,
                config,
                serviceScope,
            )
        }
    }
}
