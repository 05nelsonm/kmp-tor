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
package io.matthewnelson.kmp.tor.ext.callback.controller

import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth
import io.matthewnelson.kmp.tor.controller.TorController
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
import io.matthewnelson.kmp.tor.controller.common.exceptions.ControllerShutdownException
import io.matthewnelson.kmp.tor.ext.callback.controller.common.*
import kotlinx.coroutines.*

/**
 * Wrapper for [TorController] such that callbacks
 * can be used in lieu of suspension functions.
 * */
@OptIn(InternalTorApi::class)
class CallbackTorController(
    private val delegate: TorController,
    private val uncaughtExceptionHandler: TorCallback<Throwable>,
) : CallbackTorControlProcessor,
    TorEventProcessor<TorEvent.SealedListener>
{

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
            CoroutineName(name = "CallbackTorController")       +
            CoroutineExceptionHandler { _, t ->
                // Pass all exceptions that are thrown from RequestCallback
                // to the handler.
                uncaughtExceptionHandler.invoke(t)
            }
        )

        // set our initial on disconnect callback
        @OptIn(ExperimentalTorApi::class)
        delegate.onDisconnect {
            supervisor.cancel()
        }
    }

    val isConnected: Boolean get() = delegate.isConnected
    fun disconnect() {
        delegate.disconnect()
    }

    @ExperimentalTorApi
    fun onDisconnect(callback: TorCallback<CallbackTorController>?) {
        delegate.onDisconnect {
            supervisor.cancel()
            callback?.invoke(this)
        }
    }

    override fun addListener(listener: TorEvent.SealedListener): Boolean {
        return delegate.addListener(listener)
    }

    override fun removeListener(listener: TorEvent.SealedListener): Boolean {
        return delegate.removeListener(listener)
    }

    override fun authenticate(
        bytes: ByteArray,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.authenticate(bytes).toCallback(failure, success)
        }.toTask()
    }

    override fun configGet(
        setting: TorConfig.Setting<*>,
        failure: TorCallback<Throwable>,
        success: TorCallback<ConfigEntry>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configGet(setting).toCallback(failure, success)
        }.toTask()
    }

    override fun configGet(
        settings: Set<TorConfig.Setting<*>>,
        failure: TorCallback<Throwable>,
        success: TorCallback<List<ConfigEntry>>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configGet(settings).toCallback(failure, success)
        }.toTask()
    }

    override fun configLoad(
        config: TorConfig,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configLoad(config).toCallback(failure, success)
        }.toTask()
    }

    override fun configReset(
        setting: TorConfig.Setting<*>,
        setDefault: Boolean,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configReset(setting, setDefault).toCallback(failure, success)
        }.toTask()
    }

    override fun configReset(
        settings: Set<TorConfig.Setting<*>>,
        setDefault: Boolean,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configReset(settings, setDefault).toCallback(failure, success)
        }.toTask()
    }

    override fun configSave(
        force: Boolean,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configSave(force).toCallback(failure, success)
        }.toTask()
    }

    override fun configSet(
        setting: TorConfig.Setting<*>,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configSet(setting).toCallback(failure, success)
        }.toTask()
    }

    override fun configSet(
        settings: Set<TorConfig.Setting<*>>,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.configSet(settings).toCallback(failure, success)
        }.toTask()
    }

    override fun dropGuards(failure: TorCallback<Throwable>, success: TorCallback<Any?>): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.dropGuards().toCallback(failure, success)
        }.toTask()
    }

    override fun infoGet(
        keyword: TorControlInfoGet.KeyWord,
        failure: TorCallback<Throwable>,
        success: TorCallback<String>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.infoGet(keyword).toCallback(failure, success)
        }.toTask()
    }

    override fun infoGet(
        keywords: Set<TorControlInfoGet.KeyWord>,
        failure: TorCallback<Throwable>,
        success: TorCallback<Map<String, String>>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.infoGet(keywords).toCallback(failure, success)
        }.toTask()
    }

    override fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
        failure: TorCallback<Throwable>,
        success: TorCallback<HiddenServiceEntry>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.onionAdd(privateKey, hsPorts, flags, maxStreams).toCallback(failure, success)
        }.toTask()
    }

    override fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
        failure: TorCallback<Throwable>,
        success: TorCallback<HiddenServiceEntry>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.onionAddNew(type, hsPorts, flags, maxStreams).toCallback(failure, success)
        }.toTask()
    }

    override fun onionDel(
        address: OnionAddress,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.onionDel(address).toCallback(failure, success)
        }.toTask()
    }

    override fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: OnionClientAuth.PrivateKey,
        clientName: ClientName?,
        flags: Set<TorControlOnionClientAuth.Flag>?,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthAdd(address, key, clientName, flags).toCallback(failure, success)
        }.toTask()
    }

    override fun onionClientAuthRemove(
        address: OnionAddressV3,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthRemove(address).toCallback(failure, success)
        }.toTask()
    }

    override fun onionClientAuthView(
        failure: TorCallback<Throwable>,
        success: TorCallback<List<ClientAuthEntry>>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthView().toCallback(failure, success)
        }.toTask()
    }

    override fun onionClientAuthView(
        address: OnionAddressV3,
        failure: TorCallback<Throwable>,
        success: TorCallback<ClientAuthEntry>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.onionClientAuthView(address).toCallback(failure, success)
        }.toTask()
    }

    override fun ownershipDrop(failure: TorCallback<Throwable>, success: TorCallback<Any?>): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.ownershipDrop().toCallback(failure, success)
        }.toTask()
    }

    override fun ownershipTake(failure: TorCallback<Throwable>, success: TorCallback<Any?>): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.ownershipTake().toCallback(failure, success)
        }.toTask()
    }

    override fun setEvents(
        events: Set<TorEvent>,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.setEvents(events).toCallback(failure, success)
        }.toTask()
    }

    override fun signal(
        signal: TorControlSignal.Signal,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>
    ): Task {
        failure.shouldFailImmediately(supervisor.isCancelled) {
            shutdownException()
        }?.let { return it }

        return scope.launch {
            delegate.signal(signal).toCallback(failure, success)
        }.toTask()
    }

    private fun shutdownException(): ControllerShutdownException {
        return ControllerShutdownException("Tor has stopped and a new connection is required")
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
