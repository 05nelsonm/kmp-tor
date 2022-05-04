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
package io.matthewnelson.kmp.tor.ext.callback.manager

import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.config.ClientAuthEntry
import io.matthewnelson.kmp.tor.controller.common.config.ConfigEntry
import io.matthewnelson.kmp.tor.controller.common.config.HiddenServiceEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlOnionAdd
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.ext.callback.controller.common.*
import io.matthewnelson.kmp.tor.ext.callback.manager.common.CallbackTorControlManager
import io.matthewnelson.kmp.tor.ext.callback.manager.common.CallbackTorOperationManager
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import io.matthewnelson.kmp.tor.manager.common.state.TorStateManager
import kotlinx.coroutines.*
import kotlin.jvm.JvmField

/**
 * Wrapper for [TorManager] such that callbacks
 * can be used in lieu of suspension functions.
 * */
class CallbackTorManager(
    private val delegate: TorManager,
    private val handler: UncaughtExceptionHandler,
) : CallbackDestroyable,
    CallbackTorControlManager,
    CallbackTorOperationManager,
    TorStateManager,
    TorEventProcessor<TorManagerEvent.SealedListener>
{

    val instanceId: String
        get() = delegate.instanceId

    fun debug(enable: Boolean) {
        delegate.debug(enable)
    }

    private val supervisor = SupervisorJob()
    private val scope: CoroutineScope

    init {
        val dispatcher = try {
            Dispatchers.Main.immediate
        } catch (_: UnsupportedOperationException) {
            Dispatchers.Main
        }

        scope = CoroutineScope(context =
            dispatcher                                          +
            supervisor                                          +
            CoroutineName(name = "CallbackTorManager")          +
            CoroutineExceptionHandler { _, t ->
                // Pass all exceptions that are thrown from RequestCallback
                // to the handler.
                handler.onUncaughtException(t)
            }
        )
    }

    override val isDestroyed: Boolean
        get() = delegate.isDestroyed

    override fun destroy(stopCleanly: Boolean, callback: DestructionCallback?) {
        delegate.destroy(stopCleanly) {
            supervisor.cancel()
            callback?.onDestroyed()
        }
    }

    override val state: TorState
        get() = delegate.state
    override val networkState: TorNetworkState
        get() = delegate.networkState
    override val addressInfo: TorManagerEvent.AddressInfo
        get() = delegate.addressInfo

    override fun addListener(listener: TorManagerEvent.SealedListener): Boolean {
        return delegate.addListener(listener)
    }

    override fun removeListener(listener: TorManagerEvent.SealedListener): Boolean {
        return delegate.removeListener(listener)
    }

    override fun start(callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.start().toCallback(callback)
        }.toTask()
    }

    override fun startQuietly() {
        delegate.startQuietly()
    }

    override fun restart(callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.restart().toCallback(callback)
        }.toTask()
    }

    override fun restartQuietly() {
        delegate.restartQuietly()
    }

    override fun stop(callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.stop().toCallback(callback)
        }.toTask()
    }

    override fun stopQuietly() {
        delegate.stopQuietly()
    }

    override fun configGet(
        setting: TorConfig.Setting<*>,
        callback: RequestCallback<ConfigEntry>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configGet(setting).toCallback(callback)
        }.toTask()
    }

    override fun configGet(
        settings: Set<TorConfig.Setting<*>>,
        callback: RequestCallback<List<ConfigEntry>>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configGet(settings).toCallback(callback)
        }.toTask()
    }

    override fun configLoad(config: TorConfig, callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configLoad(config).toCallback(callback)
        }.toTask()
    }

    override fun configReset(
        setting: TorConfig.Setting<*>,
        setDefault: Boolean,
        callback: RequestCallback<Any?>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configReset(setting, setDefault).toCallback(callback)
        }.toTask()
    }

    override fun configReset(
        settings: Set<TorConfig.Setting<*>>,
        setDefault: Boolean,
        callback: RequestCallback<Any?>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configReset(settings, setDefault).toCallback(callback)
        }.toTask()
    }

    override fun configSave(force: Boolean, callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configSave(force).toCallback(callback)
        }.toTask()
    }

    override fun configSet(setting: TorConfig.Setting<*>, callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configSet(setting).toCallback(callback)
        }.toTask()
    }

    override fun configSet(
        settings: Set<TorConfig.Setting<*>>,
        callback: RequestCallback<Any?>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.configSet(settings).toCallback(callback)
        }.toTask()
    }

    override fun dropGuards(callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.dropGuards().toCallback(callback)
        }.toTask()
    }

    override fun infoGet(
        keyword: TorControlInfoGet.KeyWord,
        callback: RequestCallback<String>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.infoGet(keyword).toCallback(callback)
        }.toTask()
    }

    override fun infoGet(
        keywords: Set<TorControlInfoGet.KeyWord>,
        callback: RequestCallback<Map<String, String>>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.infoGet(keywords).toCallback(callback)
        }.toTask()
    }

    override fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
        callback: RequestCallback<HiddenServiceEntry>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.onionAdd(privateKey, hsPorts, flags, maxStreams).toCallback(callback)
        }.toTask()
    }

    override fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
        callback: RequestCallback<HiddenServiceEntry>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.onionAddNew(type, hsPorts, flags, maxStreams).toCallback(callback)
        }.toTask()
    }

    override fun onionDel(address: OnionAddress, callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.onionDel(address).toCallback(callback)
        }.toTask()
    }

    override fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: OnionClientAuth.PrivateKey,
        clientName: ClientName?,
        flags: Set<TorControlOnionClientAuth.Flag>?,
        callback: RequestCallback<Any?>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthAdd(address, key, clientName, flags).toCallback(callback)
        }.toTask()
    }

    override fun onionClientAuthRemove(
        address: OnionAddressV3,
        callback: RequestCallback<Any?>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthRemove(address).toCallback(callback)
        }.toTask()
    }

    override fun onionClientAuthView(callback: RequestCallback<List<ClientAuthEntry>>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthView().toCallback(callback)
        }.toTask()
    }

    override fun onionClientAuthView(
        address: OnionAddressV3,
        callback: RequestCallback<ClientAuthEntry>
    ): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthView(address).toCallback(callback)
        }.toTask()
    }

    override fun setEvents(events: Set<TorEvent>, callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.setEvents(events).toCallback(callback)
        }.toTask()
    }

    override fun signal(signal: TorControlSignal.Signal, callback: RequestCallback<Any?>): Task {
        callback.shouldFailImmediately(isDestroyed) { provideFailureException() }?.let { return it }

        return scope.launch {
            delegate.signal(signal).toCallback(callback)
        }.toTask()
    }

    private fun provideFailureException(): TorManagerException {
        return TorManagerException("TorManager instance has been destroyed")
    }

    private fun Job.toTask(): Task {
        return object : Task {
            override val isActive: Boolean
                get() = this@toTask.isActive
            override val isCompleted: Boolean
                get() = this@toTask.isCompleted
            override val isCancelled: Boolean
                get() = this@toTask.isCancelled

            override fun cancel() {
                this@toTask.cancel()
            }
        }
    }
}
