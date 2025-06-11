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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
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
 * create a fully customized notification for the running instances of
 * [TorRuntime] as they operate within [TorService].
 *
 * This class' API is designed as follows:
 *  - [Factory]: To be used for all [TorRuntime.ServiceFactory] instances and
 *   injected into [TorService] upon creation.
 *      - Context: `SINGLETON`
 *  - [TorServiceUI]: To be created via [Factory.createProtected]
 *   upon [TorService] start.
 *      - Context: `SERVICE`
 *  - [InstanceState]: To be created via [AbstractTorServiceUI.createProtected]
 *   for every instance of [Lifecycle.DestroyableTorRuntime] operating within
 *   [TorService].
 *      - Context: `INSTANCE`
 *
 * **NOTE:** This is currently an [ExperimentalKmpTorApi] when extending
 * to create your own implementation. Things may change (as the annotation
 * states), so use at your own risk! Prefer using the stable implementation
 * via the `kmp-tor:runtime-service-ui` dependency.
 *
 * See [KmpTorServiceUI](https://kmp-tor.matthewnelson.io/library/runtime-service-ui/io.matthewnelson.kmp.tor.runtime.service.ui/-kmp-tor-service-u-i/index.html)
 * @throws [IllegalStateException] on instantiation if [args] were not those
 *   which were passed to [Factory.create]. See [Args].
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
     * Holder for Foreground Service [Notification] and [NotificationChannel]
     * configuration info used to instantiate [Factory]. Is validated upon
     * invocation of [TorServiceConfig.Foreground.Companion.Builder].
     * */
    public class NotificationInfo
    @JvmOverloads
    public constructor(

        /**
         * The integer value to utilize when posting the [Notification] to
         * [NotificationManager].
         *
         * Must be between 1 and 9999 (inclusive).
         * */
        @JvmField
        public val notificationId: Short,

        /**
         * The string value to utilize for [NotificationChannel.getId].
         *
         * Must be between 1 and 1000 characters in length.
         * */
        @JvmField
        public val channelId: String,

        /**
         * The id of the string resource to resolve to utilize for the
         * [NotificationChannel] name.
         *
         * Resolved string value must not be empty.
         *
         * The recommended maximum length is 40 characters.
         * */
        @JvmField
        public val channelName: Int,

        /**
         * The id of the string resource to resolve to utilize for the
         * [NotificationChannel] description.
         *
         * Resolved string value must not be empty.
         *
         * The recommended maximum length is 300 characters.
         * */
        @JvmField
        public val channelDescription: Int,

        /**
         * The value to utilize for [NotificationChannel.setShowBadge].
         *
         * Default: `false`
         * */
        @JvmField
        public val channelShowBadge: Boolean = false,

        /**
         * If `true`, [NotificationManager.IMPORTANCE_LOW] will be
         * used for [NotificationChannel.setImportance].
         *
         * If `false`, [NotificationManager.IMPORTANCE_DEFAULT] will
         * be used instead.
         *
         * **NOTE:** The [NotificationChannel] is configured to **NOT**
         * make any noise, even if [NotificationManager.IMPORTANCE_DEFAULT]
         * is being utilized.
         *
         * Default: `false`
         * */
        @JvmField
        public val channelImportanceLow: Boolean = false,
    ) {

        /**
         * Called from [TorServiceConfig.Foreground.Companion.Builder] to
         * resolve string resources and validate parameters for correctness.
         *
         * @return resolved string resource values for [channelName]
         *   and [channelDescription].
         * */
        @Throws(IllegalArgumentException::class, Resources.NotFoundException::class)
        public fun validate(context: Context): Pair<String, String> {
            val cName = context.getString(channelName)
            val cDescription = context.getString(channelDescription)

            require(notificationId in 1..9999) {
                "notificationId must be between 1 and 99999 (inclusive)"
            }
            require(channelId.length in 1..1000) {
                "channelId must be between 1 and 1000 characters in length"
            }
            require(cName.isNotEmpty()) {
                "channelName cannot be empty"
            }
            require(cDescription.isNotEmpty()) {
                "channelDescription cannot be empty"
            }

            return cName to cDescription
        }
    }

    private val service: Context = args.service()
    private val info: NotificationInfo = args.info()

    @Volatile
    private var _hasStartedForeground: Boolean = false
    @Volatile
    private var _manager: NotificationManager? = service
        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @OptIn(InternalKmpTorApi::class)
    private val lock = synchronizedObject()

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
     * The [NotificationInfo.channelId] for instantiating [Notification.Builder].
     * */
    @JvmField
    protected val channelId: String = info.channelId

    /**
     * Posts the [Notification] to [NotificationManager]. This **MUST**
     * be called upon first [onRender] invocation (or sooner) to ensure
     * that the call to [Service.startForeground] is had, otherwise an ANR
     * will result for Android API 26+.
     *
     * **NOTE:** [Notification.priority], [Notification.audioAttributes],
     * [Notification.audioStreamType], and [Notification.sound] are always
     * set for each invocation of [post].
     *
     * @throws [IllegalThreadStateException] if called from non-main thread.
     * */
    @Throws(IllegalThreadStateException::class)
    protected fun Notification.post() {
        Looper.getMainLooper().let { main ->
            if (Thread.currentThread() == main.thread) return@let
            throw IllegalThreadStateException("Notification.post() must be called from the main thread")
        }

        @Suppress("DEPRECATION")
        run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // API 21+
                audioAttributes = null
            }

            audioStreamType = Notification.STREAM_DEFAULT
            sound = null

            priority = if (info.channelImportanceLow) {
                Notification.PRIORITY_LOW
            } else {
                Notification.PRIORITY_DEFAULT
            }
        }

        if (isDestroyed()) return
        if (_manager == null) return

        val id = info.notificationId.toInt()

        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            if (isDestroyed()) return
            val manager = _manager ?: return

            val startForeground = if (!_hasStartedForeground) {
                _hasStartedForeground = true
                true
            } else {
                false
            }

            if (startForeground && service is Service) {
                // API 29+ will pull foregroundServiceType from
                // whatever has been declared in the manifest.
                service.startForeground(id, this)
            } else {
                manager.notify(id, this)
            }
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

    public open fun onConfigurationChanged(newConfig: Configuration) {}

    /**
     * Core `androidMain` abstraction for a [Factory] class which is
     * responsible for instantiating new instances of [TorServiceUI]
     * when requested by [TorService].
     *
     * **NOTE:** This is currently an [ExperimentalKmpTorApi] when extending
     * to create your own implementation. Things may change (as the annotation
     * states), so use at your own risk! Prefer using the stable implementation
     * via the `kmp-tor:runtime-service-ui` dependency.
     *
     * Implementations are encouraged to keep it as a subclass within,
     * and use a `private constructor` for, their [UI] implementations.
     *
     * See [KmpTorServiceUI.Factory](https://kmp-tor.matthewnelson.io/library/runtime-service-ui/io.matthewnelson.kmp.tor.runtime.service.ui/-kmp-tor-service-u-i/-factory/index.html)
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
         * that the [Factory] was instantiated with.
         *
         * The [defaultConfig] and [info] are validated directly after this
         * function is called and do not need to be validated here.
         *
         * Implementations **MUST** ensure all resources are configured
         * correctly to inhibit an unrecoverable application state if
         * [TorServiceConfig] is allowed to be instantiated with a
         * non-operational component or resource.
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
         * instantiated with a non-operational component or resource.
         * */
        @Throws(IllegalArgumentException::class, Resources.NotFoundException::class)
        public abstract fun validateConfig(context: Context, config: C)
    }

    /**
     * `androidMain` implementation for passing arguments in an encapsulated
     * manner when instantiating new instances of [TorServiceUI] implementations.
     *
     * [Args] are single use items and must be consumed only once, otherwise
     * an exception is raised when [Factory.create] is called resulting
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

    @JvmSynthetic
    internal fun stopForeground() {

        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            val isFirstInvocation = _manager != null
            _manager = null

            if (service !is Service) return
            if (!_hasStartedForeground) return
            if (!isFirstInvocation) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // API 33+
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                // API 32-
                @Suppress("DEPRECATION")
                service.stopForeground(/* removeNotification */true)
            }
        }
    }
}
