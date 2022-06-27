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
import io.matthewnelson.kmp.tor.common.server.Server
import io.matthewnelson.kmp.tor.controller.TorControlProcessor
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
import io.matthewnelson.kmp.tor.ext.callback.common.Task
import io.matthewnelson.kmp.tor.ext.callback.common.TorCallback
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
                // Pass all exceptions that are thrown from requests'
                // TorCallbacks to the handler.
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
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            authenticate(bytes)
        }
    }

    override fun configGet(
        keyword: TorConfig.KeyWord,
        failure: TorCallback<Throwable>?,
        success: TorCallback<List<ConfigEntry>>
    ): Task {
        return provideOrFail(failure, success) {
            configGet(keyword)
        }
    }

    override fun configGet(
        keywords: Set<TorConfig.KeyWord>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<List<ConfigEntry>>
    ): Task {
        return provideOrFail(failure, success) {
            configGet(keywords)
        }
    }

    override fun configLoad(
        config: TorConfig,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            configLoad(config)
        }
    }

    override fun configReset(
        keyword: TorConfig.KeyWord,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            configReset(keyword)
        }
    }

    override fun configReset(
        keywords: Set<TorConfig.KeyWord>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            configReset(keywords)
        }
    }

    override fun configSave(
        force: Boolean,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            configSave(force)
        }
    }

    override fun configSet(
        setting: TorConfig.Setting<*>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            configSet(setting)
        }
    }

    override fun configSet(
        settings: Set<TorConfig.Setting<*>>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            configSet(settings)
        }
    }

    override fun dropGuards(
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            dropGuards()
        }
    }

    override fun hsFetch(
        address: OnionAddress,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            hsFetch(address)
        }
    }

    override fun hsFetch(
        address: OnionAddress,
        server: Server.Fingerprint,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            hsFetch(address, server)
        }
    }

    override fun hsFetch(
        address: OnionAddress,
        servers: Set<Server.Fingerprint>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            hsFetch(address, servers)
        }
    }

    override fun infoGet(
        keyword: TorControlInfoGet.KeyWord,
        failure: TorCallback<Throwable>?,
        success: TorCallback<String>
    ): Task {
        return provideOrFail(failure, success) {
            infoGet(keyword)
        }
    }

    override fun infoGet(
        keywords: Set<TorControlInfoGet.KeyWord>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Map<String, String>>
    ): Task {
        return provideOrFail(failure, success) {
            infoGet(keywords)
        }
    }

    override fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.VirtualPort>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
        failure: TorCallback<Throwable>?,
        success: TorCallback<HiddenServiceEntry>
    ): Task {
        return provideOrFail(failure, success) {
            onionAdd(privateKey, hsPorts, flags, maxStreams)
        }
    }

    override fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.VirtualPort>,
        flags: Set<TorControlOnionAdd.Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
        failure: TorCallback<Throwable>?,
        success: TorCallback<HiddenServiceEntry>
    ): Task {
        return provideOrFail(failure, success) {
            onionAddNew(type, hsPorts, flags, maxStreams)
        }
    }

    override fun onionDel(
        address: OnionAddress,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            onionDel(address)
        }
    }

    override fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: OnionClientAuth.PrivateKey,
        clientName: ClientName?,
        flags: Set<TorControlOnionClientAuth.Flag>?,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            onionClientAuthAdd(address, key, clientName, flags)
        }
    }

    override fun onionClientAuthRemove(
        address: OnionAddressV3,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            onionClientAuthRemove(address)
        }
    }

    override fun onionClientAuthView(
        failure: TorCallback<Throwable>?,
        success: TorCallback<List<ClientAuthEntry>>
    ): Task {
        return provideOrFail(failure, success) {
            onionClientAuthView()
        }
    }

    override fun onionClientAuthView(
        address: OnionAddressV3,
        failure: TorCallback<Throwable>?,
        success: TorCallback<ClientAuthEntry>
    ): Task {
        return provideOrFail(failure, success) {
            onionClientAuthView(address)
        }
    }

    override fun ownershipDrop(
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            ownershipDrop()
        }
    }

    override fun ownershipTake(
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            ownershipTake()
        }
    }

    override fun setEvents(
        events: Set<TorEvent>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            setEvents(events)
        }
    }

    override fun signal(
        signal: TorControlSignal.Signal,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Any?>
    ): Task {
        return provideOrFail(failure, success) {
            signal(signal)
        }
    }

    private fun <T: Any?> provideOrFail(
        failure: TorCallback<Throwable>?,
        success: TorCallback<T>,
        block: suspend TorControlProcessor.() -> Result<T>,
    ): Task {
        failure.shouldFailImmediately(!isConnected, { uncaughtExceptionHandler }) {
            ControllerShutdownException("Tor has stopped and a new connection is required")
        }?.let { emptyTask ->
            supervisor.cancel()
            return emptyTask
        }

        return scope.launch {
            block.invoke(delegate).toCallback(failure, success)
        }.toTask()
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
