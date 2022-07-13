/*
 * Copyright (c) 2022 Matthew Nelson
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
package io.matthewnelson.kmp.tor.manager.internal

import android.app.Application
import android.content.Intent
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal
import io.matthewnelson.kmp.tor.controller.common.exceptions.TimeoutException
import io.matthewnelson.kmp.tor.manager.KmpTorLoader
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.TorServiceConfig
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorNotStartedException
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import io.matthewnelson.kmp.tor.manager.internal.actions.ActionProcessor
import io.matthewnelson.kmp.tor.manager.internal.ext.catchInterrupt
import kotlinx.coroutines.*

/**
 * Android variant of [TorManager] which wraps the [RealTorManager]
 * instance in a started + bound [android.app.Service].
 *
 * To configure [TorService] to run in the Foreground, see [TorServiceConfig].
 *
 * @see [TorService]
 * @see [TorServiceConfig]
 * @see [RealTorManager]
 * */
internal class RealTorManagerAndroid(
    private val application: Application,
    private val loader: KmpTorLoader,
) : BaseTorManager(),
    TorManager
{
    override val instanceId: String get() = TorServiceController.DEFAULT_INSTANCE_ID

    override val state: TorState
        get() = when (val state = TorServiceController.binderState) {
            null -> TorState.Off
            is BinderState.Bound -> state.binder().state
            is BinderState.Starting -> TorState.Starting
        }

    override val networkState: TorNetworkState
        get() = when (val state = TorServiceController.binderState) {
            null,
            is BinderState.Starting -> TorNetworkState.Disabled
            is BinderState.Bound -> state.binder().networkState
        }

    override val addressInfo: TorManagerEvent.AddressInfo
        get() = when (val state = TorServiceController.binderState) {
            null,
            is BinderState.Starting -> TorManagerEvent.AddressInfo.NULL_VALUES
            is BinderState.Bound -> state.binder().addressInfo
        }

    init {
        TorService.init(application, Pair(hashCode()) { startQuietly() })
    }

    @Volatile
    private var _isDestroyed: Boolean = false
    override val isDestroyed: Boolean get() = _isDestroyed

    private val supervisor = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(context =
    supervisor                                          +
            Dispatchers.Main.immediate                          +
            CoroutineName("RealTorManagerAndroid")
    )

    private val actions: ActionProcessor by lazy {
        TorServiceController.notify(TorManagerEvent.Lifecycle(this, TorManagerEvent.Lifecycle.ON_CREATE))
        ActionProcessor()
    }

    override fun destroy(stopCleanly: Boolean, onCompletion: (() -> Unit)?) {
        synchronized(this) {
            if (isDestroyed) return@synchronized
            _isDestroyed = true

            val lce = TorManagerEvent.Lifecycle(this, TorManagerEvent.Lifecycle.ON_DESTROY)

            when (val state = TorServiceController.binderState) {
                is BinderState.Bound -> {
                    state.binder().stop()
                    TorServiceController.clearLocalListeners(lce)
                    supervisor.cancel()
                    onCompletion?.invoke()
                }
                is BinderState.Starting -> {
                    if (!stopCleanly) {
                        immediateShutdown()
                        TorServiceController.clearLocalListeners(lce)
                        supervisor.cancel()
                        onCompletion?.invoke()
                    } else {
                        scope.launch(context =
                        TorManagerEvent.Action.Stop.catchInterrupt {}                                  +
                                CoroutineName(name = "RealTorManagerAndroid.destroy")
                        ) {
                            actions.withProcessorLock(TorManagerEvent.Action.Stop) {
                                realStop(checkDestroy = false)
                            }
                        }.invokeOnCompletion {
                            TorServiceController.clearLocalListeners(lce)
                            supervisor.cancel()
                            onCompletion?.invoke()
                        }
                    }
                }
                null -> {
                    TorServiceController.clearLocalListeners(lce)
                    supervisor.cancel()
                    onCompletion?.invoke()
                }
            }

            TorService.destroy(application, hashCode())
        }
    }

    override fun startQuietly() {
        if (isDestroyed) return

        scope.launch {
            start().onFailure { ex ->
                TorServiceController.notify(TorManagerEvent.Log.Error(ex))
            }
        }
    }

    override suspend fun start(): Result<Any?> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<Any?>? = null

        scope.launch(context =
        TorManagerEvent.Action.Start.catchInterrupt { result = Result.failure(it) }     +
                CoroutineName(name = "RealTorServiceAndroid.start")
        ) {
            result = actions.withProcessorLock(TorManagerEvent.Action.Start) {
                if (isDestroyed) {
                    return@withProcessorLock Result.failure(
                        TorManagerException("TorManager instance has been destroyed")
                    )
                }

                val localResult: Result<Any?>

                var timeout = 0L
                while (true) {
                    when (val binder = TorServiceController.binderState) {
                        null -> {
                            try {
                                TorServiceController.startService(application)
                            } catch (e: RuntimeException) {
                                localResult = Result.failure(
                                    TorManagerException("Failed to start TorService", e)
                                )
                                break
                            }
                        }
                        is BinderState.Bound -> {
                            localResult = binder.binder().start(loader)
                            break
                        }
                        is BinderState.Starting -> {
                            delay(DEFAULT_DELAY)
                            timeout += DEFAULT_DELAY
                        }
                    }

                    if (timeout >= DEFAULT_TIMEOUT) {
                        localResult = Result.failure(
                            TimeoutException("TorService startup timed out")
                        )
                        immediateShutdown()
                        break
                    }
                }

                localResult
            }
        }.join()

        if (result == null) {
            delay(25L)
        }

        return result ?: Result.failure(TorManagerException("Failed to start TorService"))
    }

    override fun restartQuietly() {
        if (isDestroyed) return

        scope.launch {
            restart().onFailure { ex ->
                TorServiceController.notify(TorManagerEvent.Log.Error(ex))
            }
        }
    }

    override suspend fun restart(): Result<Any?> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<Any?>? = null

        var timeout = 0L
        while (currentCoroutineContext().isActive) {
            when (val state = TorServiceController.binderState) {
                null -> {
                    result = Result.failure(TorNotStartedException("Tor is not started"))
                    break
                }
                is BinderState.Bound -> {
                    result = state.binder().restart(loader)
                    break
                }
                is BinderState.Starting -> {
                    delay(DEFAULT_DELAY)
                    timeout += DEFAULT_DELAY
                }
            }

            if (timeout >= DEFAULT_TIMEOUT) {
                result = Result.failure(TimeoutException("Request to restart Tor timed out"))
                break
            }
        }

        return result ?: Result.failure(TorManagerException("Failed to restart Tor"))
    }

    override fun stopQuietly() {
        if (isDestroyed) return

        scope.launch {
            stop().onFailure { ex ->
                TorServiceController.notify(TorManagerEvent.Log.Error(ex))
            }
        }
    }

    override suspend fun stop(): Result<Any?> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<Any?>? = null

        scope.launch(context =
        TorManagerEvent.Action.Stop.catchInterrupt { result = Result.failure(it) }      +
                CoroutineName(name = "RealTorServiceAndroid.stop")
        ) {
            result = actions.withProcessorLock(TorManagerEvent.Action.Stop) {
                realStop(checkDestroy = true)
            }
        }.join()

        if (result == null) {
            delay(25L)
        }

        return result ?: Result.failure(TorManagerException("Failed to stop TorService"))
    }

    private suspend fun realStop(checkDestroy: Boolean): Result<Any?> {
        if (checkDestroy && isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        val result: Result<Any?>

        var timeout = 0L
        while (true) {
            when (val state = TorServiceController.binderState) {
                null -> {
                    result = Result.success("TorService is not started")
                    break
                }
                is BinderState.Bound -> {
                    result = state.binder().stop()
                    break
                }
                is BinderState.Starting -> {
                    delay(DEFAULT_DELAY)
                    timeout += DEFAULT_DELAY
                }
            }

            if (timeout >= DEFAULT_TIMEOUT) {
                immediateShutdown()
                result = Result.success("TorService shutdown")
                break
            }
        }

        return result
    }

    private fun immediateShutdown() {
        try {
            TorServiceController.unbindService(application)
        } catch (_: Exception) {}
        try {
            val intent = Intent(application, TorService::class.java)
            application.stopService(intent)
        } catch (_: Exception) {}
    }

    override fun debug(enable: Boolean) {
        if (isDestroyed) return
        TorServiceController.debug(enable)
    }

    override fun addListener(listener: TorManagerEvent.SealedListener): Boolean {
        if (isDestroyed) return false
        return TorServiceController.addListener(listener)
    }

    override fun removeListener(listener: TorManagerEvent.SealedListener): Boolean {
        if (isDestroyed) return false
        return TorServiceController.removeListener(listener)
    }

    override suspend fun signal(signal: TorControlSignal.Signal): Result<Any?> {
        return provide<TorControlSignal, Any?> {
            when (signal) {
                TorControlSignal.Signal.Shutdown,
                TorControlSignal.Signal.Halt -> {
                    val result = signal(signal)
                    if (result.isSuccess) {
                        stopQuietly()
                    }
                    result
                }

                TorControlSignal.Signal.Reload,
                TorControlSignal.Signal.Dump,
                TorControlSignal.Signal.Debug,
                TorControlSignal.Signal.NewNym,
                TorControlSignal.Signal.ClearDnsCache,
                TorControlSignal.Signal.Heartbeat,
                TorControlSignal.Signal.SetActive,
                TorControlSignal.Signal.SetDormant -> {
                    signal(signal)
                }
            }
        }
    }

    @Suppress("unchecked_cast")
    override suspend fun <T, V> provide(block: suspend T.() -> Result<V>): Result<V> {
        if (isDestroyed) {
            return Result.failure(TorManagerException("TorManager instance has been destroyed"))
        }

        var result: Result<V>? = null

        var timeout: Long = 0
        while (currentCoroutineContext().isActive) {

            when (val state = TorServiceController.binderState) {
                null -> {
                    result = Result.failure(TorNotStartedException("TorService is not started"))
                    break
                }
                is BinderState.Bound -> {
                    result = block.invoke(state.binder().manager(loader) as T)
                    break
                }
                is BinderState.Starting -> {
                    delay(DEFAULT_DELAY)
                    timeout += DEFAULT_DELAY
                }
            }

            if (timeout >= DEFAULT_TIMEOUT) {
                result = Result.failure(TimeoutException("TorService.Binder retrieval timed out"))
                break
            }
        }

        return result ?: Result.failure(TorManagerException("Failed to process request"))
    }

    companion object {
        const val DEFAULT_TIMEOUT = 250L
        const val DEFAULT_DELAY = 50L
    }
}
