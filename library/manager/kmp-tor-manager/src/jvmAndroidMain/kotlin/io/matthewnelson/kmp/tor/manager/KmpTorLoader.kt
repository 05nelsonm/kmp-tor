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
package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.TorController
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlInfoGet
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.file.toFile
import io.matthewnelson.kmp.tor.controller.common.internal.ControllerUtils
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.InterruptedException
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import io.matthewnelson.kmp.tor.manager.internal.TorStateMachine
import io.matthewnelson.kmp.tor.manager.internal.ext.infoGetBootstrapProgressOrNull
import io.matthewnelson.kmp.tor.manager.internal.util.SynchronizedMutableMap
import io.matthewnelson.kmp.tor.manager.util.PortUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

actual abstract class KmpTorLoader(protected val provider: TorConfigProvider) {

    actual companion object {
        const val READ_INTERVAL = 250L

        private val instanceRunLock = SynchronizedMutableMap<Mutex>()

        @JvmSynthetic
        internal actual fun removeInstanceRunLock(instanceId: String) {
            instanceRunLock.withLock {
                remove(instanceId)
            }
        }
    }

    /**
     * Calls [TorConfig.Builder.removeInstanceOf] for all present
     * settings. This is to ensure platform specific settings are
     * removed during the [TorConfigProvider.retrieve] process, prior
     * to starting Tor.
     * */
    protected actual open val excludeSettings: Set<TorConfig.Setting<*>> = emptySet()
    private val torDispatcher = DispatcherHandler()
    private var torJob: Job? = null

    private class DispatcherHandler {
        @Volatile
        private var dispatcher: ExecutorCoroutineDispatcher? = null

        fun getOrCreate(): ExecutorCoroutineDispatcher =
            synchronized(this) {
                dispatcher ?: Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                    .also { dispatcher = it }
            }

        fun close() {
            synchronized(this) {
                dispatcher?.close()
                dispatcher = null
            }
        }
    }

    @JvmSynthetic
    @Suppress("BlockingMethodInNonBlockingContext")
    internal actual open suspend fun load(
        instanceId: String,
        managerScope: CoroutineScope,
        stateMachine: TorStateMachine,
        notify: (TorManagerEvent) -> Unit,
    ): Result<Pair<TorController, TorConfig?>> {
        val dispatcher = torDispatcher.getOrCreate()

        provider.lastValidatedTorConfig?.let { validated ->
            val controlPortFile = validated.controlPortFile.toFile()
            val cookieAuthFile = validated.cookieAuthFile?.toFile()

            if (!controlPortFile.exists() || cookieAuthFile?.exists() == false) {
                return@let
            }
            notify.invoke(TorManagerEvent.Log.Debug(value=
                "Attempting to re-connect to already running Tor process"
            ))
            // attempt re-connect to already running Tor instance

            val address: InetSocketAddress = withContext(dispatcher) {
                try {
                    readControlPortFile(controlPortFile, timeout = 500L)
                } catch (_: Exception) {
                    null
                }
            } ?: return@let

            val bytes: ByteArray = if (cookieAuthFile != null) {
                withContext(dispatcher) {
                    try {
                        readCookieAuthFile(cookieAuthFile, timeout = 500L)
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@let
            } else {
                ByteArray(0)
            }

            val socket = Socket(Proxy.NO_PROXY)
            try {
                withContext(dispatcher) {
                    socket.connect(address)
                }
            } catch (_: Exception) {
                return@let
            }

            val controller: TorController = try {
                TorController.newInstance(socket)
            } catch (_: Exception) {
                return@let
            }

            controller.authenticate(bytes).onFailure {
                controller.signal(TorControlSignal.Signal.Shutdown)
                return@let
            }

            controller.ownershipTake().onFailure {
                controller.signal(TorControlSignal.Signal.Shutdown)
                return@let
            }
            val rInfo = controller.infoGet(TorControlInfoGet.KeyWord.Status.BootstrapPhase())
            rInfo.onFailure {
                controller.signal(TorControlSignal.Signal.Shutdown)
                return@let
            }
            rInfo.onSuccess { bootstrap ->
                @OptIn(InternalTorApi::class)
                val percent: Int? = bootstrap.infoGetBootstrapProgressOrNull()

                if (percent != null) {
                    stateMachine.updateState(TorState.On(percent))
                }
            }

            notify.invoke(TorManagerEvent.Log.Debug("Re-connection attempt successful!"))
            return Result.success(Pair(controller, null))
        }

        torJob?.cancel()

        val validated: TorConfigProvider.ValidatedTorConfig = withContext(dispatcher) {
            provider.retrieve(excludeSettings) { port -> PortUtil.isTcpPortAvailable(port) }
        }

        val mkdirsFailure: TorManagerException? = withContext(dispatcher) {

            // directories specified in the config must be present for Tor to start
            for (setting in validated.torConfig.settings) {
                val option = setting.value
                if (option !is TorConfig.Option.FileSystemDir) {
                    continue
                }

                val dirPath = option.nullIfEmpty?.path ?: continue
                val dir = dirPath.toFile()
                if (!dir.exists()) {
                    if (!dir.mkdirs()) {
                        return@withContext TorManagerException("Failed to create directory $dir")
                    }
                } else if (dir.isFile) {
                    if (!dir.delete() && !dir.mkdirs()) {
                        return@withContext TorManagerException("Failed to create directory $dir")
                    }
                }

                if (setting is TorConfig.Setting.HiddenService) {
                    try {
                        setHiddenServiceDirPermissions(dirPath)
                    } catch (e: TorManagerException) {
                        return@withContext e
                    }
                }
            }

            validated.controlPortFile.toFile().delete()
            validated.cookieAuthFile?.toFile()?.delete()

            val torrc = provider.torrcFile.toFile()
            if (!torrc.exists()) {
                torrc.createNewFile()
            }
            val torrcDefaults = provider.torrcDefaultsFile.toFile()
            if (!torrcDefaults.exists()) {
                torrcDefaults.createNewFile()
            }

            null
        }

        if (mkdirsFailure != null) {
            torDispatcher.close()
            return Result.failure(mkdirsFailure)
        }

        var torJobException: Throwable? = null
        val handler = CoroutineExceptionHandler { _, throwable ->
            torJobException = throwable
        }

        val torJob = managerScope.launch(context =
            handler                                         +
            CoroutineName(name = "KmpTorLoader.startTor")
        ) {
            instanceRunLock.withLock {
                // get or create a lock for this instanceId
                get(instanceId) ?: Mutex()
                    .also { mutex -> put(instanceId, mutex) }
            }.withLock {

                notify.invoke(TorManagerEvent.Log.Info(value=
                    "Starting Tor with the following settings:\n" +
                    "----------------------------------------------------------------" +
                    "\n${validated.torConfig.text}" +
                    "----------------------------------------------------------------"
                ))

                withContext(dispatcher) {
                    startTor(validated.configLines) { log ->
                        managerScope.launch {
                            notify.invoke(log)
                        }
                    }
                }

                // throw exception here so it is propagated to the handler in
                // case we're still in the middle of reading control port / cookie
                // auth files they can be interrupted appropriately. If we're past
                // that, this does nothing.
                throw TorManagerException("Tor stopped early. Bad config?")
            }
        }

        this.torJob = torJob
        torJob.invokeOnCompletion {
            stateMachine.updateState(TorState.Off, TorNetworkState.Disabled)
        }

        val controlPortFile = validated.controlPortFile.toFile()
        val cookieAuthFile = validated.cookieAuthFile?.toFile()
        val address: InetSocketAddress = try {
            withContext(dispatcher) {
                readControlPortFile(controlPortFile, timeout = 10_000) { torJobException }
            }
        } catch (e: Exception) {
            torJob.cancelAndJoin()
            torDispatcher.close()
            return Result.failure(e)
        }

        val authenticationBytes: ByteArray = if (cookieAuthFile != null) {
            try {
                withContext(dispatcher) {
                    readCookieAuthFile(cookieAuthFile, timeout = 10_000) { torJobException }
                }
            } catch (e: Exception) {
                torJob.cancelAndJoin()
                torDispatcher.close()
                return Result.failure(e)
            }
        } else {
            ByteArray(0)
        }

        val socket = Socket(Proxy.NO_PROXY)
        try {
            withContext(dispatcher) {
                socket.connect(address)
            }
        } catch (e: Exception) {
            torJob.cancelAndJoin()
            torDispatcher.close()
            return Result.failure(TorManagerException(
                "Failed to connect to control port", e
            ))
        }

        val controller: TorController = try {
            TorController.newInstance(socket)
        } catch (e: Exception) {
            torJob.cancelAndJoin()
            torDispatcher.close()
            return Result.failure(TorManagerException(
                "Failed to create instance of TorController", e
            ))
        }

        controller.authenticate(authenticationBytes).onFailure { ex ->
            controller.signal(TorControlSignal.Signal.Shutdown)
            torJob.cancelAndJoin()
            torDispatcher.close()
            return Result.failure(TorManagerException(
                "Failed to authenticate to Tor's control port", ex
            ))
        }

        return Result.success(Pair(controller, validated.torConfig))
    }

    @JvmSynthetic
    internal actual open fun close() {
        cancelTorJob()
        torDispatcher.close()
    }

    @JvmSynthetic
    internal actual open fun cancelTorJob() {
        torJob?.cancel()
    }

    @Throws(TorManagerException::class)
    private suspend fun readControlPortFile(
        file: File,
        timeout: Long,
        checkException: (() -> Throwable?)? = null,
    ): InetSocketAddress {
        val fileContents = retrieveFirstControlPortFromFile(file, timeout, checkException)
        return try {
            fileContents.split('=')[1].split(':').let { splits ->
                InetSocketAddress(splits[0].trim(), splits[1].trim().toInt())
            }
        } catch (e: Exception) {
            throw TorManagerException(
                "Failed to parse ${file.name} data ($fileContents) when retrieving control port", e
            )
        }
    }

    @Throws(TorManagerException::class)
    private suspend fun retrieveFirstControlPortFromFile(
        file: File,
        timeout: Long,
        checkException: (() -> Throwable?)?,
    ): String {
        if (timeout < 500L) {
            throw TorManagerException("Timeout must be greater than or equal to 500ms")
        }

        var time = 0L
        while (time < timeout) {
            if (file.exists()) {
                if (file.canRead()) {
                    val contents = file.readLines()
                    if (contents.isNotEmpty()) {
                        // only take the first ControlPort (if multiple are specified)
                        return contents.first()
                    }
                }
            }

            delay(READ_INTERVAL)
            time += READ_INTERVAL
            checkException?.invoke()?.let { ex -> throw ex }
        }

        throw TorManagerException("Failed to read ${file.name}")
    }

    @Throws(TorManagerException::class)
    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun readCookieAuthFile(
        file: File,
        timeout: Long,
        checkException: (() -> Throwable?)? = null,
    ): ByteArray {
        if (timeout < 500L) {
            throw TorManagerException("Timeout must be greater than or equal to 500ms")
        }

        var time = 0L
        while (time < timeout) {
            if (file.exists()) {
                if (file.canRead() && file.length() > 0L) {

                    try {
                        val bytes = ByteArray(file.length().toInt())
                        FileInputStream(file).use { i ->
                            var offset = 0

                            while (offset < bytes.size) {
                                val read = i.read(bytes, offset, bytes.size - offset)
                                if (read == -1) {
                                    throw EOFException("Error while reading ${file.name}")
                                }
                                offset += read
                            }
                        }

                        return bytes
                    } catch (e: Exception) {
                        throw TorManagerException(e)
                    }

                }
            }

            delay(READ_INTERVAL)
            time += READ_INTERVAL
            checkException?.invoke()?.let { ex ->
                throw InterruptedException("Reading ${file.name} interrupted", ex)
            }
        }

        throw TorManagerException("Failed to read ${file.name}")
    }

    @Throws(TorManagerException::class, CancellationException::class)
    protected actual abstract suspend fun startTor(
        configLines: List<String>,
        notify: (TorManagerEvent.Log) -> Unit,
    )

    /**
     * Not necessary for Android, only Linux && Darwin platforms.
     * */
    @Throws(TorManagerException::class)
    protected actual open fun setHiddenServiceDirPermissions(dir: Path) {}

    init {
        // Reference so that localhostAddress for JVM can have it's initial
        // value set immediately from BG thread.
        ControllerUtils
    }
}