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
package io.matthewnelson.kmp.tor.runtime.service.ui

import android.app.Notification
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI
import io.matthewnelson.kmp.tor.runtime.service.TorServiceUI
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.Progress
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.retrieveString
import kotlinx.coroutines.*
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
    args: Args,
): TorServiceUI<
    KmpTorServiceUI.Config,
    KmpTorServiceUIInstanceState<KmpTorServiceUI.Config>
>(args) {

    /**
     * TODO
     * */
    public class Config private constructor(
        enableActionRestart: Boolean,
        enableActionStop: Boolean,
        init: Any
    ): AbstractKmpTorServiceUIConfig(
        enableActionRestart,
        enableActionStop,
        emptyMap(), // TODO
        init,
    ) {

        public constructor(): this({})

        public constructor(block: ThisBlock<Builder>): this(
            b = Builder.of(
                // ...
            ).apply(block)
        )

        private constructor(b: Builder): this(
            enableActionRestart = b.enableActionRestart,
            enableActionStop = b.enableActionStop,
            // TODO: Wrappers
            INIT,
        )

        @KmpTorDsl
        public class Builder private constructor() {

            @JvmField
            public var enableActionRestart: Boolean = false

            @JvmField
            public var enableActionStop: Boolean = false

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    // TODO
                ): Builder = Builder(
                    // TODO
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

            // TODO: content pending intent
            // TODO: notification builder options

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
            // TODO("Not yet implemented")
        }

        @Throws(Resources.NotFoundException::class)
        public override fun validateConfig(context: Context, config: Config) {
            // TODO("Not yet implemented")
        }

        protected override fun newInstanceUIProtected(
            args: Args,
        ): KmpTorServiceUI = KmpTorServiceUI(args)
    }

    private val startTime = SystemClock.elapsedRealtime()
    private val appLabel = appContext.applicationInfo.loadLabel(appContext.packageManager)

    private val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, channelID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }.apply {
        setOngoing(true)
        setOnlyAlertOnce(true)
        setWhen(System.currentTimeMillis())

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
        isDeviceLocked = { false /* TODO */ }
    )

    protected override fun onUpdate(target: FileIDKey, type: UpdateType) {
        val instanceStates = instanceStates
        val state = instanceStates[target]?.state ?: return

        val content = RemoteViews(appContext.packageName, R.layout.kmp_tor_ui_notification)

        // TODO: Config
        val iconRes = android.R.drawable.stat_notify_chat
        b.setSmallIcon(iconRes)

        val title = appContext.retrieveString(state.title)
        val text = appContext.retrieveString(state.text)

        // Headers
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            // API 23-
            content.setImageViewResource(R.id.kmp_tor_ui_header_icon, iconRes)

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
            b.setCustomBigContentView(content)
            b.build()
        } else {
            // API 23-
            @Suppress("DEPRECATION")
            b.setContent(content)
            b.build()
        }.post()
    }

    protected override fun onDestroy() {
        super.onDestroy()
        // TODO
    }

    private companion object {
        private val DURATION_1_DAY = 1.days
        private val DURATION_1_HOUR = 1.hours
    }
}
