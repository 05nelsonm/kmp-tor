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
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Config
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.InstanceState
import io.matthewnelson.kmp.tor.runtime.service.internal.register
import kotlinx.coroutines.CoroutineScope

/**
 * Core `androidMain` abstraction which enables implementors the ability to
 * create a fully customized notifications for the running instances of
 * [TorRuntime] as they operate within [TorService].
 *
 * Alternatively, use the default implementation `kmp-tor:runtime-service-ui`
 * dependency, [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI].
 *
 * This class' API is designed as follows:
 *  - [Factory]: To be used for all [TorRuntime.ServiceFactory] instances and
 *   injected into [TorService] upon creation.
 *      - Context: `SINGLETON`
 *  - [AbstractTorServiceUI]: To be created via [Factory.newInstanceUIProtected]
 *   upon [TorService] start.
 *      - Context: `SERVICE`
 *  - [InstanceState]: To be created via [AbstractTorServiceUI.newInstanceStateProtected]
 *   for every instance of [Lifecycle.DestroyableTorRuntime] operating within
 *   [TorService].
 *      - Context: `INSTANCE`
 *
 * @throws [IllegalStateException] on instantiation if [args] were not those
 *   which were passed to [Factory.newInstanceUI]. See [Args].
 * */
public abstract class TorServiceUI<C: Config, IS: InstanceState<C>>
@ExperimentalKmpTorApi
@Throws(IllegalStateException::class)
protected constructor(
    args: Args,
): AbstractTorServiceUI<TorServiceUI.Args, C, IS>(
    args,
    INIT,
) {

    /**
     * Holder for customized input from consumers of `kmp-tor-service`
     * for the Foreground Service's [Notification].
     *
     * @see [of]
     * */
    public class NotificationInfo private constructor(
        @JvmField
        public val channelID: String,
        @JvmField
        public val channelName: String,
        @JvmField
        public val channelDescription: String,
        @JvmField
        public val channelShowBadge: Boolean,
        @JvmField
        public val notificationID: Int,
    ) {

        public companion object {

            @JvmStatic
            public fun of(
                channelID: String,
                channelName: String,
                channelDescription: String,
                channelShowBadge: Boolean,
                notificationID: Int,
            ): NotificationInfo {
                // TODO: Validate

                return NotificationInfo(
                    channelID,
                    channelName,
                    channelDescription,
                    channelShowBadge,
                    notificationID,
                )
            }
        }
    }

    private val service: Context = args.service()
    private val info: NotificationInfo = args.info()
    @Volatile
    private var _hasStartedForeground: Boolean = false
    @Volatile
    private var _isStoppingForeground: Boolean = false
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
        if (_isStoppingForeground) return

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

    /**
     * Core `androidMain` abstraction for a [Factory] class which is
     * responsible for instantiating new instances of [TorServiceUI]
     * when requested by [TorService].
     *
     * Implementations are encouraged to keep it as a subclass within,
     * and use a `private constructor` for, their [UI] implementations.
     *
     * As an example implementation, see
     * [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI.Factory]
     * */
    public abstract class Factory<C: Config, IS: InstanceState<C>, UI: TorServiceUI<C, IS>>
    @ExperimentalKmpTorApi
    protected constructor(
        defaultConfig: C,
        @JvmField
        public val info: NotificationInfo
    ): AbstractTorServiceUI.Factory<Args, C, IS, UI>(defaultConfig, INIT) {

        /**
         * Implementations **MUST** ensure all resources specified in their
         * given [Config] implementations are valid. This is called from
         * [TorServiceConfig.Foreground.Builder], as well as
         * [TorServiceConfig.Foreground.newEnvironment], in order to raise
         * errors before instantiating the singleton instances.
         * */
        @Throws(Resources.NotFoundException::class)
        public abstract fun validate(context: Context, config: C)
    }


    @JvmSynthetic
    internal fun stopForeground() {
        _isStoppingForeground = true

        @OptIn(InternalKmpTorApi::class)
        val stopForeground = synchronized(lock) {
            val wasNotNull = manager != null
            manager = null

            if (!_hasStartedForeground) {
                // Notification.post was never called, so startForeground
                // never triggered. Setting this to `true` will modify future
                // Notification.post behavior so that startForeground is NOT
                // called, it will use the NotificationManager (which was
                // de-referenced above).
                _hasStartedForeground = true
                false
            } else {
                // Notification.post has been called before, the first of which
                // had called Service.startForeground.
                //
                // If manager was not null, then this is first call to stopForeground
                // and Service is about to stop (e.g. onTaskRemoved).
                wasNotNull
            }
        }

        if (!stopForeground) return
        if (service !is Service) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+
            service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            // API 32-
            @Suppress("DEPRECATION")
            service.stopForeground(/* removeNotification */true)
        }
    }

    /**
     * `androidMain` implementation for passing arguments in an encapsulated
     * manner when instantiating new instances of [TorServiceUI] implementations.
     *
     * [Args] are single use items and must be consumed only once, otherwise
     * an exception is raised when [Factory.newInstanceUI] is called resulting
     * a service start failure.
     * */
    public class Args private constructor(
        defaultConfig: Config,
        private val _info: NotificationInfo,
        private val _service: Context,
        serviceScope: CoroutineScope,
    ): AbstractTorServiceUI.Args.UI(
        defaultConfig,
        serviceScope,
        INIT,
    ) {

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
                serviceScope: CoroutineScope,
            ): Args = Args(
                defaultConfig,
                info,
                service,
                serviceScope,
            )
        }
    }
}
