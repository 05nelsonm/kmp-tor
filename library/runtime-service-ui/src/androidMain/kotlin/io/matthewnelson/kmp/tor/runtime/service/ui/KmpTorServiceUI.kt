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
@file:Suppress("PropertyName", "CanBePrimaryConstructorProperty")

package io.matthewnelson.kmp.tor.runtime.service.ui

import android.app.KeyguardManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI
import io.matthewnelson.kmp.tor.runtime.service.TorServiceUI
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.*
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.DrawableRes
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.IconState
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.Progress
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.retrieveDrawable
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.retrieveString
import kotlinx.coroutines.*
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * The "default" UI implementation for `kmp-tor:runtime-service`, serving
 * both as an example for others to model their own implementations after,
 * or to utilize with [TorServiceConfig.Foreground.Companion.Builder].
 *
 * @see [Factory]
 * */
@OptIn(ExperimentalKmpTorApi::class)
public class KmpTorServiceUI private constructor(
    actionIcons: UIAction.Icons,
    actionIntentPermissionSuffix: String?,
    contentIntentCode: Int,
    contentIntent: (code: Int, context: Context) -> PendingIntent?,
    args: Args,
): TorServiceUI<
    KmpTorServiceUI.Config,
    KmpTorServiceUIInstanceState<KmpTorServiceUI.Config>,
