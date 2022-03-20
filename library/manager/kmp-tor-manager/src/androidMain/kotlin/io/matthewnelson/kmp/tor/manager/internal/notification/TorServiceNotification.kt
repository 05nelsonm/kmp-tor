/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager.internal.notification

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal.Companion.NEW_NYM_RATE_LIMITED
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal.Companion.NEW_NYM_SUCCESS
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.manager.R
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.TorServiceConfig
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.*
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_CREATE
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_REGISTER
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_UNREGISTER
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Log.Warn.Companion.WAITING_ON_NETWORK
import io.matthewnelson.kmp.tor.manager.common.state.*
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Action as EventAction
import io.matthewnelson.kmp.tor.manager.internal.TorService
import io.matthewnelson.kmp.tor.manager.internal.TorServiceController
import io.matthewnelson.kmp.tor.manager.internal.notification.NotificationState.*
import io.matthewnelson.kmp.tor.manager.internal.wrappers.retrieve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.security.SecureRandom
import java.text.NumberFormat
import java.util.*
import kotlin.math.roundToLong

internal abstract class TorServiceNotification(
    protected val config: TorServiceConfig,
    protected val serviceScope: CoroutineScope,
    protected val isServiceDestroyed: () -> Boolean,
): TorManagerEvent.Listener() {

    private val actionsEmpty: List<Action> = emptyList()
    private val actionsNewId: List<Action> = listOf(Action.NewIdentity)
    @OptIn(ExperimentalStdlibApi::class)
    private val actionsAll: List<Action> = buildList {
        add(Action.NewIdentity)
        if (config.enableRestartAction) {
            add(Action.RestartTor)
        }
        if (config.enableStopAction) {
            add(Action.StopTor)
        }
    }

    protected abstract fun render(old: NotificationState, new: NotificationState)
    protected abstract fun EventAction.provideStringFor(): String
    protected abstract fun TorState.provideStringFor(): String
    protected abstract fun provideStringForBootstrapped(): String
    protected abstract fun provideStringForWaitingOnNetwork(): String

    @Volatile
    private var _currentState: NotificationState? = null
    protected val currentState: NotificationState get() {
        return _currentState.let { cState ->
            cState ?: NotificationState(
                actions = actionsEmpty,
                color = config._colorWhenBootstrappedFalse,
                contentText = EventAction.Start.provideStringFor(),
                contentTitle = TorState.Off.provideStringFor(),
                progress = Progress.Indeterminate,
                smallIcon = config._iconNetworkDisabled,
            ).also { _currentState = it }
        }
    }

    private var isDeviceLocked = false
    protected fun deviceLocked() {
        if (isDeviceLocked) return
        isDeviceLocked = true
        if (currentState.actions.isEmpty()) return
        render(currentState.copy(actions = actionsNewId))
    }

    protected fun deviceUnlocked() {
        if (!isDeviceLocked) return
        isDeviceLocked = false
        if (currentState.actions.isEmpty()) return
        render(currentState.copy(actions = actionsAll))
    }

    private fun render(new: NotificationState) {
        isError = false
        if (currentState == new) return
        val old = currentState
        _currentState = new
        if (!config.enableForeground) return
        if (isServiceDestroyed.invoke()) return
        render(old = old, new = new)
    }

    private var messageJob: Job? = null
    @JvmSynthetic
    fun postMessage(message: String, millis: Long = 3_500) {
        messageJob?.cancel()
        messageJob = serviceScope.launch {
            render(currentState.copy(contentText = message))
            delay(millis)

            if (netState.isEnabled() || state.isBootstrapped) {
                render(currentState.copy(contentText = formatBandwidth(down, up)))
            }
        }
    }

    var isError: Boolean = false
        private set
    @JvmSynthetic
    fun postError(t: Throwable) {
        render(currentState.copy(
            actions = actionsEmpty,
            contentText = "${t.javaClass.simpleName}(${t.message})",
            progress = Progress.None,
            smallIcon = config._iconError
        ))
        isError = true
    }

    @JvmSynthetic
    open fun stoppingService() {
        messageJob?.cancel()
        render(NotificationState(
            actions = actionsEmpty,
            color = config._colorWhenBootstrappedFalse,
            contentText = EventAction.Stop.provideStringFor(),
            contentTitle = TorState.Stopping.provideStringFor(),
            progress = Progress.Indeterminate,
            smallIcon = config._iconNetworkDisabled,
        ))
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected var state: TorState = TorState.Off
        private set
    protected var netState: TorNetworkState = TorNetworkState.Disabled
        private set

    override fun managerEventActionRestart() {
        render(currentState.copy(
            contentText = TorManagerEvent.Action.Restart.provideStringFor()
        ))
    }

    override fun managerEventActionStart() {
        render(currentState.copy(
            contentText = TorManagerEvent.Action.Start.provideStringFor()
        ))
    }

    override fun managerEventActionStop() {
        render(currentState.copy(
            contentText = TorManagerEvent.Action.Stop.provideStringFor()
        ))
    }

    override fun managerEventWarn(message: String) {
        if (message == WAITING_ON_NETWORK) {
            render(currentState.copy(contentText = provideStringForWaitingOnNetwork()))
        }
    }

    override fun managerEventState(state: State) {
        val old = this.state
//        val oldNet = netState
        val new = state.torState
        val newNet = state.networkState

        if (old.isOn() && !new.isOn()) {
            // update current state immediately so
            // bandwidth updates cease
            this.state = new
        }

        val color = if (new.isBootstrapped && newNet.isEnabled()) {
            config._colorWhenBootstrappedTrue
        } else {
            config._colorWhenBootstrappedFalse
        }

        val progress = if(new.isOn()) {

            when {
                !old.isBootstrapped && new.isBootstrapped -> {
                    render(currentState.copy(
                        color = color,
                        contentText = "${provideStringForBootstrapped()} ${new.bootstrap}%",
                        progress = Progress.Determinant(new.bootstrap),
                    ))
                    Progress.None
                }
                !new.isBootstrapped && newNet.isEnabled() -> {
                    Progress.Determinant(new.bootstrap)
                }
                else -> {
                    Progress.None
                }
            }

        } else {
            Progress.Indeterminate
        }

        val contentText = when (progress) {
            is Progress.Determinant -> {
                "${provideStringForBootstrapped()} ${progress.value}%"
            }
            is Progress.Indeterminate,
            is Progress.None -> {
                val current = currentState.contentText
                if (current.startsWith(provideStringForBootstrapped()) && newNet.isEnabled()) {
                    formatBandwidth(down, up)
                } else {
                    current
                }
            }
        }

        val actions = when (progress) {
            is Progress.Determinant,
            is Progress.Indeterminate -> {
                actionsEmpty
            }
            is Progress.None -> {
                when {
                    !new.isBootstrapped -> actionsEmpty
                    isDeviceLocked -> actionsNewId
                    else -> actionsAll
                }
            }
        }

        val icon = when {
            newNet.isDisabled() -> {
                // to disable posting any more bandwidth updates
                netState = newNet

                config._iconNetworkDisabled
            }
            newNet.isEnabled() && new.isBootstrapped -> {
                if (down != 0L || up != 0L) {
                    config._iconDataXfer
                } else {
                    config._iconNetworkEnabled
                }
            }
            else -> {
                config._iconNetworkDisabled
            }
        }

        render(NotificationState(
            actions = actions,
            color = color,
            contentText = contentText,
            contentTitle = new.provideStringFor(),
            progress = progress,
            smallIcon = icon
        ))

        this.state = new
        this.netState = newNet
    }

    override fun onEvent(event: TorManagerEvent) {
        if (!config.enableForeground) return
        super.onEvent(event)
    }

    private val formatter = NumberFormat.getInstance(Locale.getDefault())
    private var down = 0L
    private var up = 0L

    override fun eventBandwidthUsed(output: String) {
        // 1608 2144
        val splits = output.trim().split(' ')
        if (splits.size != 2) return

        val down = try {
            splits[0].toLong()
        } catch (_: NumberFormatException) {
            down
        }
        val up = try {
            splits[1].toLong()
        } catch (_: NumberFormatException) {
            up
        }

        if (down != this.down || up != this.up) {
            this.down = down
            this.up = up

            if (state.isBootstrapped && netState.isEnabled()) {

                val icon = if (down == 0L && up == 0L) {
                    config._iconNetworkEnabled
                } else {
                    config._iconDataXfer
                }

                val contentText = if (messageJob?.isActive == true) {
                    currentState.contentText
                } else {
                    formatBandwidth(down, up)
                }

                render(
                    currentState.copy(
                        contentText = contentText,
                        smallIcon = icon
                    )
                )
            }
        }
    }

    private fun formatBandwidth(down: Long, up: Long): String =
        "${formatBandwidth(down)} ↓ / ${formatBandwidth(up)} ↑"

    private fun formatBandwidth(value: Long): String =
        if (value < 1e6) {
            formatter.format(
                (((value * 10 / 1024).toInt()) / 10).toFloat().roundToLong()
            ) + "KBps"
        } else {
            formatter.format(
                (((value * 100 / 1024 / 1024).toInt()) / 100).toFloat().roundToLong()
            ) + "MBps"
        }

    override fun onEvent(event: TorEvent.Type.SingleLineEvent, output: String) {
        if (!config.enableForeground) return
        super.onEvent(event, output)
    }

    override fun onEvent(event: TorEvent.Type.MultiLineEvent, output: List<String>) {}

    companion object {
        @JvmSynthetic
        fun newInstance(
            config: TorServiceConfig,
            service: TorService,
            serviceScope: CoroutineScope,
            isServiceDestroyed: () -> Boolean,
            stopService: () -> Unit,
            restartTor: () -> Unit,
            managerProvider: () -> TorManager?,
        ): TorServiceNotification =
            RealTorServiceNotification(
                config,
                service,
                serviceScope,
                isServiceDestroyed,
                stopService,
                restartTor,
                managerProvider
            )
    }
}

private class RealTorServiceNotification(
    config: TorServiceConfig,
    private val service: TorService,
    serviceScope: CoroutineScope,
    isServiceDestroyed: () -> Boolean,
    private val stopService: () -> Unit,
    private val restartTor: () -> Unit,
    private val managerProvider: () -> TorManager?
): TorServiceNotification(config, serviceScope, isServiceDestroyed) {

    companion object {
        private var isChannelSetup = false
    }

    private val notificationManager: NotificationManager?
        get() = service.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val startTime = System.currentTimeMillis()
    private val intentFilter: String = BigInteger(130, SecureRandom()).toString(32)

    private fun Progress.set(builder: Notification.Builder) {
        when (this) {
            is Progress.Determinant -> {
                builder.setProgress(100, value, false)
            }
            is Progress.Indeterminate -> {
                builder.setProgress(100, 0, true)
            }
            is Progress.None -> {
                builder.setProgress(0, 0, false)
            }
        }
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    @Suppress("deprecation")
    private fun List<Action>.set(builder: Notification.Builder) {
        for (action in this) {
            val intent = Intent(intentFilter)
            intent.putExtra(intentFilter, action.name)
            intent.setPackage(service.packageName)

            val pIntent = PendingIntent.getBroadcast(
                service,
                action.requestCode,
                intent,
                pendingIntentFlags()
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                val aBuilder = Notification.Action.Builder(0, action.provideStringFor(), pIntent)
                builder.addAction(aBuilder.build())
            } else {
                builder.addAction(0, action.provideStringFor(), pIntent)
            }
        }
    }

    @Suppress("deprecation")
    private fun newBuilder(state: NotificationState): Notification.Builder {
        val builder: Notification.Builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26
            Notification.Builder(service, config.channelId)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY)
                .setTimeoutAfter(10L)
        } else {
            Notification.Builder(service)
        }

        builder.apply {
            setContentText(state.contentText)
            setContentTitle(state.contentTitle)
            setOngoing(true)
            setOnlyAlertOnce(true)
            setSmallIcon(state.smallIcon.id)
            setSound(null)
            setWhen(startTime)

            // API 17
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                setShowWhen(true)
            }
            // API 20
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                setGroup("TorService")
                setGroupSummary(false)
            }
            // API 21
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setCategory(Notification.CATEGORY_PROGRESS)
                setColor(state.color.retrieve(service))
                setVisibility(config.visibility)
            }
            // API 31
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
        }

        state.actions.set(builder)
        state.progress.set(builder)

        service
            .packageManager
            ?.getLaunchIntentForPackage(service.packageName)
            ?.let { intent ->
                builder.setContentIntent(
                    PendingIntent.getActivity(
                        service,
                        0,
                        intent,
                        pendingIntentFlags()
                    )
                )
            }

        return builder
    }

    private var currentBuilder: Notification.Builder = newBuilder(currentState)

    private fun notify(builder: Notification.Builder) {
        currentBuilder = builder
        notificationManager?.notify(config.notificationId, builder.build())
    }

    private fun Action.provideStringFor(): String {
        return when (this) {
            Action.NewIdentity -> service.getString(R.string.kmp_tor_notification_action_newnym)
            Action.RestartTor -> service.getString(R.string.kmp_tor_notification_action_restart)
            Action.StopTor -> service.getString(R.string.kmp_tor_notification_action_stop)
        }
    }

    override fun TorManagerEvent.Action.provideStringFor(): String {
        return when (this) {
            // Not utilized in the notification
            is TorManagerEvent.Action.Controller -> { /* no-op */ "" }
            is TorManagerEvent.Action.Restart -> {
                service.getString(R.string.kmp_tor_action_restart)
            }
            is TorManagerEvent.Action.Start -> {
                service.getString(R.string.kmp_tor_action_start)
            }
            is TorManagerEvent.Action.Stop -> {
                service.getString(R.string.kmp_tor_action_stop)
            }
        }
    }

    override fun TorState.provideStringFor(): String {
        return when (this) {
            is TorState.Off -> service.getString(R.string.kmp_tor_tor_state_off)
            is TorState.On -> service.getString(R.string.kmp_tor_tor_state_on)
            is TorState.Starting -> service.getString(R.string.kmp_tor_tor_state_starting)
            is TorState.Stopping -> service.getString(R.string.kmp_tor_tor_state_stopping)
        }
    }

    override fun provideStringForBootstrapped(): String {
        return service.getString(R.string.kmp_tor_bootstrapped)
    }

    override fun provideStringForWaitingOnNetwork(): String {
        return service.getString(R.string.kmp_tor_waiting_on_network)
    }

    override fun render(old: NotificationState, new: NotificationState) {
        if (new.isActionRefreshNeeded(old.actions)) {
            notify(newBuilder(new))
            return
        }

        val builder = currentBuilder

        new.diff(
            old = old,
            setColor = { color ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.setColor(color.retrieve(service))
                }
            },            setContentText = { text ->
                builder.setContentText(text)
            },
            setContentTitle = { title ->
                builder.setContentTitle(title)
            },
            setProgress = { progress ->
                progress.set(builder)
            },
            setSmallIcon = { icon ->
                builder.setSmallIcon(icon.id)
            }
        )

        notify(builder)
    }

    private val receiver = NotificationReceiver()
    private inner class NotificationReceiver: BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return

            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    val manager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    if (manager?.isKeyguardLocked == true) {
                        deviceLocked()
                    } else {
                        deviceUnlocked()
                    }
                }
                intentFilter -> {
                    val action = try {
                        Action.valueOf(intent.getStringExtra(intentFilter) ?: return)
                    } catch (e: Exception) {
                        TorServiceController.notify(Log.Error(e))
                    }

                    when (action) {
                        Action.NewIdentity -> {
                            if (netState.isDisabled()) {
                                postMessage(context.getString(R.string.kmp_tor_newnym_no_network))
                                return
                            }

                            val manager = managerProvider.invoke() ?: return

                            serviceScope.launch {
                                val result = manager.signal(TorControlSignal.Signal.NewNym)
                                result.onSuccess {
                                    if (it !is String) {
                                        return@onSuccess
                                    }

                                    val post: String? = when {
                                        it.startsWith(NEW_NYM_RATE_LIMITED) -> {
                                            // Rate limiting NEWNYM request: delaying by 8 second(s)
                                            val seconds: Int? = it.drop(NEW_NYM_RATE_LIMITED.length)
                                                .substringBefore(' ')
                                                .toIntOrNull()

                                            if (seconds == null) {
                                                it
                                            } else {
                                                context.getString(
                                                    R.string.kmp_tor_newnym_rate_limited,
                                                    seconds
                                                )
                                            }
                                        }
                                        it == NEW_NYM_SUCCESS -> {
                                            context.getString(R.string.kmp_tor_newnym_success)
                                        }
                                        else -> {
                                            null
                                        }
                                    }

                                    if (post != null) {
                                        postMessage(post, millis = 5_000)
                                    }
                                }
                                result.onFailure {
                                    TorServiceController.notify(Log.Error(it))
                                }
                            }
                        }
                        Action.RestartTor -> {
                            restartTor.invoke()
                        }
                        Action.StopTor -> {
                            stopService.invoke()
                        }
                    }
                }
            }
        }
    }

    override fun stoppingService() {
        super.stoppingService()
        service.unregisterReceiver(receiver)
        TorServiceController.notify(Lifecycle(receiver, ON_UNREGISTER))
    }

    init {
        TorServiceController.notify(Lifecycle(this, ON_CREATE))
        TorServiceController.notify(Lifecycle(receiver, ON_CREATE))

        if (config.enableForeground) {

            if (!isChannelSetup) {
                isChannelSetup = true

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        config.channelId,
                        config.channelName,
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        setShowBadge(config.channelShowBadge)
                        description = config.channelDescription
                        setSound(null, null)
                    }
                    notificationManager?.createNotificationChannel(channel)
                }
            }

            val filter = IntentFilter(intentFilter)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_USER_PRESENT)

            service.registerReceiver(receiver, filter)
            TorServiceController.notify(Lifecycle(receiver, ON_REGISTER))
            service.startForeground(config.notificationId, currentBuilder.build())
        }
    }
}