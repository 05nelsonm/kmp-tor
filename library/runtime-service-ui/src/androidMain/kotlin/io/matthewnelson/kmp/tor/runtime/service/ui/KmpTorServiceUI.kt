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
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.Destroyable
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
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

            @JvmField
            public var iconDataXfer: Int = iconNetworkEnabled

            @JvmField
            public var enableActionRestart: Boolean = false

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
        public val contentIntentCode: Int = b.contentIntentCode
        @JvmField
        public val contentIntent: (code: Int, context: Context) -> PendingIntent? = b.contentIntent

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

            @JvmField
            public var contentIntentCode: Int = 0

            @JvmField
            public var contentIntent: (code: Int, context: Context) -> PendingIntent? = create@ { code, context ->
                val launchIntent = context.packageManager
                    ?.getLaunchIntentForPackage(context.packageName)
                    ?: return@create null

                PendingIntent.getActivity(context, code, launchIntent, P_INTENT_FLAGS)
            }

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    defaultConfig: Config,
                    info: NotificationInfo,
                ): Builder = Builder(defaultConfig, info)
            }
        }

        @Throws(Resources.NotFoundException::class)
        public override fun validate(context: Context) {
            try {
                val i = contentIntent(contentIntentCode, context)
                try {
                    i?.cancel()
                } catch (_: Throwable) {}
            } catch (e: Exception) {
                if (e is Resources.NotFoundException) throw e

                val msg = "contentIntent check failed"
                throw if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Resources.NotFoundException(msg, e)
                } else {
                    Resources.NotFoundException(msg).also {
                        it.addSuppressed(e)
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
            contentIntentCode,
            contentIntent,
            args,
        )
    }

    private val startTime = SystemClock.elapsedRealtime()
    private val appLabel = appContext.applicationInfo.loadLabel(appContext.packageManager)
    private val keyguardHandler = KeyguardHandler()
    private val pendingIntents = PendingIntents(contentIntentCode, contentIntent)

    private val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, channelID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }.apply {
        setOngoing(true)
        setOnlyAlertOnce(true)
        setWhen(System.currentTimeMillis())
        setContentIntent(pendingIntents.contentIntent)

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

    protected override fun newInstanceStateProtected(
        args: AbstractTorServiceUI.Args.Instance,
    ): KmpTorServiceUIInstanceState<Config> = KmpTorServiceUIInstanceState.of(
        args,
        isDeviceLocked = keyguardHandler::isDeviceLocked
    )

    protected override fun onUpdate(target: FileIDKey, type: UpdateType) {
        val instanceStates = instanceStates
        val instance = instanceStates[target] ?: return
        val state = instance.state

        val content = RemoteViews(appContext.packageName, R.layout.kmp_tor_ui_notification)

        val iconRes = when (state.icon) {
            IconState.NetworkEnabled -> instance.instanceConfig._iconNetworkEnabled
            IconState.NetworkDisabled -> instance.instanceConfig._iconNetworkDisabled
            IconState.DataXfer -> instance.instanceConfig._iconDataXfer
        }
        builder.setSmallIcon(iconRes.id)

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
                Triple("", View.VISIBLE, text)
            } else {
                // API 23-
                Triple(text, View.GONE, "")
            }.let { (titleText, infoVisibility, infoText) ->
                content.setTextViewText(R.id.kmp_tor_ui_content_title_text, titleText)
                content.setViewVisibility(R.id.kmp_tor_ui_content_info_text, infoVisibility)
                content.setTextViewText(R.id.kmp_tor_ui_content_info_text, infoText)
            }

            content.setViewVisibility(R.id.kmp_tor_ui_content_info_progress, progressVisibility)
            val (max, progress, indeterminate) = progressParams ?: return@let
            content.setProgressBar(R.id.kmp_tor_ui_content_info_progress, max, progress, indeterminate)
        }

        // TODO: Actions

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // API 24+
            builder.setCustomBigContentView(content)
            builder.build()
        } else {
            // API 23-
            @Suppress("DEPRECATION")
            builder.setContent(content)
            builder.build()
        }.post()
    }

    protected override fun onDestroy() {
        super.onDestroy()
        keyguardHandler.destroy()
        pendingIntents.destroy()
    }

    private inner class KeyguardHandler: Destroyable {

        private val manager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Volatile
        private var _isDeviceLocked: Boolean = manager.isKeyguardLocked

        fun isDeviceLocked(): Boolean = _isDeviceLocked

        private val disposable = Receiver { intent ->
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

        override fun destroy() { disposable?.dispose() }

        override fun isDestroyed(): Boolean = disposable?.isDisposed ?: true
    }

    private inner class PendingIntents(
        contentIntentCode: Int,
        contentIntent: (code: Int, context: Context) -> PendingIntent?,
    ): Destroyable {

        @Volatile
        private var _isDestroyed: Boolean = false

        val contentIntent: PendingIntent? = contentIntent(contentIntentCode, appContext)

        override fun destroy() {
            if (_isDestroyed) return

            listOf(
                contentIntent,
            ).forEach { i ->
                try {
                    i?.cancel()
                } catch (_: Throwable) {}
            }
        }

        override fun isDestroyed(): Boolean = _isDestroyed
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
    }
}
