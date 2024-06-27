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

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.service.internal.register
import kotlinx.coroutines.Job

/**
 * Core abstraction for Android which enables implementors the ability
 * to create a fully customized notification for the running instances of
 * [TorRuntime] as they operate within [TorService].
 *
 * Alternatively, use the default implementation `kmp-tor:runtime-service-ui`
 * dependency, [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI].
 *
 * @see [TorServiceConfig.Foreground]
 * */
@OptIn(ExperimentalKmpTorApi::class)
public abstract class AndroidTorServiceUI<C: AndroidTorServiceUI.Config>
@ExperimentalKmpTorApi
@Throws(IllegalStateException::class)
protected constructor(
    args: Args,
): TorServiceUI<AndroidTorServiceUI.Args, C>(
    args,
    args.defaultConfig(),
    INIT,
) {

    private val service: Context = args.service()
    private val info: NotificationInfo = args.info()
    @Volatile
    private var _hasStartedForeground: Boolean = false
    @Volatile
    private var manager: NotificationManager? = service
        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()

    /**
     * Application [Context] for resolving android resources and creating
     * [Notification.Builder]. This **MUST NOT** be utilized when posting
     * [Notification] updates and registering [BroadcastReceiver]. Use
     * [post] and [register] functions which will be executed within the
     * context of the [android.app.Service].
     * */
    @JvmField
    protected val appContext: Context = service.applicationContext

    /**
     * The [NotificationInfo.channelID] for creating [Notification.Builder].
     * */
    @JvmField
    protected val channelID: String = info.channelID

    /**
     * Posts the [Notification].
     * */
    protected fun Notification.post() {
        val startForeground = if (_hasStartedForeground) {
            false
        } else {
            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) {
                if (_hasStartedForeground) {
                    false
                } else {
                    _hasStartedForeground = true
                    true
                }
            }
        }

        if (isDestroyed()) return
        if (startForeground && service is Service) {
            // API 29+ will pull foregroundServiceType from whatever was
            // declared in the manifest.
            service.startForeground(info.notificationID, this)
        } else {
            manager?.notify(info.notificationID, this)
        }
    }

    /**
     * Registers a [BroadcastReceiver] with the [Service] context (which
     * is not exposed to implementors).
     *
     * @param [flags] Only utilized if API 26+. Default 0 (none).
     * @param [exported] Only utilized if non-null and API 33+, adding flag
     *   [Context.RECEIVER_EXPORTED] or [Context.RECEIVER_NOT_EXPORTED]
     *   automatically. This **must** be non-null if the receiver is not being
     *   registered for system broadcasts, otherwise a [SecurityException]
     *   will be thrown on API 34+.
     * @see [Context.registerReceiver]
     * */
    @JvmOverloads
    protected fun BroadcastReceiver.register(
        filter: IntentFilter,
        permission: String?,
        scheduler: Handler?,
        exported: Boolean?,
        flags: Int = 0,
    ): Intent? = service.register(
        this,
        filter,
        permission,
        scheduler,
        exported,
        flags,
    )

    /**
     * Unregisters a [BroadcastReceiver] from the [android.app.Service]
     * context (which is not exposed to implementors).
     * */
    protected fun BroadcastReceiver.unregister() {
        service.unregisterReceiver(this)
    }

    @JvmSynthetic
    internal fun stopForeground() {
        @OptIn(InternalKmpTorApi::class)
        val stopForeground = synchronized(lock) {
            val wasNotNull = manager != null
            manager = null

            if (!_hasStartedForeground) {
                // Manager set to null above which will simply not post
                // anymore notification updates. Nothing else to do.
                _hasStartedForeground = true
                false
            } else {
                // if not null, this is first call where manager reference
                // was released.
                wasNotNull
            }
        }

        if (!stopForeground) return
        if (service !is Service) return
        if (isDestroyed()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            // API 32-
            @Suppress("DEPRECATION")
            service.stopForeground(/* removeNotification */true)
        }
    }

    public class Args private constructor(
        private val _defaultConfig: Config,
        private val _info: NotificationInfo,
        private val _service: Context,
        serviceJob: Job,
    ): TorServiceUI.Args(serviceJob) {

        @JvmSynthetic
        @Suppress("UNCHECKED_CAST")
        internal fun <C: Config> defaultConfig(): C = _defaultConfig as C
        @JvmSynthetic
        internal fun info(): NotificationInfo = _info
        @JvmSynthetic
        internal fun service(): Context = _service

        internal companion object {

            @JvmSynthetic
            internal fun of(
                defaultConfig: Config,
                info: NotificationInfo,
                service: Context,
                serviceJob: Job,
            ): Args = Args(
                defaultConfig,
                info,
                service,
                serviceJob,
            )
        }
    }

    public abstract class Config
    @ExperimentalKmpTorApi
    @Throws(IllegalArgumentException::class)
    protected constructor(fields: Set<Field>): TorServiceUI.Config(fields) {

        /**
         * Implementations **MUST** ensure all resources specified
         * are valid for their given implementation. This is simply
         * a runtime check that is performed from [TorServiceConfig]
         * before instantiating new [TorRuntime.Environment] and
         * [TorServiceConfig.Foreground] instances to ensure errors
         * are caught and handled immediately.
         * */
        @Throws(Resources.NotFoundException::class)
        public abstract fun validate(app: Application)
    }

    /**
     * Core abstraction for Android which enables [TorService] the
     * ability to create new instances of [UI].
     * */
    public abstract class Factory<C: Config, UI: AndroidTorServiceUI<C>>
    @ExperimentalKmpTorApi
    protected constructor(
        defaultConfig: C,

        /**
         * Information for the notification. If API 26+, an
         * [android.app.NotificationChannel] is automatically set up using
         * the provided [NotificationInfo] when the [Factory] implementation
         * is utilized to instantiate [TorServiceConfig.Foreground].
         * */
        @JvmField
        public val info: NotificationInfo
    ): TorServiceUI.Factory<Args, C, UI>(defaultConfig, INIT)
}
