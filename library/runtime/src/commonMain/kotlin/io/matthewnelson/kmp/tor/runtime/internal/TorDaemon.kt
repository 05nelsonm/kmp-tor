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
@file:Suppress("PrivatePropertyName", "LocalVariableName")

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileAlreadyExistsException
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.file.chmod2
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.readBytes
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.common.api.TorApi
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.i
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPSocketAddress.Companion.toIPSocketAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.filterByOption
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.internal.TorDaemon.StartArgs.Companion.createStartArgs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class TorDaemon private constructor(
    private val generator: TorConfigGenerator,
    private val manager: TorState.Manager,
    private val NOTIFIER: RuntimeEvent.Notifier,
    private val scope: CoroutineScope,
    private val state: FIDState,
): FileID by generator.environment {

    private val _toString by lazy { toFIDString(includeHashCode = true) }

    @Throws(Throwable::class)
    private suspend fun <T: Any?> start(
        checkCancellationOrInterrupt: () -> Unit,
        connect: suspend CtrlArguments.() -> T
    ): T = state.lock.withLock {
        try {
            NOTIFIER.lce(Lifecycle.Event.OnCreate(this))
            state.cancelAndJoinOtherProcess(checkCancellationOrInterrupt)
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        val config = try {
            checkCancellationOrInterrupt()
            generator.generate(NOTIFIER)
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        val startArgs = try {
            checkCancellationOrInterrupt()
            config.createStartArgs(generator.environment)
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        NOTIFIER.i(
            this,
            "Starting Tor with the following settings:\n"
            + "------------------------------------------------------------------------\n"
            + startArgs.load.config.toString()
            + "\n------------------------------------------------------------------------"
        )

        // Is **always** present in generated config.
        val controlPortFile = config
            .filterByOption<TorOption.ControlPortWriteToFile>()
            .first()
            .items
            .first()
            .argument
            .toFile()

        // Delete any remnants from last start (if present)
        try {
            controlPortFile.delete2(ignoreReadOnly = true)
        } catch (_: IOException) {}

        val torJob = startArgs.startTor(checkCancellationOrInterrupt)

        torJob.invokeOnCompletion {
            try {
                controlPortFile.delete2(ignoreReadOnly = true)
            } catch (_: IOException) {}
        }

        val result = try {
            val connection = awaitCtrlConnection(controlPortFile, checkCancellationOrInterrupt)
            val authenticate = config.awaitAuthentication(checkCancellationOrInterrupt)

            val arguments = CtrlArguments(
                torJob,
                authenticate,
                startArgs.load,
                connection,
            )

            checkCancellationOrInterrupt()
            connect(arguments)
        } catch (t: Throwable) {
            when (t) {
                is CancellationException,
                is InterruptedException -> {}
                else -> {
                    t.addSuppressed(IOException("Connect failure"))
                }
            }

            manager.update(TorState.Daemon.Stopping)
            torJob.cancel()
            throw t
        }

        result
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun FIDState.cancelAndJoinOtherProcess(checkCancellationOrInterrupt: () -> Unit) {
        torJob?.cancelAndJoin()
        yield()
        checkCancellationOrInterrupt()

        manager.update(TorState.Daemon.Starting, TorState.Network.Disabled)

        // Need to ensure there is at least 500ms between last
        // process' stop, and this process' start for TorDaemon
        val lastStop = stopMark ?: return

        val delayTime = 500.milliseconds
        val delayIncrement = 100.milliseconds
        val start = TimeSource.Monotonic.markNow()
        var remainder = delayTime - lastStop.elapsedNow()
        var wasNotified = false

        while (remainder > Duration.ZERO) {

            if (!wasNotified) {
                wasNotified = true
                NOTIFIER.i(this@TorDaemon, "Waiting ${remainder.inWholeMilliseconds}ms for previous process to clean up.")
            }

            delay(if (remainder < delayIncrement) remainder else delayIncrement)
            checkCancellationOrInterrupt()
            remainder = delayTime - lastStop.elapsedNow()
        }

        if (wasNotified) {
            val elapsed = start.elapsedNow().inWholeMilliseconds
            NOTIFIER.i(this@TorDaemon, "Resuming startup after waiting ~${elapsed}ms")
        }
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private fun StartArgs.startTor(checkCancellationOrInterrupt: () -> Unit): Job {
        val loader = generator.environment.loader

        var process: Process? = null

        val (awaitStop, terminate) = try {
            checkCancellationOrInterrupt()

            when (loader) {
                is ResourceLoader.Tor.Exec -> {
                    process = loader.process(TorBinder) { tor, configureEnv ->
                        Process.Builder(command = tor.path)
                            .args(cmdLine)
                            .environment(configureEnv)
                            .environment("HOME", generator.environment.workDirectory.path)
                            .stdin(Stdio.Null)
                            .stdout(Stdio.Null)
                            .stderr(Stdio.Null)
                            .destroySignal(Signal.SIGTERM)
                            .spawn()
                    }

                    suspend { process.waitForAsync() } to process::destroy
                }
                is ResourceLoader.Tor.NoExec -> {
                    loader.withApi(TorBinder) {
                        torRunMain(cmdLine)

                        suspend {
                            while (state() == TorApi.State.STARTED) {
                                delay(100.milliseconds)
                            }
                        } to ::terminateAndAwaitResult
                    }
                }
            }
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this@TorDaemon))
            throw t
        }

        NOTIFIER.lce(Lifecycle.Event.OnStart(this@TorDaemon))

        if (process != null) {
            NOTIFIER.i(this@TorDaemon, process.toString())
        }

        // If scope is cancelled when we go to launch
        // the coroutine and await exit, it won't trigger
        // the try/finally block. So, this single callback
        // allows to ensure it is executed either within
        // the non-cancelled job, or via invokeOnCompletion
        // handler (immediately upon handle being set).
        val finalize = Executable.Once.of(concurrent = true, executable = {
            try {
                terminate()
                NOTIFIER.lce(Lifecycle.Event.OnStop(this@TorDaemon))
            } finally {
                state.stopMark = TimeSource.Monotonic.markNow()
                manager.update(TorState.Daemon.Off)
            }
        })

        val torJob = scope.launch {
            try {
                awaitStop()
            } finally {
                finalize.execute()
            }
        }

        state.torJob = torJob

        torJob.invokeOnCompletion {
            finalize.execute()
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this@TorDaemon))
        }

        return torJob
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun awaitCtrlConnection(
        controlPortFile: File,
        checkCancellationOrInterrupt: () -> Unit,
    ): CtrlArguments.Connection {
        timedDelay(100.milliseconds)

        val lines = controlPortFile
            .awaitRead(10.seconds, checkCancellationOrInterrupt)
            .decodeToString()
            .lines()
            .mapNotNull { it.ifBlank { null } }

        NOTIFIER.d(this@TorDaemon, "ControlFile$lines")

        val addresses = ArrayList<String>(1)

        for (line in lines) {
            val argument = line.substringAfter('=', "")
            if (argument.isBlank()) continue

            // UNIX_PORT=/tmp/kmp_tor_ctrl/data/ctrl.sock
            if (line.startsWith("UNIX_PORT")) {
                val file = argument.toFile()
                if (!file.exists2()) continue

                // Prefer UnixDomainSocket if present
                return CtrlArguments.Connection(file)
            }

            // PORT=127.0.0.1:9055
            if (line.startsWith("PORT")) {
                addresses.add(argument)
            }
        }

        // No UnixDomainSocket, fallback to first TCP address
        for (address in addresses) {
            val socketAddress = address.toIPSocketAddressOrNull() ?: continue
            return CtrlArguments.Connection(socketAddress)
        }

        throw IOException("Failed to acquire control connection info from file[${controlPortFile.name}]")
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun TorConfig.awaitAuthentication(
        checkCancellationOrInterrupt: () -> Unit,
    ): TorCmd.Authenticate {
        // TODO: HashedControlPassword Issue #1

        return filterByOption<TorOption.CookieAuthFile>()
            .firstOrNull()
            ?.items
            ?.first()
            ?.argument
            ?.toFile()
            ?.awaitRead(1.seconds, checkCancellationOrInterrupt)
            ?.let { bytes -> TorCmd.Authenticate(cookie = bytes)  }
            ?: TorCmd.Authenticate() // Unauthenticated
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun File.awaitRead(
        timeout: Duration,
        checkCancellationOrInterrupt: () -> Unit,
    ): ByteArray {
        require(timeout >= 500.milliseconds) { "timeout must be greater than or equal to 500ms" }
        checkCancellationOrInterrupt()

        val startMark = TimeSource.Monotonic.markNow()

        var notified = false
        while (true) {
            // Ensure that stdout is flowing, otherwise wait until it is.
            val content = try {
                readBytes()
            } catch (_: IOException) {
                null
            }

            // It's either ctrl port file or cookie auth, so
            // be longer than PORT=
            if (content != null && content.size > 5) {
                return content
            }

            if (!notified) {
                notified = true
                NOTIFIER.d(this@TorDaemon, "Waiting for tor to write to $name")
            }

            timedDelay(50.milliseconds)

            checkCancellationOrInterrupt()

            if (state.torJob?.isActive != true) {
                throw IOException("Tor exited early...")
            }

            if (startMark.elapsedNow() > timeout) break
        }

        throw IOException("$TIMED_OUT after ${timeout.inWholeMilliseconds}ms waiting for tor to write to file[$name]")
    }

    public override fun toString(): String = _toString

    private class StartArgs private constructor(val cmdLine: List<String>, val load: TorCmd.Config.Load) {

        companion object {

            @Throws(IOException::class)
            fun TorConfig.createStartArgs(env: TorRuntime.Environment): StartArgs {
                val cmdLine = mutableListOf<String>().apply {
                    add("--quiet")
                    add("--torrc-file")

                    if (env.loader is ResourceLoader.Tor.Exec) {
                        add("-") // stdin (i.e. /dev/null)
                    } else {
                        val torrc = env.loader.resourceDir.resolve("__torrc")
                        add(torrc.path)
                        add("--ignore-missing-torrc")
                        torrc.delete2(ignoreReadOnly = true)
                    }
                }

                val directories = mutableSetOf<File>().apply {
                    add(env.workDirectory)
                    add(env.cacheDirectory)
                }

                for (setting in settings) {
                    for (lineItem in setting.items) {
                        val line = lineItem.toString()

                        // A space is ALWAYS present for LineItem.toString()
                        // as when argument is blank, LineItem creation will
                        // return null (will not be created).
                        val i = line.indexOf(' ')

                        val key = "--${line.substring(0, i)}"
                        val remainder = line.substring(i + 1)

                        if (lineItem.option.isCmdLineArg) {
                            cmdLine.add(key)
                            cmdLine.add(remainder)
                        }

                        if (lineItem.option.attributes.contains(TorOption.Attribute.DIRECTORY)) {
                            directories.add(lineItem.argument.toFile())
                        }
                    }
                }

                directories.forEach { directory ->
                    try {
                        directory.mkdirs2(mode = "700", mustCreate = true)
                    } catch (_: FileAlreadyExistsException) {
                        directory.chmod2(mode = "700")
                    }
                }

                return StartArgs(cmdLine.toImmutableList(), TorCmd.Config.Load(this))
            }
        }
    }

    internal class CtrlArguments internal constructor(
        internal val processJob: Job,
        internal val authenticate: TorCmd.Authenticate,
        internal val configLoad: TorCmd.Config.Load,
        internal val connection: Connection,
    ) {

        internal class Connection private constructor(
            private val tcp: IPSocketAddress?,
            private val uds: File?,
        ) {

            constructor(tcp: IPSocketAddress): this(tcp, null)
            constructor(uds: File): this(null, uds)

            @Throws(CancellationException::class, IOException::class)
            internal suspend fun openWith(factory: TorCtrl.Factory): TorCtrl {
                uds?.let { return factory.connectAsync(it) }
                return factory.connectAsync(tcp!!)
            }
        }
    }

    internal companion object: InstanceKeeper<String, Any>() {

        private object TorBinder: ResourceLoader.RuntimeBinder

        // Exposed for testing only
        @get:JvmSynthetic
        internal val torBinder: ResourceLoader.RuntimeBinder get() = TorBinder

        @JvmSynthetic
        @Throws(Throwable::class)
        internal suspend fun <T: Any> start(
            generator: TorConfigGenerator,
            manager: TorState.Manager,
            NOTIFIER: RuntimeEvent.Notifier,
            scope: CoroutineScope,
            checkCancellationOrInterrupt: () -> Unit,
            connect: suspend CtrlArguments.() -> T,
        ): T {
            val state = getOrCreateInstance(generator.environment.fid) { FIDState() }

            return TorDaemon(
                generator,
                manager,
                NOTIFIER,
                scope,
                state as FIDState,
            ).start(checkCancellationOrInterrupt, connect)
        }

        private class FIDState {
            val lock = Mutex()

            @Volatile
            var torJob: Job? = null

            @Volatile
            var stopMark: TimeSource.Monotonic.ValueTimeMark? = null
        }

        private const val TIMED_OUT = "Timed out after"
    }
}
