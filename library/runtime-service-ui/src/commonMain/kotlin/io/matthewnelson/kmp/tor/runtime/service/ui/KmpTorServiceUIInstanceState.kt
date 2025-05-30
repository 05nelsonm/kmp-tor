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
@file:Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")

package io.matthewnelson.kmp.tor.runtime.service.ui

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.ButtonAction
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.IconState
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.UIState
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.Progress
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentAction
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentBandwidth
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentBootstrap
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentMessage
import io.matthewnelson.kmp.tor.runtime.service.ui.internal.content.ContentNetworkWaiting
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalKmpTorApi::class)
public class KmpTorServiceUIInstanceState<C: AbstractKmpTorServiceUIConfig> private constructor(
    args: AbstractTorServiceUI.Args.Instance,
    private val isDeviceLocked: () -> Boolean,
): AbstractTorServiceUI.InstanceState<C>(args) {

    @Volatile
    private var _bandwidth: ContentBandwidth = ContentBandwidth.ZERO
    @Volatile
    private var _messageJob: Job? = null
    @Volatile
    private var _state = UIState.of(fid = this)
    @Volatile
    private var _stateTor: TorState = TorState(_state.title, TorState.Network.Disabled)

    @OptIn(InternalKmpTorApi::class)
    private val lockUpdate = synchronizedObject()

    @get:JvmName("state")
    internal val state: UIState get() = _state

    public override val events: Set<TorEvent> = EVENTS
    public override val observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>
    public override val observersTorEvent: Set<TorEvent.Observer>

    internal fun onDeviceLockChange() {
        if (!instanceConfig.enableActionRestart && !instanceConfig.enableActionStop) {
            return
        }

        update { current ->
            val newActions = current.progress.toActions()
            current.copy(actions = newActions)
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun update(block: (current: UIState) -> UIState?) {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

        @OptIn(InternalKmpTorApi::class)
        synchronized(lockUpdate) {
            val old = _state
            val new = block(old)
            if (new == null || old == new) {
                null
            } else {
                _state = new
                // Post the change outside the lock lambda
                Executable {
                    postStateChange()
                    debug(new::toString)
                }
            }
        }?.execute()
    }

    private fun postMessage(message: ContentMessage<*>, duration: Duration) {
        if (duration <= Duration.ZERO) return

        @OptIn(InternalKmpTorApi::class)
        synchronized(lockUpdate) {
            val oldJob = _messageJob
            _messageJob = instanceScope.launch {
                oldJob?.cancelAndJoin()

                update { current ->
                    current.copy(
                        text = message,
                    )
                }

                delay(duration)

                update { current ->
                    ensureActive()
                    if (!_stateTor.daemon.isBootstrapped) return@update null

                    current.copy(
                        text = _bandwidth,
                    )
                }
            }.also { newJob -> newJob.invokeOnCompletion { oldJob?.cancel() } }
        }
    }

    private fun Progress.toActions(): Set<ButtonAction> = when (this) {
        is Progress.Determinant,
        is Progress.Indeterminate -> emptySet()
        is Progress.None -> when {
            !_stateTor.daemon.isBootstrapped -> emptySet()
            isDeviceLocked() -> setOf(ButtonAction.NewIdentity)
            else -> buildSet {
                add(ButtonAction.NewIdentity)

                if (instanceConfig.enableActionRestart) {
                    add(ButtonAction.RestartTor)
                }
                if (instanceConfig.enableActionStop) {
                    add(ButtonAction.StopTor)
                }
            }
        }
    }

    init {
        val executor = OnEvent.Executor.Immediate
        val tag = toString()

        var lastAction = Action.StartDaemon
        val oACTION = RuntimeEvent.EXECUTE.ACTION.observer(tag, executor) { job ->
            update {
                lastAction = job.action
                null
            }
        }

        val oWARN = RuntimeEvent.LOG.WARN.observer(tag, executor) { log ->
            if (!log.endsWith("No Network Connectivity. Waiting...")) return@observer

            update { current ->
                current.copy(
                    text = ContentNetworkWaiting,
                    progress = Progress.Indeterminate,
                )
            }
        }

        var newNymObserver: Disposable.Once? = null
        @OptIn(InternalKmpTorApi::class)
        val stateLock = synchronizedObject()

        val oSTATE = RuntimeEvent.STATE.observer(tag, executor) { new ->
            @OptIn(InternalKmpTorApi::class)
            synchronized(stateLock) {
                val old = _stateTor
                _stateTor = new

                if (old.daemon.isOn && !new.daemon.isOn) {
                    newNymObserver?.dispose()
                    _messageJob?.cancel()
                }

                val progress = when {
                    !new.daemon.isOn -> {
                        Progress.Indeterminate
                    }
                    !old.daemon.isBootstrapped && new.daemon.isBootstrapped -> {
                        update { current ->
                            current.copy(
                                progress = Progress.Determinant(new.daemon),
                                text = ContentBootstrap.of(new.daemon.bootstrap),
                            )
                        }

                        Progress.None
                    }
                    !new.daemon.isBootstrapped -> {
                        Progress.Determinant(new.daemon)
                    }
                    else -> when (_state.text) {
                        is ContentAction,
                        is ContentNetworkWaiting -> {
                            Progress.Indeterminate
                        }
                        is ContentBootstrap -> {
                            Progress.Determinant(new.daemon)
                        }
                        else -> Progress.None
                    }
                }

                update { current ->
                    val text = when (progress) {
                        is Progress.Determinant -> ContentBootstrap.of(progress.value)
                        is Progress.Indeterminate -> ContentAction.of(lastAction)
                        is Progress.None -> {
                            val currentText = current.text

                            if (new.network.isEnabled && currentText is ContentBootstrap) {
                                _bandwidth
                            } else {
                                currentText
                            }
                        }
                    }

                    val actions = progress.toActions()

                    val icon = when {
                        new.network.isDisabled -> IconState.NotReady
                        new.daemon.isBootstrapped -> if (_bandwidth !is ContentBandwidth.ZERO) {
                            IconState.Data
                        } else {
                            IconState.Ready
                        }
                        else -> IconState.NotReady
                    }

                    current.copy(
                        actions = actions,
                        icon = icon,
                        progress = progress,
                        text = text,
                        title = new.daemon,
                    )
                }
            }
        }

        val oREADY = RuntimeEvent.READY.observer(tag, executor) {
            @OptIn(InternalKmpTorApi::class)
            synchronized(stateLock) {
                newNymObserver?.dispose()
                newNymObserver = observeSignalNewNym(tag, executor) { line ->
                    val message = if (line == null) {
                        ContentMessage.NewNym.Success
                    } else {
                        ContentMessage.NewNym.RateLimited.of(line)
                    }

                    postMessage(message, 5_000.milliseconds)
                }
            }
        }

        val oBW = TorEvent.BW.observer(tag, executor) { bw ->
            // 1608 2144
            val bandwidth = bw.trim().indexOfFirst { it.isWhitespace() }.let { i ->
                if (i == -1) return@observer
                val d = bw.substring(0, i).trim().toLongOrNull() ?: _bandwidth.down
                val u = bw.substring(i + 1).trim().toLongOrNull() ?: _bandwidth.up

                val new = _bandwidth.copy(down = d, up = u)
                if (_bandwidth == new) {
                    postStateChange()
                    return@observer
                }

                new
            }

            _bandwidth = bandwidth

            with(_stateTor) {
                if (!daemon.isBootstrapped || !network.isEnabled) {
                    postStateChange()
                    return@observer
                }
            }

            val icon = if (bandwidth !is ContentBandwidth.ZERO) {
                IconState.Data
            } else {
                IconState.Ready
            }

            update { current ->
                val isMessageActive = _messageJob?.isActive == true

                current.copy(
                    text = if (isMessageActive) current.text else bandwidth,
                    icon = icon,
                )
            }
        }

        observersRuntimeEvent = immutableSetOf(
            oACTION,
            oWARN,
            oSTATE,
            oREADY,
        )

        observersTorEvent = immutableSetOf(
            oBW,
        )
    }

    internal companion object {

        @JvmSynthetic
        internal fun <C: AbstractKmpTorServiceUIConfig> of(
            args: AbstractTorServiceUI.Args.Instance,
            isDeviceLocked: () -> Boolean,
        ): KmpTorServiceUIInstanceState<C> = KmpTorServiceUIInstanceState(
            args,
            isDeviceLocked,
        )

        private val EVENTS = immutableSetOf(TorEvent.BW)
    }
}
