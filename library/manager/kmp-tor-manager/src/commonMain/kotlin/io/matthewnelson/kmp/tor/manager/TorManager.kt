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
@file:Suppress("FunctionName")

package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.TorController
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.usecase.*
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal.Companion.NEW_NYM_RATE_LIMITED
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal.Companion.NEW_NYM_SUCCESS
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.controller.common.exceptions.ControllerShutdownException
import io.matthewnelson.kmp.tor.controller.internal.DebugItem
import io.matthewnelson.kmp.tor.controller.internal.Debuggable
import io.matthewnelson.kmp.tor.controller.internal.controller.ListenersHandler
import io.matthewnelson.kmp.tor.manager.common.TorControlManager
import io.matthewnelson.kmp.tor.manager.common.TorOperationManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Action.*
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_CREATE
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_DESTROY
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Log.Warn.Companion.WAITING_ON_NETWORK
import io.matthewnelson.kmp.tor.manager.common.exceptions.InterruptedException
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorNotStartedException
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import io.matthewnelson.kmp.tor.manager.common.state.TorStateManager
import io.matthewnelson.kmp.tor.manager.common.state.isEnabled
import io.matthewnelson.kmp.tor.manager.internal.actions.ActionProcessor
import io.matthewnelson.kmp.tor.manager.internal.actions.ActionQueue
import io.matthewnelson.kmp.tor.manager.internal.BaseTorManager
import io.matthewnelson.kmp.tor.manager.internal.TorStateMachine
import io.matthewnelson.kmp.tor.manager.internal.ext.*
import io.matthewnelson.kmp.tor.manager.internal.ext.dnsOpened
import io.matthewnelson.kmp.tor.manager.internal.ext.httpOpened
import io.matthewnelson.kmp.tor.manager.internal.ext.socksOpened
import io.matthewnelson.kmp.tor.manager.internal.ext.transOpened
import io.matthewnelson.kmp.tor.manager.internal.util.realTorManagerInstanceDestroyed
import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.coroutineContext
import kotlin.jvm.JvmSynthetic

/**
 * [TorManager]'s primary responsibility is to ensure synchronous execution of
 * Tor operations.
 *
 * By implementing [TorControlManager], [TorManager] facilitates pass-through
 * interaction with [TorController] (which is connected to automatically upon
 * every [start]).
 *
 * Interactions with [TorController] are single locking, first-in-first-out
 * ordered. This can be problematic if needing to abruptly [stop] or [restart]
 * Tor. To ensure that immediate reaction to those special events can be had,
 * [TorManager] implements a double locking queue system such that suspending
 * methods waiting to interact with [TorController] can be interrupted and
 * cancelled immediately.
 *
 * [TorManager] also handles a lot of conveniences such as:
 *  - Parsing [TorEvent]s in order to notify attached listeners with more usefully
 *  formatted data.
 *  - Tracks and dispatches State
 *  - Threading (all methods can safely be called from the Main thread)
 *  - etc.
 *
 * @see [RealTorManager]
 * @see [Destroyable]
 * @see [TorControlManager]
 * @see [TorOperationManager]
 * @see [TorStateManager]
 * @see [TorEventProcessor]
 * */
expect interface TorManager:
    Destroyable,
    TorControlManager,
    TorOperationManager,
    TorStateManager,
    TorEventProcessor<TorManagerEvent.SealedListener>
{
    val instanceId: String

    fun debug(enable: Boolean)
}

@JvmSynthetic
internal fun realTorManager(
    loader: KmpTorLoader,
    instanceId: String,
    processorLock: Mutex,
    main: CoroutineDispatcher = try {
        Dispatchers.Main.immediate
    } catch (e: UnsupportedOperationException) {
        Dispatchers.Main
    },
    networkObserver: NetworkObserver? = null,
    requiredEvents: Set<TorEvent>? = null,
): TorManager =
    RealTorManager(
        loader,
        instanceId,
        processorLock,
        main,
        networkObserver,
        requiredEvents
    )

