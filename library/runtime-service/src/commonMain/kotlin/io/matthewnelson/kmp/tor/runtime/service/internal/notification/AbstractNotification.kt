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
package io.matthewnelson.kmp.tor.runtime.service.internal.notification

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.EXECUTE.CMD.observeSignalNewNym
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.Destroyable
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.service.internal.ColorRes
import io.matthewnelson.kmp.tor.runtime.service.internal.DrawableRes
import io.matthewnelson.kmp.tor.runtime.service.internal.SynchronizedInstance
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.*
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentAction
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentBandwidth
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.content.ContentBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(InternalKmpTorApi::class)
internal abstract class AbstractNotification internal constructor(
    @JvmField
    protected val serviceScope: CoroutineScope,
    @JvmField
    protected val isServiceDestroyed: () -> Boolean,
    init: Any,
) {

    @Volatile
    private var _isDeviceLocked: Boolean = false
    private val views = SynchronizedInstance.of(LinkedHashMap<String, InstanceView>(1, 1.0f))

    @get:JvmName("isDeviceLocked")
    protected val isDeviceLocked: Boolean get() = _isDeviceLocked

    protected fun onDeviceLocked() {
        views.withLock {
            if (_isDeviceLocked) return@withLock emptyList()
            _isDeviceLocked = true

            values.mapTo(ArrayList(size)) { view ->
                Executable {
                    view.update { current ->
                        if (current.actions.isEmpty()) return@update null
                        current.copy(
                            actions = setOf(ButtonAction.NewIdentity),
                        )
                    }
                }
            }
        }.forEach { it.execute() }
    }

    protected fun onDeviceUnlocked() {
        views.withLock {
            if (!_isDeviceLocked) return@withLock emptyList()
            _isDeviceLocked = false

            values.mapTo(ArrayList(size)) { view ->
                Executable {
                    view.update { current ->
                        if (current.actions.isEmpty()) return@update null

                        val newActions = buildSet(1) {
                            add(ButtonAction.NewIdentity)

                            if (view.actionEnableRestart) {
                                add(ButtonAction.RestartTor)
                            }
                            if (view.actionEnableStop) {
                                add(ButtonAction.StopTor)
                            }
                        }

                        current.copy(
                            actions = newActions,
                        )
                    }
                }
            }
        }.forEach { it.execute() }
    }

    protected abstract fun InstanceView.remove()
    protected abstract fun InstanceView.render(old: NotificationState, new: NotificationState)

    internal inner class InstanceView(
        fid: FileID,
        internal val actionEnableRestart: Boolean,
        internal val actionEnableStop: Boolean,
        private val colorWhenBootstrappedTrue: ColorRes,
        private val colorWhenBootstrappedFalse: ColorRes,
        private val iconNetworkEnabled: DrawableRes,
        private val iconNetworkDisabled: DrawableRes,
        private val iconDataXfer: DrawableRes,
        private val lazyRuntime: () -> RuntimeEvent.Processor,
    ): Destroyable {

        @Volatile
        private var _bandwidth: ContentBandwidth = ContentBandwidth.ZERO
        @Volatile
        private var _isDestroyed: Boolean = false
        @Volatile
        private var _messageJob: Job? = null
        @Volatile
        private var _stateTor: TorState = TorState(TorState.Daemon.Off, TorState.Network.Disabled)
        @Volatile
        private var _state = NotificationState.of(
            actions = emptySet(),
            color = colorWhenBootstrappedFalse,
            icon = iconNetworkDisabled,
            progress = Progress.Indeterminate,
            text = ContentAction.of(Action.StartDaemon),
            title = _stateTor.daemon,
            fid = fid
        )

        private val fidString: String = fid.fid
        private val tag = (AbstractNotification::class.simpleName ?: "Notification") + "[fid=${fid.fidEllipses}]"
        private val lock = SynchronizedObject()

        @get:JvmName("state")
        internal val state: NotificationState get() = _state
        internal val observersEventRuntime: Set<RuntimeEvent.Observer<*>>
        internal val observersEventTor: Set<TorEvent.Observer>
        internal val requiredEventTor: Set<TorEvent> = TOR_EVENT_REQUIRED

        override fun isDestroyed(): Boolean = _isDestroyed

        override fun destroy() {
            if (_isDestroyed) return

            views.withLock {
                if (_isDestroyed) return@withLock null
                _isDestroyed = true

                val instance = get(fidString)
                if (instance != this@InstanceView) return@withLock null
                val removed = remove(fidString) ?: return@withLock null

                Executable {
                    removed.remove()
                }
            }?.execute()
        }

        internal fun update(block: (current: NotificationState) -> NotificationState?) {
            if (_isDestroyed) return

            synchronized(lock) {
                if (_isDestroyed) return@synchronized null
                if (isServiceDestroyed()) return@synchronized null

                val old = _state
                val new = block(old)
                if (new == null || old == new) {
                    null
                } else {
                    _state = new
                    Executable { render(old, new) }
                }
            }?.execute()
        }

        private fun postMessage(message: ContentMessage<*>, duration: Duration) {
            if (duration <= Duration.ZERO) return

            _messageJob?.cancel()
            _messageJob = serviceScope.launch {
                update { current ->
                    current.copy(
                        text = message,
                    )
                }

                delay(duration)

                if (!_stateTor.isBootstrapped) return@launch

                update { current ->
                    current.copy(
                        text = _bandwidth,
                    )
                }
            }
        }

        init {
            val executor = OnEvent.Executor.Immediate

            val oACTION = RuntimeEvent.EXECUTE.ACTION.observer(tag, executor) { job ->
                update { current ->
                    current.copy(
                        text = ContentAction.of(job.action),
                    )
                }
            }

            val oWARN = RuntimeEvent.LOG.WARN.observer(tag, executor) { log ->
                if (!log.endsWith("No Network Connectivity. Waiting...")) return@observer

                update { current ->
                    current.copy(
                        text = ContentNetworkWaiting,
                    )
                }
            }

            var newNymObserver = Disposable.noOp()
            val stateLock = SynchronizedObject()

            val oSTATE = RuntimeEvent.STATE.observer(tag, executor) { new ->
                synchronized(stateLock) {
                    val old = _stateTor
                    _stateTor = new

                    if (old.isOn && !new.isOn) {
                        newNymObserver.dispose()
                        _messageJob?.cancel()
                    }

                    val color = if (new.isBootstrapped && new.isNetworkEnabled) {
                        colorWhenBootstrappedTrue
                    } else {
                        colorWhenBootstrappedFalse
                    }

                    val progress = if (new.isOn) {
                        when {
                            !old.isBootstrapped
                            && new.isBootstrapped -> {
                                update { current ->
                                    current.copy(
                                        color = color,
                                        progress = Progress.Determinant(new.daemon),
                                        text = ContentBootstrap.of(new.daemon.bootstrap),
                                    )
                                }

                                Progress.None
                            }

                            !new.isBootstrapped
                            && new.isNetworkEnabled -> Progress.Determinant(new.daemon)

                            else -> Progress.None
                        }
                    } else {
                        Progress.Indeterminate
                    }

                    update { current ->
                        val text = when (progress) {
                            is Progress.Determinant -> ContentBootstrap.of(progress.value)
                            is Progress.Indeterminate,
                            is Progress.None -> {
                                val currentText = current.text

                                if (new.isNetworkEnabled && currentText is ContentBootstrap) {
                                    _bandwidth
                                } else {
                                    currentText
                                }
                            }
                        }

                        val actions = when (progress) {
                            is Progress.Determinant,
                            is Progress.Indeterminate -> emptySet()

                            is Progress.None -> when {
                                !new.isBootstrapped -> emptySet()
                                _isDeviceLocked -> setOf(ButtonAction.NewIdentity)
                                else -> buildSet {
                                    add(ButtonAction.NewIdentity)
                                    if (actionEnableRestart) {
                                        add(ButtonAction.RestartTor)
                                    }
                                    if (actionEnableStop) {
                                        add(ButtonAction.StopTor)
                                    }
                                }
                            }
                        }

                        val icon = when {
                            new.isNetworkDisabled -> iconNetworkDisabled
                            new.isNetworkEnabled && new.isBootstrapped -> {
                                if (_bandwidth !is ContentBandwidth.ZERO) {
                                    iconDataXfer
                                } else {
                                    iconNetworkEnabled
                                }
                            }

                            else -> iconNetworkDisabled
                        }

                        current.copy(
                            actions = actions,
                            color = color,
                            icon = icon,
                            progress = progress,
                            text = text,
                            title = new.daemon,
                        )
                    }
                }
            }

            val oREADY = RuntimeEvent.PROCESS.READY.observer(tag, executor) {
                synchronized(stateLock) {
                    newNymObserver.dispose()
                    newNymObserver = lazyRuntime().observeSignalNewNym(tag, executor) { line ->
                        val message = if (line == null) {
                            ContentMessage.NewNym.Success
                        } else {
                            ContentMessage.NewNym.RateLimited.of(line)
                        }

                        postMessage(message, 5_000.milliseconds)
                    }
                }
            }

            // 1608 2144
            val oBW = TorEvent.BW.observer(tag, executor) { bw ->
                val bandwidth = bw.trim().indexOfFirst { it.isWhitespace() }.let { i ->
                    if (i == -1) return@observer
                    val d = bw.substring(0, i).trim().toLongOrNull() ?: _bandwidth.down
                    val u = bw.substring(i + 1).trim().toLongOrNull() ?: _bandwidth.up

                    val new = _bandwidth.copy(down = d, up = u)
                    if (_bandwidth == new) return@observer

                    new
                }

                _bandwidth = bandwidth

                with(_stateTor) {
                    if (!isBootstrapped) return@observer
                    if (!isNetworkEnabled) return@observer
                }

                val icon = if (bandwidth !is ContentBandwidth.ZERO) {
                    iconDataXfer
                } else {
                    iconNetworkEnabled
                }

                update { current ->
                    val isMessageActive = _messageJob?.isActive == true

                    current.copy(
                        text = if (isMessageActive) current.text else bandwidth,
                        icon = icon,
                    )
                }
            }

            observersEventRuntime = immutableSetOf(
                oACTION,
                oWARN,
                oSTATE,
                oREADY,
            )
            observersEventTor = immutableSetOf(
                oBW,
            )

            views.withLock { put(fidString, this@InstanceView) }
        }
    }

    private companion object {

        private val TOR_EVENT_REQUIRED = immutableSetOf(TorEvent.BW)
    }

    protected object Synthetic {

        @JvmSynthetic
        internal val INIT = Any()
    }

    init {
        check(init == Synthetic.INIT) { "Notification cannot be extended." }
    }
}
