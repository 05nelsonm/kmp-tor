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
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Config
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.InstanceState
import io.matthewnelson.kmp.tor.runtime.service.internal.isPermissionGranted
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

            /**
             * Verifies input before creating the [NotificationInfo]
             *
             * @throws [IllegalArgumentException] if fields:
             *  - [channelID], [channelName], [channelDescription] exceed length
             *    bounds of 1 to 1000 (i.e. cannot be empty or more than 1000 chars).
             *  - [notificationID] is not between 1 and 9999
             * */
            @JvmStatic
            @Throws(IllegalArgumentException::class)
            public fun of(
                channelID: String,
                channelName: String,
                channelDescription: String,
                channelShowBadge: Boolean,
                notificationID: Int,
            ): NotificationInfo {
                channelID.checkLength { "channelID" }
                channelName.checkLength { "channelName" }
                channelDescription.checkLength { "channelDescription" }
                require(notificationID in 1..9999) {
                    "field[notificationID] must be between 1 and 9999"
                }

                return NotificationInfo(
                    channelID,
                    channelName,
                    channelDescription,
                    channelShowBadge,
                    notificationID,
                )
            }

            @JvmStatic
            @Throws(IllegalArgumentException::class)
            private inline fun String.checkLength(field: () -> String) {
                require(length in 1..1000) {
                    "field[${field()}] must be between 1 and 1000 characters in length"
                }
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
     * Posts the [Notification]. This **MUST** be called upon first
     * [onUpdate] invocation (or sooner) to ensure that the call to
     * [Service.startForeground] is had, otherwise an ANR will result
     * for Android API 26+.
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
     * Callback for creating [BroadcastReceiver] that belong to the [Service]
     * context, without exposing the [Service] to implementors of [TorServiceUI].
     *
     * @see [register]
     * */
    protected fun interface Receiver {
        public fun onReceive(intent: Intent?)
    }

    /**
     * Registers a [BroadcastReceiver] with the [Service] context and pipes
     * the [Intent] from [BroadcastReceiver.onReceive] to [Receiver.onReceive].
     *
     * **NOTE:** Underlying [BroadcastReceiver] are not automatically unregistered.
     * The returned [Disposable] should be invoked when done with the [Receiver],
     * or from [onDestroy] override when this instance of [TorServiceUI] is
     * destroyed. Otherwise, there will be a reference leak.
     *
     * @param [flags] Only utilized if API 26+. Default 0 (none).
     * @param [exported] Only utilized if non-null and API 33+, adding flag
     *   [Context.RECEIVER_EXPORTED] or [Context.RECEIVER_NOT_EXPORTED]
     *   automatically. This **must** be non-null if the receiver is not being
     *   registered for system broadcasts, otherwise a [SecurityException]
     *   will be thrown on API 34+.
     * @return [Disposable.Once] to unregister the underlying [BroadcastReceiver]
     *   or `null` if [TorServiceUI.isDestroyed] was true.
     * @see [Context.registerReceiver]
     * */
    @JvmOverloads
    protected fun Receiver.register(
        filter: IntentFilter,
        permission: String?,
        scheduler: Handler?,
        exported: Boolean?,
        flags: Int = 0,
    ): Disposable.Once? {
        if (isDestroyed()) return null

        var disposable: Disposable.Once? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isDestroyed()) {
                    disposable?.dispose()
                    return
                }

                onReceive(intent)
            }
        }

        service.register(
            receiver,
            filter,
            permission,
            scheduler,
            exported,
            flags,
        )

        return Disposable.Once.of(concurrent = true) {
            try {
                service.unregisterReceiver(receiver)
            } catch (_: Throwable) {}
        }.also { disposable = it }
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
         * Called from [TorServiceConfig.Foreground.Companion.Builder] when
         * creating a new instance of [TorServiceConfig.Foreground].
         *
         * The intended purpose is to validate anything requiring [Context]
         * that the [Factory] maintains outside the [defaultConfig].
         *
         * The [defaultConfig] is also validated directly after this returns,
         * from [TorServiceConfig.Foreground.Companion.Builder].
         *
         * Implementations **MUST** ensure all resources are configured
         * correctly to inhibit an unrecoverable application state if
         * [TorServiceConfig] is allowed to be instantiated with a
         * non-operational component.
         * */
        @Throws(IllegalStateException::class, Resources.NotFoundException::class)
        public abstract fun validate(context: Context)

        /**
         * Called from [TorServiceConfig.Foreground.Companion.Builder] and
         * [TorServiceConfig.Foreground.newEnvironment] when checking the
         * [defaultConfig], or if a stand-alone [Config] is being used to
         * create a new instance of [TorRuntime.Environment].
         *
         * Implementations **MUST** ensure all resources are configured
         * correctly to inhibit an unrecoverable application state if
         * [TorServiceConfig], or [TorRuntime.Environment] is allowed to be
         * instantiated with a non-operational component.
         * */
        @Throws(Resources.NotFoundException::class)
        public abstract fun validateConfig(context: Context, config: C)
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

    protected companion object {

        /**
         * Helper for checking if the provided [permission] string is
         * granted for the calling application PID/UID.
         *
         * @see [Context.checkPermission]
         * */
        @JvmStatic
        public fun Context.hasPermission(permission: String): Boolean = isPermissionGranted(permission)
    }
}