@OptIn(InternalTorApi::class)
@Suppress("CanBePrimaryConstructorProperty")
private class RealTorManager(
    loader: KmpTorLoader,
    instanceId: String,
    processorLock: Mutex,
    main: CoroutineDispatcher,
    networkObserver: NetworkObserver?,
    requiredEvents: Set<TorEvent>?,
) : BaseTorManager(),
    TorManager
{

    private val requiredEvents: Set<TorEvent> = requiredEvents
        ?.toMutableSet()
        ?.apply {
            add(TorEvent.LogMsg.Notice)
            add(TorEvent.ConfChanged)
        }
        ?: setOf(TorEvent.LogMsg.Notice, TorEvent.ConfChanged)

    private val debug: AtomicBoolean = atomic(false)
    private val listeners: ListenersHandler = ListenersHandler.newInstance(3) {
        // Listener threw an exception when being notified for a TorEvent
        try {
            (it.listener as TorManagerEvent.SealedListener).onEvent(TorManagerEvent.Log.Error(it.error.value))
        } catch (_: Exception) {}
    }
    private val controllerListener: ControllerListener = ControllerListener()

    private val supervisor: CompletableJob = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(context =
        supervisor                                  +
        main                                        +
        CoroutineName(name = "tor_manager_scope")
    )
    private val coroutineCounter: AtomicLong = atomic(0L)

    override val instanceId: String = instanceId
    private val networkObserver: NetworkObserver? = networkObserver
    private val disableNetwork = TorConfig.Setting.DisableNetwork()
    private val networkObserverJob: AtomicRef<Job?> = atomic(null)

    private val loader: KmpTorLoader by lazy {
        // Notify lazily so listeners can be added after initialization
        notifyListenersNoScope(TorManagerEvent.Lifecycle(this, ON_CREATE))

        // Attach to network observer
        networkObserver?.attach { connectivity ->
            when (connectivity) {
                NetworkObserver.Connectivity.Connected -> {
                    controller.value?.first?.let { controller ->
                        networkObserverJob.update { job ->
                            job?.cancel()
                            scope.launch {
                                delay(300L)
                                val result = controller.configSet(
                                    TorConfig.Setting.DisableNetwork()
                                        .set(TorConfig.Option.TorF.False)
                                )
                                if (result.isSuccess) {
                                    stateMachine.updateState(TorNetworkState.Enabled)
                                }
                            }
                        }
                    }
                }
                NetworkObserver.Connectivity.Disconnected -> {
                    controller.value?.first?.let { controller ->
                        networkObserverJob.update { job ->
                            job?.cancel()
                            scope.launch {
                                delay(300L)
                                val result = controller.configSet(
                                    TorConfig.Setting.DisableNetwork()
                                        .set(TorConfig.Option.TorF.True)
                                )
                                if (result.isSuccess) {
                                    stateMachine.updateState(TorNetworkState.Disabled)
                                }
                            }
                        }
                    }
                }
            }
        }

        loader
    }

    private val actions: ActionProcessor = ActionProcessor.newInstance(processorLock)
    private val controller: AtomicRef<Pair<
        TorController,
        TorManagerEvent.StartUpCompleteForTorInstance?
    >?> = atomic(null)

    private val _addressInfo = atomic(TorManagerEvent.AddressInfo.NULL_VALUES)
    private val addressInfoJob: AtomicRef<Job?> = atomic(null)

    // State should be dispatched immediately. as such, only update state machine
    // from Dispatchers.Main
    private val stateMachine: TorStateMachine = TorStateMachine.newInstance { old, new ->
        notifyListenersNoScope(new)

        _addressInfo.update { info ->
            info.onStateChange(old, new)?.let { newInfo ->
                addressInfoJob.value?.cancel()
                notifyListenersNoScope(newInfo)
                newInfo
            } ?: info
        }

        // Bootstrapping completed
        if (!old.torState.isBootstrapped && new.torState.isBootstrapped && new.isNetworkEnabled) {
            controller.update { pair ->
                if (pair == null) return@update null
                if (pair.second != null) return@update pair

                notifyListenersNoScope(TorManagerEvent.StartUpCompleteForTorInstance)

                Pair(pair.first, TorManagerEvent.StartUpCompleteForTorInstance)
            }
        }
    }
    override val state: TorState get() = stateMachine.state
    override val networkState: TorNetworkState get() = stateMachine.networkState
    override val addressInfo: TorManagerEvent.AddressInfo
        get() = if (state.isBootstrapped && networkState.isEnabled()) {
            _addressInfo.value
        } else {
            TorManagerEvent.AddressInfo.NULL_VALUES
        }

    private val _isDestroyed: AtomicBoolean = atomic(false)
    override val isDestroyed: Boolean get() = _isDestroyed.value

    override fun destroy(stopCleanly: Boolean, onCompletion: (() -> Unit)?) {
        synchronized(this) {
            if (isDestroyed) return@synchronized
            _isDestroyed.value = true
            networkObserver?.detach()

            if (!stopCleanly) {
                controller.value?.first?.disconnect()
                supervisor.cancel()
                loader.close()
                realTorManagerInstanceDestroyed(instanceId)

                listeners.withLock {
                    for (listener in this) {
                        (listener as TorManagerEvent.SealedListener)
                            .onEvent(TorManagerEvent.Lifecycle(this@RealTorManager, ON_DESTROY))
                    }
                    this.clear()
                }

                onCompletion?.invoke()
                return@synchronized
            }

            scope.launch(context =
                Stop.catchInterrupt {}                                               +
                CoroutineName(name ="${Stop}_${coroutineCounter.getAndIncrement()}")
            ) {
                actions.withProcessorLock(Stop) {
                    controller.value?.first?.let { controller ->
                        realStop(controller, isRestart = false)
                    } ?: Result.success("Tor was already stopped")
                }
            }.invokeOnCompletion {
                supervisor.cancel()
                loader.close()
                realTorManagerInstanceDestroyed(instanceId)

                listeners.withLock {
                    for (listener in this) {
                        (listener as TorManagerEvent.SealedListener)
                            .onEvent(TorManagerEvent.Lifecycle(this@RealTorManager, ON_DESTROY))
                    }
                    this.clear()
                }

                onCompletion?.invoke()
            }
        }
    }

    override fun startQuietly() {
        if (isDestroyed) return

        scope.launch {
            start().onFailure { ex ->
                notifyListenersNoScope(TorManagerEvent.Log.Error(ex))
            }
        }
    }

    override suspend fun start(): Result<Any?> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<Any?>? = null

        scope.launch(context =
            Start.catchInterrupt { result = Result.failure(it) }                     +
            CoroutineName(name = "${Start}_${coroutineCounter.getAndIncrement()}")
        ) {
            result = actions.withProcessorLock(Start) {

                if (isDestroyed) {
                    return@withProcessorLock Result.failure(
                        TorManagerException("TorManager instance has been destroyed")
                    )
                }

                realStart()
            }
        }.join()

        if (result == null) {
            // wait for exception to propagate to handler
            delay(25L)
        }

        return result ?: Result.failure(TorManagerException("Failed to process $Start"))
    }

    override fun restartQuietly() {
        if (isDestroyed) return

        scope.launch {
            restart().onFailure { ex ->
                notifyListenersNoScope(TorManagerEvent.Log.Error(ex))
            }
        }
    }

    override suspend fun restart(): Result<Any?> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<Any?>? = null

        scope.launch(context =
            Restart.catchInterrupt { result = Result.failure(it) }                   +
            CoroutineName(name = "${Restart}_${coroutineCounter.getAndIncrement()}")
        ) {
            result = actions.withProcessorLock(Restart) {

                if (isDestroyed) {
                    return@withProcessorLock Result.failure(
                        TorManagerException("TorManager instance has been destroyed")
                    )
                }

                controller.value?.first?.let { controller ->
                    realStop(controller, isRestart = true)

                    if ((actions as ActionQueue).contains(Stop)) {
                        // ensure the loader is closed as realStop did not
                        // close it b/c we signaled restart = true
                        loader.close()

                        return@let Result.failure(
                            InterruptedException(
                                "$Stop found in queue. $Restart stopping early."
                            )
                        )
                    }

                    realStart(isRestart = true)
                } ?: run {
                    loader.close()
                    Result.success("No controller connection present")
                }
            }
        }.join()

        if (result == null) {
            // wait for exception to propagate to handler
            delay(25L)
        }

        return result ?: Result.failure(TorManagerException("Failed to process $Restart"))
    }

    override fun stopQuietly() {
        if (isDestroyed) return

        scope.launch {
            stop().onFailure { ex ->
                notifyListenersNoScope(TorManagerEvent.Log.Error(ex))
            }
        }
    }

    override suspend fun stop(): Result<Any?> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<Any?>? = null

        scope.launch(context =
            Stop.catchInterrupt { result = Result.failure(it) }                      +
            CoroutineName(name = "${Stop}_${coroutineCounter.getAndIncrement()}")
        ) {
            result = actions.withProcessorLock(Stop) {

                if (isDestroyed) {
                    return@withProcessorLock Result.failure(
                        TorManagerException("TorManager instance has been destroyed")
                    )
                }

                controller.value?.first?.let {
                    realStop(it, false)
                } ?: run {
                    loader.close()
                    Result.success("No controller connection present")
                }
            }
        }.join()

        if (result == null) {
            // wait for exception to propagate to handler
            delay(25L)
        }

        return result ?: Result.failure(TorManagerException("Failed to process $Stop"))
    }

    override fun debug(enable: Boolean) {
        debug.value = enable
    }

    override suspend fun configLoad(config: TorConfig): Result<Any?> {
        return provide<TorControlConfigLoad, Any?> {
            // TODO: Check settings
            val result = configLoad(config)
            result
        }
    }

    override suspend fun configReset(
        setting: TorConfig.Setting<*>,
        setDefault: Boolean
    ): Result<Any?> {
        return provide<TorControlConfigReset, Any?> {
            // TODO: Check settings
            val result = configReset(setting, setDefault)
            result
        }
    }

    override suspend fun configReset(
        settings: Set<TorConfig.Setting<*>>,
        setDefault: Boolean
    ): Result<Any?> {
        return provide<TorControlConfigReset, Any?> {
            // TODO: Check settings
            val result = configReset(settings, setDefault)
            result
        }
    }

    // TODO: Maybe???
    override suspend fun configSave(force: Boolean): Result<Any?> {
        return provide<TorControlConfigSave, Any?> {
            configSave(force)
        }
    }

    override suspend fun configSet(setting: TorConfig.Setting<*>): Result<Any?> {
        return provide<TorControlConfigSet, Any?> {
            // TODO: Check settings
            val result = configSet(setting)
            result
        }
    }

    override suspend fun configSet(settings: Set<TorConfig.Setting<*>>): Result<Any?> {
        return provide<TorControlConfigSet, Any?> {
            // TODO: Check settings
            val result = configSet(settings)
            result
        }
    }

    override suspend fun signal(signal: TorControlSignal.Signal): Result<Any?> {
        return when (signal) {
            TorControlSignal.Signal.NewNym -> {
                provide<TorControlSignal, Any?> {
                    val newWaiter = Waiter(noticeStartsWith = NEW_NYM_RATE_LIMITED)
                    controllerListener.waiter.value = newWaiter

                    val result = signal(signal)
                    if (result.isSuccess) {
                        val rateLimited = newWaiter.readResponse()
                        controllerListener.waiter.value = null
                        if (rateLimited == null) {
                            Result.success(NEW_NYM_SUCCESS)
                        } else {
                            Result.success(rateLimited)
                        }
                    } else {
                        controllerListener.waiter.value = null
                        result
                    }
                }
            }

            TorControlSignal.Signal.Shutdown,
            TorControlSignal.Signal.Halt -> {
                provide<TorControlSignal, Any?> {
                    realStop(this as TorController, isRestart = false)
                }
            }

            TorControlSignal.Signal.Reload,
            TorControlSignal.Signal.Dump,
            TorControlSignal.Signal.Debug,
            TorControlSignal.Signal.ClearDnsCache,
            TorControlSignal.Signal.Heartbeat,
            TorControlSignal.Signal.SetActive,
            TorControlSignal.Signal.SetDormant -> {
                provide<TorControlSignal, Any?> {
                    signal(signal)
                }
            }
        }
    }

    override suspend fun setEvents(events: Set<TorEvent>, extended: Boolean): Result<Any?> {
        return provide<TorControlSetEvents, Any?> {
            setEvents(events.toMutableSet().apply { addAll(requiredEvents) }, extended)
        }
    }

    @Suppress("unchecked_cast")
    override suspend fun <T, V> provide(
        block: suspend T.() -> Result<V>
    ): Result<V> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<V>? = null

        val outerScopeContext = coroutineContext

        scope.launch(context =
            Controller.catchInterrupt { result = Result.failure(it) }                   +
            CoroutineName(name = "${Controller}_${coroutineCounter.getAndIncrement()}")
        ) {
            result = actions.withProcessorLock(Controller) {

                if (isDestroyed) {
                    return@withProcessorLock Result.failure(
                        TorManagerException("TorManager instance has been destroyed")
                    )
                }

                if (!outerScopeContext.isActive) {
                    return@withProcessorLock Result.failure(
                        InterruptedException("Outer scope is no longer active. Interrupting...")
                    )
                }

                controller.value?.first?.let {
                    notifyListenersNoScope(Controller)
                    block.invoke(it as T)
                } ?: Result.failure(TorNotStartedException("Tor is not started"))
            }
        }.join()

        if (result == null) {
            // wait for exception to propagate to handler
            delay(25L)
        }

        return result ?: Result.failure(TorManagerException("Failed to process $Controller"))
    }

    override fun addListener(listener: TorManagerEvent.SealedListener): Boolean {
        if (isDestroyed) return false
        return listeners.addListener(listener)
    }

    override fun removeListener(listener: TorManagerEvent.SealedListener): Boolean {
        if (isDestroyed) return false
        return listeners.removeListener(listener)
    }

    private fun dispatchNewAddressInfo(addressInfo: TorManagerEvent.AddressInfo) {
        if (state.isBootstrapped && networkState.isEnabled()) {
            addressInfoJob.update { job ->
                job?.cancel()
                scope.launch {
                    delay(100L)
                    notifyListenersNoScope(addressInfo)
                }
            }
        }
    }

    private class Waiter(val noticeStartsWith: String) {

        private val eventResponse: AtomicRef<String?> = atomic(null)

        suspend fun readResponse(): String? {
            var timeout = 0L
            while (timeout < 100L) {
                eventResponse.value?.let { return it }
                if (!currentCoroutineContext().isActive) {
                    return null
                }
                timeout += 25L
            }
            return null
        }

        fun setResponse(response: String) {
            eventResponse.value = response
        }
    }

    private inner class ControllerListener: TorEvent.Listener() {

        val waiter: AtomicRef<Waiter?> = atomic(null)

        override fun eventConfChanged(output: String) {
            if (output.startsWith(disableNetwork.keyword)) {
                when (output.substringAfter('=')) {
                    TorConfig.Option.TorF.True.value -> {
                        scope.launch {
                            stateMachine.updateState(TorNetworkState.Disabled)
                        }
                    }
                    TorConfig.Option.TorF.False.value -> {
                        scope.launch {
                            stateMachine.updateState(TorNetworkState.Enabled)
                        }
                    }
                }
            }
        }

        override fun eventLogNotice(output: String) {
            waiter.value?.let { waiter ->
                if (output.startsWith(waiter.noticeStartsWith)) {
                    waiter.setResponse(output)
                }
            }

            when {
                output.startsWith("Bootstrapped ") -> {
                    val percent = output.eventNoticeBootstrapProgressOrNull()

                    if (percent != null) {
                        scope.launch {
                            stateMachine.updateState(TorState.On(percent))
                        }
                    }
                }
                // Closing no-longer-configured DNS listener on 127.0.0.1:53085
                // Closing no-longer-configured HTTP tunnel listener on 127.0.0.1:48932
                // Closing no-longer-configured Socks listener on 127.0.0.1:9150
                // Closing no-longer-configured Transparent pf/netfilter listener on 127.0.0.1:45963
                output.startsWith("Closing no-longer-configured ") &&
                        output.contains(" listener on ")                                -> {

                    val splits = output.split(' ')
                    val address = splits.lastOrNull()?.trim()

                    if (address != null) {
                        val info = _addressInfo.value
                        when (splits.elementAtOrNull(2)?.lowercase()) {
                            "dns" -> {
                                info.dnsClosed(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                            "http" -> {
                                info.httpClosed(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                            "socks" -> {
                                info.socksClosed(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                            "transparent" -> {
                                info.transClosed(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                        }
                    }
                }
                // Opened DNS listener connection (ready) on 127.0.0.1:58391
                // Opened HTTP tunnel listener connection (ready) on 127.0.0.1:48601
                // Opened Socks listener connection (ready) on 127.0.0.1:9050
                // Opened Transparent pf/netfilter listener connection (ready) on 127.0.0.1:48494
                output.startsWith("Opened ") &&
                        output.contains(" listener connection ")                        -> {

                    val splits = output.split(' ')
                    val address = splits.lastOrNull()?.trim()

                    if (address != null) {
                        val info = _addressInfo.value
                        when (splits.elementAtOrNull(1)?.lowercase()) {
                            "dns" -> {
                                info.dnsOpened(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                            "http" -> {
                                info.httpOpened(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                            "socks" -> {
                                info.socksOpened(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                            "transparent" -> {
                                info.transOpened(address)?.let { newInfo ->
                                    _addressInfo.value = newInfo
                                    dispatchNewAddressInfo(newInfo)
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun onEvent(event: TorEvent.Type.SingleLineEvent, output: String) {
            super.onEvent(event, output)

            scope.launch {
                listeners.notify(event, output)
            }
        }

        override fun onEvent(event: TorEvent.Type.MultiLineEvent, output: List<String>) {
            scope.launch {
                listeners.notify(event, output)
            }
        }
    }

    private fun notifyListeners(event: TorManagerEvent) {
        if (event is TorManagerEvent.Log.Debug && !debug.value) return

        scope.launch {
            notifyListenersNoScope(event)
        }
    }

    private fun notifyListenersNoScope(event: TorManagerEvent) {
        listeners.withLock {
            for (listener in this) {
                try {
                    (listener as TorManagerEvent.SealedListener).onEvent(event)
                } catch (e: Exception) {
                    if (event !is TorManagerEvent.Log.Error) {
                        try {
                            (listener as TorManagerEvent.SealedListener).onEvent(TorManagerEvent.Log.Error(e))
                        } catch (ee: Exception) {}
                    }
                }
            }
        }
    }

    private suspend fun realStart(isRestart: Boolean = false): Result<Any?> {
        if (controller.value?.first?.isConnected == true && state is TorState.On) {
            return Result.success("Tor is already started")
        }

        loader
        stateMachine.updateState(TorState.Starting, TorNetworkState.Disabled)
        if (!isRestart) {
            notifyListenersNoScope(Start)
        }

        val result = loader.load(instanceId, scope, stateMachine) { event ->
            if (event is TorManagerEvent.Log.Debug && !debug.value) return@load
            notifyListenersNoScope(event)
        }

        if (result.isFailure) {
            stateMachine.updateState(TorState.Off, TorNetworkState.Disabled)
            return result
        }

        val controller = result.getOrThrow()
        notifyListenersNoScope(TorManagerEvent.Lifecycle(controller, ON_CREATE))

        controller.addListener(controllerListener)

        (controller as Debuggable).setDebugger { item ->
            when (item) {
                is DebugItem.Message -> {
                    if (item.value.startsWith("<< 650 BW ")) return@setDebugger
                    notifyListeners(TorManagerEvent.Log.Debug(item.value))
                }
                is DebugItem.Error -> {
                    notifyListeners(TorManagerEvent.Log.Error(item.value))
                }
                is DebugItem.ListenerError -> {
                    // ControllerListener threw an exception
                    item.error.value.printStackTrace()
                }
            }
        }

        // set shutdown callback
        @OptIn(ExperimentalTorApi::class)
        controller.onDisconnect { disconnectedController ->

            notifyListeners(TorManagerEvent.Lifecycle(disconnectedController, ON_DESTROY))

            (disconnectedController as Debuggable).setDebugger(null)
            disconnectedController.removeListener(controllerListener)

            this.controller.value?.let { currentController ->

                // check if it's our current controller or not
                if (currentController == disconnectedController) {
                    loader.close()
                    this.controller.value = null
                }

            }

        }

        // TODO: Handle Failure case
        controller.ownershipTake().onSuccess {
            // Stop Tor from polling for processId, as we've passed ownership
            // to the controller which, if it is stopped, Tor will exit.
            controller.configReset(TorConfig.Setting.OwningControllerProcess())
        }
        controller.setEvents(requiredEvents)

        if (networkObserver?.isNetworkConnected() != false) {
            // null (no observer) or true
            controller.configSet(disableNetwork.set(TorConfig.Option.TorF.False)).onSuccess {
                stateMachine.updateState(TorNetworkState.Enabled)
            }
        } else {
            notifyListenersNoScope(TorManagerEvent.Log.Warn(WAITING_ON_NETWORK))
        }

        this.controller.value = Pair(controller, null)
        stateMachine.updateState(TorState.On(state.bootstrap))

        return Result.success("Tor started successfully")
    }

    private suspend fun realStop(
        controller: TorController,
        isRestart: Boolean
    ): Result<Any?> {
        this.controller.value = null

        stateMachine.updateState(TorState.Stopping)
        notifyListenersNoScope(if (isRestart) Restart else Stop)

        if (networkState.isEnabled()) {
            controller.configSet(disableNetwork.set(TorConfig.Option.TorF.True))
        }
        val result1 = controller.signal(TorControlSignal.Signal.Shutdown)

        when {
            result1.isSuccess -> {
                result1
            }
            result1.exceptionOrNull() is ControllerShutdownException -> {
                Result.success("Controller was already shutdown")
            }
            !controller.isConnected -> {
                Result.success("Controller was already shutdown")
            }
            else -> {
                val result2 = controller.signal(TorControlSignal.Signal.Halt)

                if (result2.isFailure) {
                    if (result2.exceptionOrNull() is ControllerShutdownException) {
                        Result.success("Controller was already shutdown")
                    } else {
                        // Force close Tor
                        controller.disconnect()
                        loader.close()
                        notifyListenersNoScope(TorManagerEvent.Log.Warn(
                            "Tor failed to signal shutdown/halt and was forcibly stopped"
                        ))
                        Result.success("Tor was forcibly stopped")
                    }
                } else {
                    result2
                }
            }
        }.let { result ->
            if (result.isSuccess && !isRestart) {
                loader.close()
            }

            if (isRestart) {
                loader.cancelTorJob()
            }

            return result
        }
    }

}