>(args) {

    /**
     * Configuration to be utilized with [Factory], or on a per-instance
     * basis via [TorServiceConfig.Foreground.newEnvironment].
     *
     * @see [Config.BuilderScope]
     * */
    public class Config private constructor(
        internal val _colorReady: ColorRes?,
        internal val _iconReady: DrawableRes,
        internal val _iconNotReady: DrawableRes,
        internal val _iconData: DrawableRes,
        enableActionRestart: Boolean,
        enableActionStop: Boolean,
        displayName: DisplayName,
        init: Any
    ): AbstractKmpTorServiceUIConfig(
        enableActionRestart,
        enableActionStop,
        fields = mapOf(
            "colorReady" to _colorReady,
            "displayName" to displayName,
            "iconReady" to _iconReady,
            "iconNotReady" to _iconNotReady,
            "iconData" to _iconData,
        ),
        init,
    ) {

        /**
         * Create a new [Config] with the default options.
         *
         * @param [iconReady] See [BuilderScope.iconReady]
         * @param [iconNotReady] See [BuilderScope.iconNotReady]
         * */
        public constructor(
            iconReady: Int,
            iconNotReady: Int,
        ): this(
            iconReady,
            iconNotReady,
            {},
        )

        /**
         * Create and new [Config] and configure options via [block].
         *
         * @param [iconReady] See [BuilderScope.iconReady]
         * @param [iconNotReady] See [BuilderScope.iconNotReady]
         * @param [block] lambda for configuring optionals via [BuilderScope]
         * */
        public constructor(
            iconReady: Int,
            iconNotReady: Int,
            block: ThisBlock<BuilderScope>,
        ): this(
            b = BuilderScope.of(
                iconReady,
                iconNotReady,
            ).apply(block)
        )

        /**
         * See [BuilderScope.colorReady]
         * */
        @JvmField
        public val colorReady: Int? = _colorReady?.id

        /**
         * See [BuilderScope.displayName]
         * */
        @JvmField
        public val displayName: DisplayName = displayName

        /**
         * See [BuilderScope.iconReady]
         * */
        @JvmField
        public val iconReady: Int = _iconReady.id

        /**
         * See [BuilderScope.iconNotReady]
         * */
        @JvmField
        public val iconNotReady: Int = _iconNotReady.id

        /**
         * See [BuilderScope.iconData]
         * */
        @JvmField
        public val iconData: Int = _iconData.id

        /**
         * Configure a new [Config.BuilderScope] which inherits all settings
         * from this one.
         * */
        public fun newConfig(
            block: ThisBlock<BuilderScope>,
        ): Config = newConfig(null, block)

        /**
         * Configure a new [Config.BuilderScope] which inherits all settings
         * from this one.
         *
         * @param [iconReady] If non-null, that value will be set for
         *   [BuilderScope.iconReady] instead of the current [Config.iconReady].
         * */
        public fun newConfig(
            iconReady: Int?,
            block: ThisBlock<BuilderScope>,
        ): Config = newConfig(
            iconReady,
            null,
            block
        )

        /**
         * Configure a new [Config.BuilderScope] which inherits all options
         * from this one.
         *
         * @param [iconReady] If non-null, that value will be set for
         *   [BuilderScope.iconReady] instead of the current [Config.iconReady].
         * @param [iconNotReady] If non-null, that value will be set for
         *   [BuilderScope.iconNotReady] instead of the current [Config.iconNotReady].
         * */
        public fun newConfig(
            iconReady: Int?,
            iconNotReady: Int?,
            block: ThisBlock<BuilderScope>,
        ): Config = Config(
            b = BuilderScope.of(
                this,
                iconReady,
                iconNotReady,
            ).apply(block)
        )

        /**
         * Configure optionals for [Config].
         *
         * e.g.
         *
         *     Config(
         *         iconReady = R.drawable.my_icon_ready,
         *         iconNotReady = R.drawable.my_icon_not_ready,
         *     ) {
         *         colorReady = R.color.tor_purple
         *         iconData = R.drawable.my_icon_data
         *         enableActionRestart = true
         *         displayName = DisplayName.StringRes(R.string.tor_instance_1)
         *     }
         * */
        @KmpTorDsl
        public class BuilderScope private constructor(

            /**
             * The drawable resource id of the notification icon which will be
             * displayed when the instance of [TorRuntime] is in an operative
             * state (i.e. bootstrapped & network enabled).
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             * */
            @JvmField
            public val iconReady: Int,

            /**
             * The drawable resource id of the notification icon which will be
             * displayed when the instance of [TorRuntime] is in a non-operative
             * state (i.e. not bootstrapped or network disabled).
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             * */
            @JvmField
            public val iconNotReady: Int,
        ) {

            /**
             * The color resource id to apply to the notification when the instance
             * of [TorRuntime] is in an operative state (i.e. bootstrapped & network
             * enabled).
             *
             * Set to a value less than 1 to disable.
             *
             * **NOTE:** This setting will override whatever may be declared in
             * your application theme for [R.attr.kmp_tor_ui_color_ready].
             *
             * Default: `0` (disabled), unless this [BuilderScope] is a result of
             * [Config.newConfig], then whatever [Config.colorReady] is.
             *
             * @see [R.attr.kmp_tor_ui_color_ready]
             * */
            @JvmField
            public var colorReady: Int = 0

            /**
             * The drawable resource id of the notification icon which will be
             * displayed when the instance of [TorRuntime] is in an operative state
             * and receives data via [TorEvent.BW].
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             *
             * Default: Whatever [iconReady] is set to, unless this [BuilderScope] is a
             * result of [Config.newConfig], then whatever [Config.iconData] is.
             * */
            @JvmField
            public var iconData: Int = iconReady

            /**
             * If `true`, a notification action will be applied which allows users to
             * restart the [TorRuntime] instance via [Action.RestartDaemon] upon click.
             *
             * If `false`, the action will not be applied to the notification view.
             *
             * **NOTE:** This action is removed from the notification while on the
             * lock screen.
             *
             * Default: `false`, unless this [BuilderScope] is a result of [Config.newConfig],
             * then whatever [Config.enableActionRestart] is.
             * */
            @JvmField
            public var enableActionRestart: Boolean = false

            /**
             * If `true`, a notification action will be applied which allows users to
             * stop the [TorRuntime] instance via [Action.StopDaemon] upon click.
             *
             * If `false`, the action will not be applied to the notification view.
             *
             * **NOTE:** This action is removed from the notification while on the
             * lock screen.
             *
             * Default: `false`, unless this [BuilderScope] is a result of [Config.newConfig],
             * then whatever [Config.enableActionStop] is.
             * */
            @JvmField
            public var enableActionStop: Boolean = false

            /**
             * Declare an instance specific [DisplayName] to be shown while multiple
             * instances of [TorRuntime] are in operation. The "previous" and "next"
             * notification action buttons (only shown if more than 1 [TorRuntime]
             * is in operation) will cycle between instances, showing that instance's
             * current state. This should be different for each of your [TorRuntime]
             * instances so the user may differentiate between more easily.
             *
             * **NOTE:** If this [BuilderScope] is being configured via the
             * [Factory.BuilderScope.defaultConfig] DSL, then it will be set back to
             * [DisplayName.FID] when [Factory] is instantiated. This can only be
             * configured on a per-instance basis. Use [Config.newConfig] from
             * [Factory.defaultConfig] to modify for instance specific configs.
             *
             * e.g.
             *
             *     // Assuming multiple instances because you are overriding something
             *     // that is only used when multiple runtimes are operating.
             *     val environment = serviceConfig.newEnvironment(
             *         dirName = "torservice/1",
             *         instanceConfig = serviceConfig.factory.defaultConfig.newConfig {
             *             displayName = DisplayName.StringRes(R.string.tor_instance_1)
             *             // ...
             *         },
             *         loader = ResourceLoaderTorExec::getOrCreate,
             *         block = {
             *             // Use base directory for all resources for all Environment
             *             // instances (i.e. torservice)
             *             resourceDir = workDirectory.parentFile!!
             *
             *             // ...
             *         },
             *     )
             *
             * Default: [DisplayName.FID], unless this [BuilderScope] is a result of [Config.newConfig],
             * then whatever [Config.displayName] is.
             *
             * @see [DisplayName]
             * */
            @JvmField
            public var displayName: DisplayName = DisplayName.FID

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    iconReady: Int,
                    iconNotReady: Int,
                ): BuilderScope = BuilderScope(
                    iconReady,
                    iconNotReady,
                )

                @JvmSynthetic
                internal fun of(
                    other: Config,
                    iconReady: Int?,
                    iconNotReady: Int?,
                ): BuilderScope = BuilderScope(
                    iconReady ?: other.iconReady,
                    iconNotReady ?: other.iconNotReady
                ).apply {
                    colorReady = other.colorReady ?: 0
                    iconData = other.iconData
                    enableActionRestart = other.enableActionRestart
                    enableActionStop = other.enableActionStop
                    displayName = other.displayName
                }
            }
        }

        internal constructor(b: BuilderScope): this(
            // NOTE: If adding any fields, must also update
            // Builder.of(other, ...) & fields map.
            _colorReady = b.colorReady.takeIf { it > 0 }?.let { ColorRes(it) },
            _iconReady = DrawableRes(b.iconReady),
            _iconNotReady = DrawableRes(b.iconNotReady),
            _iconData = DrawableRes(b.iconData),
            enableActionRestart = b.enableActionRestart,
            enableActionStop = b.enableActionStop,
            displayName = b.displayName,
            INIT,
        )
    }

    /**
     * Factory class for [TorServiceConfig.Foreground.Companion.Builder]
     *
     * e.g.
     *
     *     val factory = KmpTorServiceUI.Factory(
     *         iconReady = R.drawable.my_icon_ready,
     *         iconNotReady = R.drawable.my_icon_not_ready,
     *         info = TorServiceUI.NotificationInfo(
     *             // ...
     *         ),
     *         block = {
     *             // configure...
     *
     *             defaultConfig {
     *                 // configure ...
     *             }
     *         },
     *     )
     *
     *     val serviceConfig = TorServiceConfig.Foreground.Builder(factory) {
     *         // configure...
     *     }
     *
     * @see [Factory.BuilderScope]
     * @see [TorServiceUI.NotificationInfo]
     * */
    public class Factory private constructor(
        b: BuilderScope,
        c: Config.BuilderScope,
        i: NotificationInfo,
    ): TorServiceUI.Factory<Config, KmpTorServiceUIInstanceState<Config>, KmpTorServiceUI>(
        defaultConfig = Config(c.apply { displayName = DisplayName.FID }),
        info = i,
    ) {

        /**
         * Create a new [Factory] with the default options.
         *
         * @param [iconReady] See [Config.BuilderScope.iconReady]
         * @param [iconNotReady] See [Config.BuilderScope.iconNotReady]
         * */
        public constructor(
            iconReady: Int,
            iconNotReady: Int,
            info: NotificationInfo,
        ): this(
            iconReady,
            iconNotReady,
            info,
            {},
        )

        /**
         * Create a new [Factory] and configure options via [block].
         *
         * @param [iconReady] See [Config.BuilderScope.iconReady]
         * @param [iconNotReady] See [Config.BuilderScope.iconNotReady]
         * @see [Factory.BuilderScope]
         * */
        public constructor(
            iconReady: Int,
            iconNotReady: Int,
            info: NotificationInfo,
            block: ThisBlock<BuilderScope>,
        ): this(
            Config.BuilderScope.of(
                iconReady,
                iconNotReady,
            ),
            info,
            block,
        )

        /**
         * See [BuilderScope.actionIntentPermissionSuffix]
         * */
        @JvmField
        public val actionIntentPermissionSuffix: String? = b.actionIntentPermissionSuffix

        /**
         * See [BuilderScope.contentIntentCode]
         * */
        @JvmField
        public val contentIntentCode: Int = b.contentIntentCode

        @KmpTorDsl
        public class BuilderScope private constructor(private val config: Config.BuilderScope) {

            /**
             * The suffix of a signature level permission to define in your manifest, which
             * will be used to further lock down the notification action [BroadcastReceiver].
             *
             * The permission string used will be the concatenation of your applicationId,
             * a dot character (i.e. `.`), and this suffix.
             *
             * e.g.
             *
             *     // defined suffix
             *     actionIntentPermissionSuffix = "NOTIFICATION_ACTION_KMP_TOR"
             *
             *     // AndroidManifest.xml (with `runtime-service-ui` provided description)
             *     <permission
             *         android:name="${applicationId}.NOTIFICATION_ACTION_KMP_TOR"
             *         android:description="@string/kmp_tor_ui_action_permission_description"
             *         android:protectionLevel="signature" />
             *
             *     <uses-permission android:name="${applicationId}.NOTIFICATION_ACTION_KMP_TOR" />
             *
             * All "non-system" [IntentFilter] (i.e. notification action [PendingIntent]) use
             * [SecureRandom] to generate their action string values. Sadly, there is no way
             * to define something like `exported="false"` when registering a [BroadcastReceiver]
             * at runtime until API 33 via the [Context.RECEIVER_NOT_EXPORTED] flag. This option
             * resolves that for all APIs by signature enforced permissions.
             *
             * **NOTE:** Suffix cannot be empty or contain any whitespace, and cannot contain
             * the application package name (it is just the suffix, as shown in the above example).
             *
             * Default: `null` (i.e. no permissions)
             * */
            @JvmField
            public var actionIntentPermissionSuffix: String? = null

            /**
             * Define a code to use when creating the [PendingIntent] which is to be set via
             * [Notification.Builder.setContentIntent].
             *
             * Default: `0`
             * */
            @JvmField
            public var contentIntentCode: Int = 0

            /**
             * Supply your own [PendingIntent] factory for [Notification.Builder.setContentIntent].
             *
             * If `null`, or the factory function returns `null`, then no [PendingIntent] will be
             * set for [Notification.Builder.setContentIntent].
             *
             * Default: An implementation which will wrap the [Intent] returned by
             * [PackageManager.getLaunchIntentForPackage] in a [PendingIntent].
             * */
            @JvmField
            public var contentIntent: ((code: Int, context: Context) -> PendingIntent?)? = STUB_PACKAGE_LAUNCHER

            /**
             * The drawable resource id for the notification action icon which allows users to
             * submit [TorCmd.Signal.NewNym] to the [TorRuntime] instance upon click.
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             *
             * Default: `null` (do not override default [R.drawable.ic_kmp_tor_ui_action_newnym])
             * */
            @JvmField
            public var iconActionNewNym: Int? = null

            /**
             * The drawable resource id for the notification action icon which allows users to
             * submit [Action.RestartDaemon] to the [TorRuntime] instance upon click.
             *
             * **NOTE:** This will not be utilized unless [Config.BuilderScope.enableActionRestart]
             * has been set to `true` for the currently displayed instance of [TorRuntime].
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             *
             * Default: `null` (do not override default [R.drawable.ic_kmp_tor_ui_action_restart])
             * */
            @JvmField
            public var iconActionRestart: Int? = null

            /**
             * The drawable resource id for the notification action icon which allows users to
             * submit [Action.StopDaemon] to the [TorRuntime] instance upon click.
             *
             * **NOTE:** This will not be utilized unless [Config.BuilderScope.enableActionStop]
             * has been set to `true` for the currently displayed instance of [TorRuntime].
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             *
             * Default: `null` (do not override default [R.drawable.ic_kmp_tor_ui_action_stop])
             * */
            @JvmField
            public var iconActionStop: Int? = null

            /**
             * The drawable resource id for the notification action icon which allows users to
             * cycle to the "previous" [TorRuntime] instance that is operating, upon click.
             *
             * **NOTE:** This will not be utilized unless multiple instances of [TorRuntime]
             * are operating within the service.
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             *
             * Default: `null` (do not override default [R.drawable.ic_kmp_tor_ui_action_previous])
             * */
            @JvmField
            public var iconActionPrevious: Int? = null

            /**
             * The drawable resource id for the notification action icon which allows users to
             * cycle to the "next" [TorRuntime] instance that is operating, upon click.
             *
             * **NOTE:** This will not be utilized unless multiple instances of [TorRuntime]
             * are operating within the service.
             *
             * **NOTE:** Icons should be `24dp` x `24dp` for best performance.
             *
             * Default: `null` (do not override default [R.drawable.ic_kmp_tor_ui_action_next])
             * */
            @JvmField
            public var iconActionNext: Int? = null

            /**
             * Configure [Config.BuilderScope] optionals for [Factory.defaultConfig].
             * */
            @KmpTorDsl
            public fun defaultConfig(
                block: ThisBlock<Config.BuilderScope>
            ): BuilderScope = apply { config.apply(block) }

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    config: Config.BuilderScope,
                ): BuilderScope = BuilderScope(config)
            }
        }

        private val actionIcons = UIAction.Icons.of(b)
        private val contentIntent: (code: Int, context: Context) -> PendingIntent? = when (val ci = b.contentIntent) {
            // Builder value was set to null to indicate no
            // content intent is desired. STUB actually produces
            // null, so reuse.
            null -> STUB_PACKAGE_LAUNCHER

            // Builder value was not modified, create the default.
            STUB_PACKAGE_LAUNCHER -> {
                create@ { code, context ->
                    val appContext = context.applicationContext

                    val launchIntent = appContext.packageManager
                        ?.getLaunchIntentForPackage(appContext.packageName)
                        ?: return@create null

                    PendingIntent.getActivity(appContext, code, launchIntent, P_INTENT_FLAGS)
                }
            }

            // Custom implementation
            else -> ci
        }

        private constructor(
            config: Config.BuilderScope,
            info: NotificationInfo,
            block: ThisBlock<BuilderScope>,
        ): this (
            BuilderScope.of(config).apply(block),
            config,
            info,
        )

        /**
         * See [TorServiceUI.Factory.validate]
         * */
        @Throws(IllegalStateException::class, Resources.NotFoundException::class)
        public override fun validate(context: Context) {
            try {
                val i = contentIntent(contentIntentCode, context)
                try {
                    i?.cancel()
                } catch (_: Throwable) {}
            } catch (t: Throwable) {
                throw IllegalStateException("contentIntent check failed", t)
            }

            actionIntentPermissionSuffix?.let { suffix ->
                val packageName = context.packageName
                check(suffix.isNotEmpty() && suffix.indexOfFirst { it.isWhitespace() } == -1) {
                    "actionIntentPermissionSuffix cannot be empty or contain whitespace"
                }
                check(!suffix.contains(packageName)) {
                    "actionIntentPermissionSuffix cannot contain the packageName[$packageName]"
                }

                val permission = "$packageName.$suffix"
                check(context.hasPermission(permission)) {
                    "actionIntentPermissionSuffix is declared, but permission is not granted for $permission"
                }
            }

            for (entry in UIAction.entries) {
                val res = actionIcons[entry]
                context.validateResource(
                    block = { retrieveDrawable(res) },
                    lazyText = { "Invalid iconAction$entry of $res" }
                )
            }
        }

        /**
         * See [TorServiceUI.Factory.validateConfig]
         * */
        @Throws(IllegalArgumentException::class, Resources.NotFoundException::class)
        public override fun validateConfig(context: Context, config: Config) {
            listOf(
                config._iconReady to "iconReady",
                config._iconNotReady to "iconNotReady",
                config._iconData to "iconData"
            ).forEach { (res, field) ->
                context.validateResource(
                    block = { retrieveDrawable(res) },
                    lazyText = { "Invalid $field of $res" }
                )
            }

            if (config.displayName is DisplayName.StringRes) {
                val text = context.validateResource(
                    block = { getString(config.displayName.id) },
                    lazyText = { "Invalid displayName of ${config.displayName}" },
                )

                // Parse returned text for required constraints
                DisplayName.Text.of(text)
            }

            config._colorReady?.let { res ->
                context.validateResource(
                    block = { retrieveColor(res) },
                    lazyText = { "Invalid colorReady of $res" }
                )
            }
        }

        @Throws(Resources.NotFoundException::class)
        private inline fun <T: Any?> Context.validateResource(
            block: Context.() -> T,
            lazyText: () -> String,
        ): T {
            try {
                return block(this)
            } catch (e: Exception) {
                val msg = lazyText()

                throw if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Resources.NotFoundException(msg, e)
                } else {
                    Resources.NotFoundException(msg).apply { addSuppressed(e) }
                }
            }
        }

        protected override fun createProtected(
            args: Args,
        ): KmpTorServiceUI = KmpTorServiceUI(
            actionIcons,
            actionIntentPermissionSuffix,
            contentIntentCode,
            contentIntent,
            args,
        )

        private companion object {

            /**
             * This is simply a stub for [BuilderScope] to indicate that the default
             * [PackageManager.getLaunchIntentForPackage] should be used, without
             * exposing the actual callback implementation via [BuilderScope].
             * */
            private val STUB_PACKAGE_LAUNCHER: (code: Int, context: Context) -> PendingIntent? = { _, _ -> null }
        }
    }

    private val appLabel = appContext.applicationInfo.loadLabel(appContext.packageManager)
    private val startTime = SystemClock.elapsedRealtime()

    private val keyguardHandler = KeyguardHandler()
    private val pendingIntents = PendingIntents(actionIntentPermissionSuffix, contentIntentCode, contentIntent)

    private val uiColor = UIColor.of(appContext)
    private val uiActionCache = UIAction.View.Cache.of(appContext, actionIcons) { action ->
        if (action != null) {
            pendingIntents[action]
        } else {
            appContext.noOpPendingIntent()
        }
    }

    private val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, channelId)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }.apply {
        // Sound and priority attributes set automatically
        // by TorServiceUI Notification.post

        setContentIntent(pendingIntents.contentIntent)
        setOngoing(true)
        setOnlyAlertOnce(true)
        setWhen(System.currentTimeMillis())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // API 17+
            // TODO: Issue #497
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

    private var current: CurrentUIState? = null

    protected override fun onRender(
        displayed: KmpTorServiceUIInstanceState<Config>,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ) {
        val state = displayed.state

        // TorEvent.BW is dispatched every 1 second (even if DisableNetwork is true).
        // This allows API 23- header layout to be a simple TextView, instead of
        // something like a Chronometer. Need to check for each onRender invocation
        // if the text changes in order to rebuild & post a new notification.
        val durationText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) "" else {
            val elapsed = (SystemClock.elapsedRealtime() - startTime)
                .toDuration(DurationUnit.MILLISECONDS)

            when {
                elapsed > DURATION_1_DAY -> "${elapsed.inWholeDays}d"
                elapsed > DURATION_1_HOUR -> "${elapsed.inWholeHours}h"
                else -> "${elapsed.inWholeMinutes}m"
            }
        }

        val pallet = uiColor[state.icon.colorize, displayed.instanceConfig._colorReady]

        current.let { current ->
            if (
                current == null
                || current.state != state
                || current.hasPrevious != hasPrevious
                || current.hasNext != hasNext
                || current.pallet != pallet
                || current.durationText != durationText
            ) {
                // There was a stateful change. Continue.
                this.current = CurrentUIState(
                    state,
                    hasPrevious,
                    hasNext,
                    pallet,
                    durationText,
                )
                return@let
            }

            // No stateful change. Ignore.
            return@onRender
        }

        val iconRes = when (state.icon) {
            IconState.Ready -> displayed.instanceConfig._iconReady
            IconState.NotReady -> displayed.instanceConfig._iconNotReady
            IconState.Data -> displayed.instanceConfig._iconData
        }

        builder.setSmallIcon(iconRes.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setColor(pallet.default.argb)
        }

        val content = RemoteViews(appContext.packageName, R.layout.kmp_tor_ui)

        val title = appContext.retrieveString(state.title)
        val text = appContext.retrieveString(state.text)
        val displayName = when (val n = displayed.instanceConfig.displayName) {
            is DisplayName.FID -> "[${state.fid}]"
            is DisplayName.StringRes -> appContext.getString(n.id)
            is DisplayName.Text -> n.text
        }

        content.applyHeader(pallet, iconRes, durationText)
        content.applyContent(pallet, state, title, text)
        val (showActions, expandedApi23) = content.applyActions(pallet, state, displayName, hasPrevious, hasNext, text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+
            val actionVisibility = if (showActions) View.VISIBLE else View.GONE
            content.setViewVisibility(R.id.kmp_tor_ui_container_actions, actionVisibility)

            builder.setCustomBigContentView(content)
            builder.build()
        } else {
            // API 23-
            @Suppress("DEPRECATION")
            builder.setContent(content)
            builder.build().apply {
                @Suppress("DEPRECATION")
                bigContentView = expandedApi23
            }
        }.post()
    }

    private fun RemoteViews.applyHeader(
        pallet: UIColor.Pallet,
        iconRes: DrawableRes,
        durationText: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return
        // API 23-

        val bitmap = iconRes.toBitmap(appContext, pallet.default, dpSize = 18)
        setImageViewBitmap(R.id.kmp_tor_ui_header_icon, bitmap)
        setTextViewText(R.id.kmp_tor_ui_header_app_name, appLabel)
        setTextViewText(R.id.kmp_tor_ui_header_duration, durationText)
    }

    private fun RemoteViews.applyContent(
        pallet: UIColor.Pallet,
        state: UIState,
        title: String,
        text: String,
    ) {
        setTextViewText(R.id.kmp_tor_ui_content_title_state, title)

        val (progressVisibility, progressParams) = when (state.progress) {
            is Progress.Determinant -> {
                View.VISIBLE to Triple(100, state.progress.value.toInt(), false)
            }
            is Progress.Indeterminate -> {
                View.VISIBLE to Triple(100, 0, true)
            }
            is Progress.None -> {
                View.INVISIBLE to null
            }
        }

        val (titleText, infoText) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && progressParams == null) {
            // API 24+
            "" to text
        } else {
            // API 23-
            text to ""
        }

        setTextViewText(R.id.kmp_tor_ui_content_title_text, titleText)
        setTextViewText(R.id.kmp_tor_ui_content_info_text, infoText)

        val progressId = R.id.kmp_tor_ui_content_info_progress
        setViewVisibility(progressId, progressVisibility)

        val (max, progress, indeterminate) = progressParams ?: return

        setProgressBar(progressId, max, progress, indeterminate)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+
            setColorStateList(progressId, "setProgressTintList", pallet.notNight, pallet.yesNight)
            setColorStateList(progressId, "setProgressBackgroundTintList", pallet.notNight, pallet.yesNight)
            setColorStateList(progressId, "setIndeterminateTintList", pallet.notNight, pallet.yesNight)
        }
    }

    private fun RemoteViews.applyActions(
        pallet: UIColor.Pallet,
        state: UIState,
        displayName: String,
        hasPrevious: Boolean,
        hasNext: Boolean,
        text: String,
    ): Pair<Boolean, RemoteViews?> {
        removeAllViews(R.id.kmp_tor_ui_actions_load_instance)
        removeAllViews(R.id.kmp_tor_ui_actions_load_selector)

        val showInstanceActions = state.actions.isNotEmpty()
        val showSelectorActions = hasPrevious || hasNext

        if (!showInstanceActions && !showSelectorActions) return false to null

        var expandedApi23: RemoteViews? = null

        val contentTarget = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+
            this
        } else {
            // API 23-
            @Suppress("DEPRECATION")
            val clone = clone()
            expandedApi23 = clone
            clone.setViewVisibility(R.id.kmp_tor_ui_container_actions, View.VISIBLE)

            if (state.progress is Progress.None) {
                clone.setTextViewText(R.id.kmp_tor_ui_content_title_text, "")
                clone.setTextViewText(R.id.kmp_tor_ui_content_info_text, text)
            }

            clone
        }

        contentTarget.applyInstanceActions(pallet, showInstanceActions, state)
        contentTarget.applySelectorActions(pallet, showSelectorActions, displayName, hasPrevious, hasNext)

        return true to expandedApi23
    }

    private fun RemoteViews.applyInstanceActions(
        pallet: UIColor.Pallet,
        showInstanceActions: Boolean,
        state: UIState,
    ) {
        val visibility = if (showInstanceActions) {
            if (pendingIntents.contentIntent != null) {
                setOnClickPendingIntent(R.id.kmp_tor_ui_actions_load_instance, appContext.noOpPendingIntent())
            }

            for (btnAction in state.actions) {

                val uiAction = when (btnAction) {
                    ButtonAction.NewIdentity -> UIAction.NewNym
                    ButtonAction.RestartTor -> UIAction.Restart
                    ButtonAction.StopTor -> UIAction.Stop
                }

                val view = uiActionCache.getOrCreate(uiAction, pallet, enabled = true)
                addView(R.id.kmp_tor_ui_actions_load_instance, view)
            }

            View.VISIBLE
        } else {
            View.GONE
        }

        setViewVisibility(R.id.kmp_tor_ui_actions_load_instance, visibility)
    }

    private fun RemoteViews.applySelectorActions(
        pallet: UIColor.Pallet,
        showSelectorActions: Boolean,
        displayName: String,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ) {
        val visibility = if (showSelectorActions) {
            if (pendingIntents.contentIntent != null) {
                setOnClickPendingIntent(R.id.kmp_tor_ui_actions_load_instance, appContext.noOpPendingIntent())
                setOnClickPendingIntent(R.id.kmp_tor_ui_actions_selector_text, appContext.noOpPendingIntent())
            }
            setTextViewText(R.id.kmp_tor_ui_actions_selector_text, displayName)

            listOf(
                UIAction.Previous to hasPrevious,
                UIAction.Next to hasNext,
            ).forEach { (uiAction, enabled) ->
                val view = uiActionCache.getOrCreate(uiAction, pallet, enabled = enabled)
                addView(R.id.kmp_tor_ui_actions_load_selector, view)
            }

            View.VISIBLE
        } else {
            View.GONE
        }

        setViewVisibility(R.id.kmp_tor_ui_container_actions_selector, visibility)
    }

    protected override fun onDestroy() {
        super.onDestroy()
        keyguardHandler.destroy()
        pendingIntents.destroy()
        uiActionCache.clearCache()
    }

    protected override fun createProtected(
        args: AbstractTorServiceUI.Args.Instance,
    ): KmpTorServiceUIInstanceState<Config> = KmpTorServiceUIInstanceState.of(
        args,
        isDeviceLocked = keyguardHandler::isDeviceLocked
    )

    private class CurrentUIState(
        val state: UIState,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
        val pallet: UIColor.Pallet,
        val durationText: String,
    )

    private inner class KeyguardHandler: Destroyable {

        private val manager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Volatile
        private var _isDeviceLocked: Boolean = manager.isKeyguardLocked

        public fun isDeviceLocked(): Boolean = _isDeviceLocked

        private val receiver = Receiver { intent ->
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    val old = _isDeviceLocked
                    val new = manager.isKeyguardLocked

                    if (old == new) return@Receiver

                    _isDeviceLocked = new
                    val instances = instanceStates
                    for (instance in instances) {
                        instance.onDeviceLockChange()
                    }
                    serviceChildScope.launch {
                        for (instance in instances) {
                            instance.debug { "DeviceIsLocked[$new]" }
                        }
                    }
                }
            }
        }.register(
            filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            permission = null,
            scheduler = null,
            exported = null,
        )

        public override fun destroy() { receiver?.dispose() }

        public override fun isDestroyed(): Boolean = receiver?.isDisposed ?: true
    }

    private inner class PendingIntents(
        actionIntentPermissionSuffix: String?,
        contentIntentCode: Int,
        contentIntent: (code: Int, context: Context) -> PendingIntent?,
    ): Destroyable {

        @JvmField
        public val contentIntent: PendingIntent? = contentIntent(contentIntentCode, appContext)
        public operator fun get(action: UIAction): PendingIntent = actionIntents[action.ordinal]

        @Volatile
        private var _isDestroyed: Boolean = false
        private val filter = BigInteger(130, SecureRandom()).toString(32)

        private val receiver = Receiver { intent ->
            if (intent == null) return@Receiver
            if (intent.action != filter) return@Receiver
            if (intent.`package` != appContext.packageName) return@Receiver

            val action = intent.getStringExtra(filter)?.let { extra ->
                try {
                    UIAction.valueOf(extra)
                } catch (_: IllegalArgumentException) {
                    null
                }
            } ?: return@Receiver

            when (action) {
                UIAction.NewNym -> {
                    displayed?.processorTorCmd()?.enqueue(
                        TorCmd.Signal.NewNym,
                        OnFailure.noOp(),
                        OnSuccess.noOp(),
                    )
                }
                UIAction.Restart -> {
                    if (keyguardHandler.isDeviceLocked()) return@Receiver

                    displayed?.processorAction()?.enqueue(
                        Action.RestartDaemon,
                        OnFailure.noOp(),
                        OnSuccess.noOp(),
                    )
                }
                UIAction.Stop -> {
                    if (keyguardHandler.isDeviceLocked()) return@Receiver

                    displayed?.processorAction()?.enqueue(
                        Action.StopDaemon,
                        OnFailure.noOp(),
                        OnSuccess.noOp(),
                    )
                }
                UIAction.Previous -> {
                    selectPrevious()
                }
                UIAction.Next -> {
                    selectNext()
                }
            }
        }.register(
            filter = IntentFilter(filter),
            permission = actionIntentPermissionSuffix?.let { suffix -> "${appContext.packageName}.$suffix" },
            scheduler = null,
            exported = false,
        )

        private val actionIntents = UIAction.entries.let { entries ->
            entries.mapTo(ArrayList(entries.size)) { it.toPendingIntent() }
        }.toImmutableList()

        public override fun destroy() {
            if (_isDestroyed) return
            _isDestroyed = true

            (actionIntents + contentIntent).forEach { intent ->
                try {
                    intent?.cancel()
                } catch (_: Throwable) {}
            }
            receiver?.dispose()
        }

        public override fun isDestroyed(): Boolean = _isDestroyed

        private fun UIAction.toPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
            appContext,
            ordinal + 42,
            Intent(filter).putExtra(filter, name).setPackage(appContext.packageName),
            P_INTENT_FLAGS,
        )
    }

    private companion object {
        private val DURATION_1_DAY = 1.days
        private val DURATION_1_HOUR = 1.hours

        private val P_INTENT_FLAGS by lazy {
            var f = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+
                f = f or PendingIntent.FLAG_IMMUTABLE
            }
            f
        }

        @Volatile
        private var _noOpPendingIntent: PendingIntent? = null

        private fun Context.noOpPendingIntent(): PendingIntent {
            return _noOpPendingIntent ?: synchronized(Companion) {
                _noOpPendingIntent ?: run {
                    val pIntent = PendingIntent.getBroadcast(
                        applicationContext,
                        0,
                        Intent("io.matthewnelson.kmp.tor.service.ui.NO_ACTION").setPackage(packageName),
                        P_INTENT_FLAGS,
                    )

                    try {
                        pIntent.cancel()
                    } catch (_: Throwable) {}

                    _noOpPendingIntent = pIntent
                    pIntent
                }
            }
        }
    }
}
