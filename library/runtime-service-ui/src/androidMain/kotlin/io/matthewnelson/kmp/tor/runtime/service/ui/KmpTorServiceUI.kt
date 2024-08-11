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
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.Action
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
     * TODO
     * */
    public class Config private constructor(
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
            "displayName" to displayName,
            "iconReady" to _iconReady,
            "iconNotReady" to _iconNotReady,
            "iconData" to _iconData,
        ),
        init,
    ) {

        @JvmField
        public val displayName: DisplayName = displayName
        @JvmField
        public val iconReady: Int = _iconReady.id
        @JvmField
        public val iconNotReady: Int = _iconNotReady.id
        @JvmField
        public val iconData: Int = _iconData.id

        /**
         * TODO
         * */
        public fun newConfig(
            block: ThisBlock<Builder>
        ): Config = newConfig(null, block)

        /**
         * TODO
         * */
        public fun newConfig(
            iconReady: Int?,
            block: ThisBlock<Builder>,
        ): Config = newConfig(
            iconReady,
            null,
            block
        )

        /**
         * TODO
         * */
        public fun newConfig(
            iconReady: Int?,
            iconNotReady: Int?,
            block: ThisBlock<Builder>,
        ): Config = Config(
            b = Builder.of(
                this,
                iconReady,
                iconNotReady,
            ).apply(block)
        )

        public constructor(
            iconReady: Int,
            iconNotReady: Int,
        ): this(
            iconReady,
            iconNotReady,
            {},
        )

        public constructor(
            iconReady: Int,
            iconNotReady: Int,
            block: ThisBlock<Builder>,
        ): this(
            b = Builder.of(
                iconReady,
                iconNotReady,
            ).apply(block)
        )

        internal constructor(b: Builder): this(
            _iconReady = DrawableRes(b.iconReady),
            _iconNotReady = DrawableRes(b.iconNotReady),
            _iconData = DrawableRes(b.iconData),
            enableActionRestart = b.enableActionRestart,
            enableActionStop = b.enableActionStop,
            displayName = b.displayName,
            INIT,
        )

        @KmpTorDsl
        public class Builder private constructor(
            @JvmField
            public val iconReady: Int,
            @JvmField
            public val iconNotReady: Int,
        ) {

            /**
             * TODO
             * */
            @JvmField
            public var iconData: Int = iconReady

            /**
             * TODO
             * */
            @JvmField
            public var enableActionRestart: Boolean = false

            /**
             * TODO
             * */
            @JvmField
            public var enableActionStop: Boolean = false

            /**
             * TODO
             * */
            @JvmField
            public var displayName: DisplayName = DisplayName.FID

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    iconReady: Int,
                    iconNotReady: Int,
                ): Builder = Builder(
                    iconReady,
                    iconNotReady,
                )

                @JvmSynthetic
                internal fun of(
                    other: Config,
                    iconReady: Int?,
                    iconNotReady: Int?,
                ): Builder = Builder(
                    iconReady ?: other.iconReady,
                    iconNotReady ?: other.iconNotReady
                ).apply {
                    iconData = other.iconData
                    enableActionRestart = other.enableActionRestart
                    enableActionStop = other.enableActionStop
                    displayName = other.displayName
                }
            }
        }
    }

    /**
     * Factory class for [TorServiceConfig.Foreground.Companion.Builder]
     *
     * e.g.
     *
     *     val factory = KmpTorServiceUI.Factory(
     *         iconReady = R.drawable.my_icon_a,
     *         iconNotReady = R.drawable.my_icon_b,
     *         info = TorServiceUI.NotificationInfo.of(
     *             // ...
     *         ),
     *         block = {
     *             // configure...
     *
     *             defaultConfig {
     *                 // configure ...
     *             }
     *         }
     *     )
     *
     *     val config = TorServiceConfig.Foreground.Builder(factory) {
     *         // configure...
     *     }
     * */
    public class Factory private constructor(
        b: Builder,
        c: Config.Builder,
    ): TorServiceUI.Factory<Config, KmpTorServiceUIInstanceState<Config>, KmpTorServiceUI>(
        defaultConfig = Config(c.apply { displayName = DisplayName.FID }),
        info = b.info,
    ) {

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

        @JvmField
        public val actionIntentPermissionSuffix: String? = b.actionIntentPermissionSuffix
        @JvmField
        public val contentIntentCode: Int = b.contentIntentCode

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

        public constructor(
            iconReady: Int,
            iconNotReady: Int,
            info: NotificationInfo,
            block: ThisBlock<Builder>,
        ): this(
            Config.Builder.of(
                iconReady,
                iconNotReady,
            ),
            info,
            block,
        )

        private constructor(
            config: Config.Builder,
            info: NotificationInfo,
            block: ThisBlock<Builder>,
        ): this (
            b = Builder.of(
                info,
                config,
            ).apply(block),
            c = config,
        )

        @KmpTorDsl
        public class Builder private constructor(
            @JvmField
            public val info: NotificationInfo,
            private val config: Config.Builder,
        ) {

            /**
             * TODO: Add string resource for description and label that
             *  consumers can easily point to for the permission declaration.
             * */
            @JvmField
            public var actionIntentPermissionSuffix: String? = null

            /**
             * TODO
             * */
            @JvmField
            public var contentIntentCode: Int = 0

            /**
             * TODO
             * */
            @JvmField
            public var contentIntent: ((code: Int, context: Context) -> PendingIntent?)? = STUB_PACKAGE_LAUNCHER

            /**
             * TODO
             * */
            @JvmField
            public var iconActionNewNym: Int? = null

            /**
             * TODO
             * */
            @JvmField
            public var iconActionRestart: Int? = null

            /**
             * TODO
             * */
            @JvmField
            public var iconActionStop: Int? = null

            /**
             * TODO
             * */
            @JvmField
            public var iconActionPrevious: Int? = null

            /**
             * TODO
             * */
            @JvmField
            public var iconActionNext: Int? = null

            /**
             * TODO
             * */
            @KmpTorDsl
            public fun defaultConfig(
                block: ThisBlock<Config.Builder>
            ): Builder = apply { config.apply(block) }

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    info: NotificationInfo,
                    config: Config.Builder,
                ): Builder = Builder(info, config)
            }
        }

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

                val permission = packageName + suffix
                check(context.hasPermission(permission)) {
                    "actionIntentPermissionSuffix is declared, but permission is not granted for $permission"
                }
            }

            UIAction.entries.forEach { entry ->
                val res = actionIcons[entry]
                context.validateResource(
                    block = { retrieveDrawable(res) },
                    lazyText = { "Invalid iconAction$entry of $res" }
                )
            }
        }

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
             * This is simply a stub for [Builder] to indicate that the default
             * [PackageManager.getLaunchIntentForPackage] should be used, without
             * exposing the actual callback implementation via [Builder].
             * */
            private val STUB_PACKAGE_LAUNCHER: (code: Int, context: Context) -> PendingIntent? = { _, _ -> null }
        }
    }

    private val appLabel = appContext.applicationInfo.loadLabel(appContext.packageManager)
    private val startTime = SystemClock.elapsedRealtime()
    private val uiColor = UIColor.of(appContext)

    private val keyguardHandler = KeyguardHandler()
    private val pendingIntents = PendingIntents(actionIntentPermissionSuffix, contentIntentCode, contentIntent)
    private val actionCache = UIAction.View.Cache.of(appContext, actionIcons) { action ->
        if (action != null) {
            pendingIntents[action]
        } else {
            appContext.noOpPendingIntent()
        }
    }

    private val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, channelID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }.apply {
        setContentIntent(pendingIntents.contentIntent)
        // TODO: Issue #485
        @Suppress("DEPRECATION")
        setPriority(Notification.PRIORITY_DEFAULT)
        setOngoing(true)
        setOnlyAlertOnce(true)
        @Suppress("DEPRECATION")
        setSound(null)
        setWhen(System.currentTimeMillis())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // API 17+
            setShowWhen(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            // API 20+
            // TODO: Issue #485
            //  Allow ability to configure, but use TorService
            //  as default for backward compatibility
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

    private var current: Triple<UIState, Boolean, Boolean>? = null

    protected override fun onUpdate(
        displayed: KmpTorServiceUIInstanceState<Config>,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ) {
        val state = displayed.state

        Triple(state, hasPrevious, hasNext).let { new ->
            if (new == current) return
            current = new
        }

        val pallet = uiColor[state.color]
        val iconRes = when (state.icon) {
            IconState.NetworkEnabled -> displayed.instanceConfig._iconReady
            IconState.NetworkDisabled -> displayed.instanceConfig._iconNotReady
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

        content.applyHeader(pallet, iconRes)
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

    // TODO: Duration on API 23- needs to handled for header
    //  which may also affect current depending on the
    //  implementation. Issue #490.
    private fun RemoteViews.applyHeader(
        pallet: UIColor.Pallet,
        iconRes: DrawableRes,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return
        // API 23-

        val bitmap = iconRes.toIconBitmap(appContext, pallet.default, dpSize = 18)
        setImageViewBitmap(R.id.kmp_tor_ui_header_icon, bitmap)
        setTextViewText(R.id.kmp_tor_ui_header_app_name, appLabel)

        val elapsed = (SystemClock.elapsedRealtime() - startTime)
            .toDuration(DurationUnit.MILLISECONDS)

        val durationText = when {
            elapsed > DURATION_1_DAY -> "${elapsed.inWholeDays}d"
            elapsed > DURATION_1_HOUR -> "${elapsed.inWholeHours}h"
            else -> "${elapsed.inWholeMinutes}m"
        }

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
                View.GONE to null
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

            state.actions.forEach { btnAction ->

                val uiAction = when (btnAction) {
                    ButtonAction.NewIdentity -> UIAction.NewNym
                    ButtonAction.RestartTor -> UIAction.Restart
                    ButtonAction.StopTor -> UIAction.Stop
                }

                val view = actionCache.getOrCreate(uiAction, pallet, enabled = true)
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
                val view = actionCache.getOrCreate(uiAction, pallet, enabled = enabled)
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
        actionCache.clearCache()
    }

    protected override fun createProtected(
        args: AbstractTorServiceUI.Args.Instance,
    ): KmpTorServiceUIInstanceState<Config> = KmpTorServiceUIInstanceState.of(
        args,
        isDeviceLocked = keyguardHandler::isDeviceLocked
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
                    if (old != new) {
                        _isDeviceLocked = new
                        val instances = instanceStates
                        instances.forEach { instance ->
                            instance.onDeviceLockChange()
                        }
                        serviceChildScope.launch {
                            instances.forEach { instance ->
                                instance.debug { "DeviceIsLocked[$new]" }
                            }
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
                    previous()
                }
                UIAction.Next -> {
                    next()
                }
            }
        }.register(
            filter = IntentFilter(filter),
            permission = actionIntentPermissionSuffix?.let { suffix -> appContext.packageName + suffix },
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
