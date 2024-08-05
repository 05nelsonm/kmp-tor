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
@file:Suppress("PropertyName")

package io.matthewnelson.kmp.tor.runtime.service.ui

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private val actionIcons: NotificationAction.Icons,
    actionIntentPermissionSuffix: String?,
    contentIntentCode: Int,
    contentIntent: (code: Int, context: Context) -> PendingIntent?,
    args: Args
): TorServiceUI<
    KmpTorServiceUI.Config,
    KmpTorServiceUIInstanceState<KmpTorServiceUI.Config>
>(args) {

    /**
     * TODO
     * */
    public class Config private constructor(
        internal val _iconNetworkEnabled: DrawableRes,
        internal val _iconNetworkDisabled: DrawableRes,
        internal val _iconDataXfer: DrawableRes,
        enableActionRestart: Boolean,
        enableActionStop: Boolean,
        init: Any
    ): AbstractKmpTorServiceUIConfig(
        enableActionRestart,
        enableActionStop,
        fields = mapOf(
            "iconNetworkEnabled" to _iconNetworkEnabled,
            "iconNetworkDisabled" to _iconNetworkDisabled,
            "iconDataXfer" to _iconDataXfer,
        ),
        init,
    ) {

        @JvmField
        public val iconNetworkEnabled: Int = _iconNetworkEnabled.id
        @JvmField
        public val iconNetworkDisabled: Int = _iconNetworkDisabled.id
        @JvmField
        public val iconDataXfer: Int = _iconDataXfer.id

        public constructor(
            iconNetworkEnabled: Int,
            iconNetworkDisabled: Int,
        ): this(
            iconNetworkEnabled,
            iconNetworkDisabled,
            {},
        )

        public constructor(
            iconNetworkEnabled: Int,
            iconNetworkDisabled: Int,
            block: ThisBlock<Builder>,
        ): this(
            b = Builder.of(
                iconNetworkEnabled,
                iconNetworkDisabled,
            ).apply(block)
        )

        private constructor(b: Builder): this(
            _iconNetworkEnabled = DrawableRes(b.iconNetworkEnabled),
            _iconNetworkDisabled = DrawableRes(b.iconNetworkDisabled),
            _iconDataXfer = DrawableRes(b.iconDataXfer),
            enableActionRestart = b.enableActionRestart,
            enableActionStop = b.enableActionStop,
            INIT,
        )

        @KmpTorDsl
        public class Builder private constructor(
            @JvmField
            public val iconNetworkEnabled: Int,
            @JvmField
            public val iconNetworkDisabled: Int,
        ) {

            /**
             * TODO
             * */
            @JvmField
            public var iconDataXfer: Int = iconNetworkEnabled

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

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    iconNetworkEnabled: Int,
                    iconNetworkDisabled: Int,
                ): Builder = Builder(
                    iconNetworkEnabled,
                    iconNetworkDisabled,
                )
            }
        }
    }

    /**
     * Factory class for [TorServiceConfig.Foreground.Companion.Builder]
     *
     * e.g.
     *
     *     val factory = KmpTorServiceUI.Factory(
     *         defaultConfig = KmpTorServiceUI.Config(
     *             // ...
     *         ),
     *         info = TorServiceUI.NotificationInfo.of(
     *             // ...
     *         ),
     *         block = {
     *             // configure...
     *         }
     *     )
     *
     *     val config = TorServiceConfig.Foreground.Builder(factory) {
     *         // configure...
     *     }
     * */
    public class Factory private constructor(
        b: Builder,
    ): TorServiceUI.Factory<Config, KmpTorServiceUIInstanceState<Config>, KmpTorServiceUI>(
        b.defaultConfig,
        b.info,
    ) {

        @JvmField
        public val actionIntentPermissionSuffix: String? = b.actionIntentPermissionSuffix
        @JvmField
        public val contentIntentCode: Int = b.contentIntentCode
        @JvmField
        public val contentIntent: (code: Int, context: Context) -> PendingIntent? = b.contentIntent

        private val actionIcons = NotificationAction.Icons(b)

        @JvmField
        public val iconActionNewNym: Int = actionIcons[NotificationAction.NewNym].id
        @JvmField
        public val iconActionRestart: Int = actionIcons[NotificationAction.Restart].id
        @JvmField
        public val iconActionStop: Int = actionIcons[NotificationAction.Stop].id
        @JvmField
        public val iconActionPrevious: Int = actionIcons[NotificationAction.Previous].id
        @JvmField
        public val iconActionNext: Int = actionIcons[NotificationAction.Next].id

        public constructor(
            defaultConfig: Config,
            info: NotificationInfo,
        ): this(defaultConfig, info, {})

        public constructor(
            defaultConfig: Config,
            info: NotificationInfo,
            block: ThisBlock<Builder>,
        ): this(
            b = Builder.of(
                defaultConfig,
                info,
            ).apply(block)
        )

        @KmpTorDsl
        public class Builder private constructor(
            @JvmField
            public val defaultConfig: Config,
            @JvmField
            public val info: NotificationInfo,
        ) {

            /**
             * TODO
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
            public var contentIntent: (code: Int, context: Context) -> PendingIntent? = create@ { code, context ->
                val launchIntent = context.packageManager
                    ?.getLaunchIntentForPackage(context.packageName)
                    ?: return@create null

                PendingIntent.getActivity(context, code, launchIntent, P_INTENT_FLAGS)
            }

            /**
             * TODO
             * */
            @JvmField
            public var iconActionNewNym: Int = android.R.drawable.ic_menu_add

            /**
             * TODO
             * */
            @JvmField
            public var iconActionRestart: Int = android.R.drawable.ic_media_play

            /**
             * TODO
             * */
            @JvmField
            public var iconActionStop: Int = android.R.drawable.ic_menu_delete

            /**
             * TODO
             * */
            @JvmField
            public var iconActionPrevious: Int = android.R.drawable.ic_media_previous

            /**
             * TODO
             * */
            @JvmField
            public var iconActionNext: Int = android.R.drawable.ic_media_next

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    defaultConfig: Config,
                    info: NotificationInfo,
                ): Builder = Builder(defaultConfig, info)
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
                if (t is IllegalStateException) throw t
                throw IllegalStateException("contentIntent check failed")
            }

            actionIntentPermissionSuffix?.let { suffix ->
                val name = context.packageName
                check(suffix.isNotEmpty() && suffix.indexOfFirst { it.isWhitespace() } == -1) {
                    "actionIntentPermissionSuffix cannot be empty or contain any whitespace"
                }
                check(!suffix.contains(name)) {
                    "actionIntentPermissionSuffix cannot contain the packageName[$name]"
                }

                val permission = name + suffix
                check(context.hasPermission(permission)) {
                    "actionIntentPermissionSuffix is declared, but permission is not granted for $permission"
                }
            }

            NotificationAction.entries.forEach { entry ->
                val res = actionIcons[entry]

                try {
                    context.retrieveDrawable(res)
                } catch (e: Resources.NotFoundException) {
                    val msg = "Invalid iconAction$entry of $res"

                    throw if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Resources.NotFoundException(msg, e)
                    } else {
                        Resources.NotFoundException(msg).apply { addSuppressed(e) }
                    }
                }
            }
        }

        @Throws(Resources.NotFoundException::class)
        public override fun validateConfig(context: Context, config: Config) {
            context.retrieveDrawable(config._iconNetworkEnabled)
            context.retrieveDrawable(config._iconNetworkDisabled)
            context.retrieveDrawable(config._iconDataXfer)
        }

        protected override fun newInstanceUIProtected(
            args: Args,
        ): KmpTorServiceUI = KmpTorServiceUI(
            actionIcons,
            actionIntentPermissionSuffix,
            contentIntentCode,
            contentIntent,
            args,
        )
    }

    private val appLabel = appContext.applicationInfo.loadLabel(appContext.packageManager)
    private val startTime = SystemClock.elapsedRealtime()

    private val keyguardHandler = KeyguardHandler()
    private val pendingIntents = PendingIntents(actionIntentPermissionSuffix, contentIntentCode, contentIntent)

    private val builder = NotificationBuilder.of(appContext, channelID, pendingIntents.contentIntent)

    @Volatile
    private var _target: FileIDKey? = null
    private val lockTarget = Lock()

    protected override fun onUpdate(target: FileIDKey, type: UpdateType) {
        val selected: FileIDKey = _target ?: lockTarget.withLock {
            _target ?: run {
                // UpdateType.Added for the first time
                _target = target
                target
            }
        }

        // Update to a target's state that is not currently
        // being displayed in the Notification area. Ignore.
        if (type == UpdateType.Changed && selected != _target) return

        val (instance, enableCyclePrevious, enableCycleNext) = instanceStates.let { instanceStates ->
            val s = if (type == UpdateType.Removed && selected == target) {
                // Currently selected instance was removed. Cycle
                // to first available (if available).
                lockTarget.withLock {
                    // Check if still selected
                    if (selected != _target) return@withLock null

                    val next = instanceStates.keys.firstOrNull()
                    _target = next
                    next
                }
            } else {
                // Another instance was added or removed, or the currently
                // selected one was just added or had a change.
                selected
            }

            if (s == null) return

            val instance = instanceStates[s] ?: return
            val index = instanceStates.keys.indexOf(s).takeIf { it != -1 } ?: return

            Triple(instance, index > 0, index < (instanceStates.size - 1))
        }

        val state = instance.state

        val content = RemoteViews(appContext.packageName, R.layout.kmp_tor_ui_notification)

        val iconRes = when (state.icon) {
            IconState.NetworkEnabled -> instance.instanceConfig._iconNetworkEnabled
            IconState.NetworkDisabled -> instance.instanceConfig._iconNetworkDisabled
            IconState.DataXfer -> instance.instanceConfig._iconDataXfer
        }

        val title = appContext.retrieveString(state.title)
        val text = appContext.retrieveString(state.text)

        // Headers
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // API 23-
            content.setImageViewResource(R.id.kmp_tor_ui_header_icon, iconRes.id)
            content.setTextViewText(R.id.kmp_tor_ui_header_app_name, appLabel)

            (SystemClock.elapsedRealtime() - startTime).toDuration(DurationUnit.MILLISECONDS).let { elapsed ->
                when {
                    elapsed > DURATION_1_DAY -> "${elapsed.inWholeDays}d"
                    elapsed > DURATION_1_HOUR -> "${elapsed.inWholeHours}h"
                    else -> "${elapsed.inWholeMinutes}m"
                }
            }.let { content.setTextViewText(R.id.kmp_tor_ui_header_duration, it) }
        }

        // Content
        content.setTextViewText(R.id.kmp_tor_ui_content_title_state, title)

        when (state.progress) {
            is Progress.Determinant -> {
                View.VISIBLE to Triple(100, state.progress.value.toInt(), false)
            }
            is Progress.Indeterminate -> {
                View.VISIBLE to Triple(100, 0, true)
            }
            is Progress.None -> {
                View.GONE to null
            }
        }.let { (progressVisibility, progressParams) ->

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && progressParams == null) {
                // API 24+
                "" to text
            } else {
                // API 23-
                text to ""
            }.let { (titleText, infoText) ->
                content.setTextViewText(R.id.kmp_tor_ui_content_title_text, titleText)
                content.setTextViewText(R.id.kmp_tor_ui_content_info_text, infoText)
            }

            content.setViewVisibility(R.id.kmp_tor_ui_content_info_progress, progressVisibility)
            val (max, progress, indeterminate) = progressParams ?: return@let
            content.setProgressBar(R.id.kmp_tor_ui_content_info_progress, max, progress, indeterminate)
        }

        // Actions
        content.removeAllViews(R.id.kmp_tor_ui_actions_load_instance)
        content.removeAllViews(R.id.kmp_tor_ui_actions_load_cycle)

        var expandedApi23: RemoteViews? = null
        val showCycleActions = enableCyclePrevious || enableCycleNext
        val showInstanceActions = state.actions.isNotEmpty()
        val showActions = showInstanceActions || showCycleActions

        if (showActions) {
            val contentTarget = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // API 24+
                content
            } else {
                // API 23-
                @Suppress("DEPRECATION")
                val clone = content.clone()
                expandedApi23 = clone
                clone.setViewVisibility(R.id.kmp_tor_ui_container_actions, View.VISIBLE)

                if (state.progress is Progress.None) {
                    clone.setTextViewText(R.id.kmp_tor_ui_content_title_text, "")
                    clone.setTextViewText(R.id.kmp_tor_ui_content_info_text, text)
                }

                clone
            }

            if (showInstanceActions) {
                state.actions.forEach { action ->
                    val view = RemoteViews(appContext.packageName, R.layout.kmp_tor_ui_action_enabled)
                    contentTarget.addView(R.id.kmp_tor_ui_actions_load_instance, view)

                    when (action) {
                        ButtonAction.NewIdentity -> NotificationAction.NewNym
                        ButtonAction.RestartTor -> NotificationAction.Restart
                        ButtonAction.StopTor -> NotificationAction.Stop
                    }.let { intent ->
                        view.setOnClickPendingIntent(R.id.kmp_tor_ui_action, pendingIntents[intent])
                        view.setImageViewResource(R.id.kmp_tor_ui_action_image, actionIcons[intent].id)
                    }
                }
                View.VISIBLE
            } else {
                View.GONE
            }.let { contentTarget.setViewVisibility(R.id.kmp_tor_ui_actions_load_instance, it) }

            if (showCycleActions) {
                // TODO: instance.displayName()
                contentTarget.setTextViewText(R.id.kmp_tor_ui_actions_cycle_text, "[${state.fid}]")
                contentTarget.setOnClickPendingIntent(R.id.kmp_tor_ui_actions_cycle_text, appContext.noOpPendingIntent())

                listOf(
                    NotificationAction.Previous to enableCyclePrevious,
                    NotificationAction.Next to enableCycleNext,
                ).forEach { (intent, enable) ->
                    val layoutId = if (enable) {
                        R.layout.kmp_tor_ui_action_enabled
                    } else {
                        R.layout.kmp_tor_ui_action_disabled
                    }

                    val view = RemoteViews(appContext.packageName, layoutId)
                    contentTarget.addView(R.id.kmp_tor_ui_actions_load_cycle, view)

                    if (enable) {
                        pendingIntents[intent]
                    } else {
                        appContext.noOpPendingIntent()
                    }.let { view.setOnClickPendingIntent(R.id.kmp_tor_ui_action, it) }

                    view.setImageViewResource(R.id.kmp_tor_ui_action_image, actionIcons[intent].id)
                }

                View.VISIBLE
            } else {
                View.GONE
            }.let { contentTarget.setViewVisibility(R.id.kmp_tor_ui_container_actions_cycle, it) }
        }

        builder.withLock {
            setSmallIcon(iconRes.id)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // API 24+
                val actionVisibility = if (showActions) View.VISIBLE else View.GONE
                content.setViewVisibility(R.id.kmp_tor_ui_container_actions, actionVisibility)

                setCustomBigContentView(content)
                build()
            } else {
                // API 23-
                @Suppress("DEPRECATION")
                setContent(content)
                build().apply {
                    @Suppress("DEPRECATION")
                    bigContentView = expandedApi23
                }
            }.post()
        }
    }

    protected override fun onDestroy() {
        super.onDestroy()
        keyguardHandler.destroy()
        pendingIntents.destroy()
    }

    protected override fun newInstanceStateProtected(
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
                        instanceStates.values.forEach { instance ->
                            instance.debug { "DeviceIsLocked[$new]" }
                            instance.onDeviceLockChange()
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
        public operator fun get(action: NotificationAction): PendingIntent = actionIntents[action.ordinal]

        @Volatile
        private var _isDestroyed: Boolean = false
        private val filter = BigInteger(130, SecureRandom()).toString(32)

        private val receiver = Receiver { intent ->
            if (intent == null) return@Receiver
            if (intent.action != filter) return@Receiver
            if (intent.`package` != appContext.packageName) return@Receiver

            val action = intent.getStringExtra(filter)?.let { extra ->
                try {
                    NotificationAction.valueOf(extra)
                } catch (_: IllegalArgumentException) {
                    null
                }
            } ?: return@Receiver

            serviceChildScope.launch {
                val selected = _target ?: return@launch
                val instanceStates = instanceStates

                when (action) {
                    NotificationAction.NewNym -> {
                        instanceStates[selected]?.processorTorCmd()?.enqueue(
                            TorCmd.Signal.NewNym,
                            OnFailure.noOp(),
                            OnSuccess.noOp(),
                        )
                    }
                    NotificationAction.Restart -> {
                        instanceStates[selected]?.processorAction()?.enqueue(
                            Action.RestartDaemon,
                            OnFailure.noOp(),
                            OnSuccess.noOp(),
                        )
                    }
                    NotificationAction.Stop -> {
                        instanceStates[selected]?.processorAction()?.enqueue(
                            Action.StopDaemon,
                            OnFailure.noOp(),
                            OnSuccess.noOp(),
                        )
                    }
                    NotificationAction.Previous -> {
                        val keys = instanceStates.keys
                        val previous: FileIDKey = keys.elementAtOrNull(keys.indexOf(selected) - 1) ?: return@launch
                        _target = previous
                        onUpdate(previous, UpdateType.Changed)
                    }
                    NotificationAction.Next -> {
                        val keys = instanceStates.keys
                        val next: FileIDKey = keys.elementAtOrNull(keys.indexOf(selected) + 1) ?: return@launch
                        _target = next
                        onUpdate(next, UpdateType.Changed)
                    }
                }
            }
        }.register(
            filter = IntentFilter(filter),
            permission = actionIntentPermissionSuffix?.let { suffix -> appContext.packageName + suffix },
            scheduler = null,
            exported = false,
        )

        private val actionIntents = NotificationAction.entries.let { entries ->
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

        private fun NotificationAction.toPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
            appContext,
            ordinal,
            Intent(filter).putExtra(filter, name).setPackage(appContext.packageName),
            P_INTENT_FLAGS,
        )
    }

    private enum class NotificationAction {
        NewNym,
        Restart,
        Stop,
        Previous,
        Next;

        public class Icons(b: Factory.Builder) {

            private val icons = entries.mapTo(ArrayList(entries.size)) { entry ->
                when (entry) {
                    NewNym -> b.iconActionNewNym
                    Restart -> b.iconActionRestart
                    Stop -> b.iconActionStop
                    Previous -> b.iconActionPrevious
                    Next -> b.iconActionNext
                }.let { id -> DrawableRes(id) }
            }.toImmutableList()

            public operator fun get(action: NotificationAction): DrawableRes = icons[action.ordinal]
        }
    }

    private companion object {
        private val DURATION_1_DAY = 1.days
        private val DURATION_1_HOUR = 1.hours

        private val P_INTENT_FLAGS by lazy {
            var f = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
                    } catch (_: Throwable) {
                    }

                    _noOpPendingIntent = pIntent
                    pIntent
                }
            }
        }
    }
}
