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

package io.matthewnelson.kmp.tor.runtime.internal.process

import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.OutputFeed
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.e
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.i
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.stderr
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.stdout
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress.Companion.toIPSocketAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.internal.InstanceKeeper
import io.matthewnelson.kmp.tor.runtime.internal.TorConfigGenerator
import io.matthewnelson.kmp.tor.runtime.internal.process.TorDaemon.StartArgs.Companion.createStartArgs
import io.matthewnelson.kmp.tor.runtime.internal.setDirectoryPermissions
import io.matthewnelson.kmp.tor.runtime.internal.timedDelay
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

        val (config, paths) = try {
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
            + startArgs.load.configText
            + "\n------------------------------------------------------------------------"
        )

        val (feed, processJob) = startArgs.spawnProcess(paths, checkCancellationOrInterrupt)

        val result = try {
            val connection = config.awaitCtrlConnection(feed, checkCancellationOrInterrupt)
            val authenticate = config.awaitAuthentication(feed, checkCancellationOrInterrupt)

            val arguments = CtrlArguments(
                processJob,
                authenticate,
                startArgs.load,
                connection,
            )

            checkCancellationOrInterrupt()
            connect(arguments)
        } catch (t: Throwable) {
            when (t) {
                is CancellationException,
                is InterruptedException,
                is ProcessStartException -> {}
                else -> {
                    val e = feed.createError("Connect failure")
                    t.addSuppressed(e)
                }
            }

            manager.update(TorState.Daemon.Stopping)
            processJob.cancel()
            throw t
        } finally {
            feed.close()
        }

        result
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun FIDState.cancelAndJoinOtherProcess(checkCancellationOrInterrupt: () -> Unit) {
        processJob?.cancelAndJoin()
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
    private fun StartArgs.spawnProcess(
        paths: ResourceInstaller.Paths.Tor,
        checkCancellationOrInterrupt: () -> Unit,
    ): Pair<StartupFeedParser, Job> {
        val process = try {
            checkCancellationOrInterrupt()

            Process.Builder(command = paths.tor.path)
                .args(cmdLine)
                .environment { putAll(generator.environment.processEnv) }
                .onError { t ->
                    if (t.cause is UncaughtException) {
                        // OutputFeed was passed to event observers
                        // and one threw exception. That exception
                        // was caught by the UncaughtException.Handler
                        // and piped to RuntimeEvent.ERROR observer. If
                        // the ERROR observer threw (or one was not
                        // subscribed), the UncaughtException was thrown
                        // within the OutputFeed.onOutput lambda and piped
                        // here. Throw it and shut things down.
                        throw t.cause
                    } else {
                        NOTIFIER.e(t)
                    }
                }
                .stdin(Stdio.Null)
                .stdout(Stdio.Pipe)
                .stderr(Stdio.Pipe)
                .destroySignal(Signal.SIGTERM)
                .spawn()
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this@TorDaemon))
            throw t
        }

        NOTIFIER.lce(Lifecycle.Event.OnStart(this@TorDaemon))
        NOTIFIER.i(this@TorDaemon, process.toString())

        val startupFeed = StartupFeedParser(exitCodeOrNull = {
            try {
                process.exitCode()
            } catch (_: IllegalStateException) {
                null
            }
        })

        run {
            var notify: Executable.Once?
            notify = Executable.Once.of(executable = {
                notify = null
                val time = process.startTime.elapsedNow().inWholeMilliseconds

                NOTIFIER.i(
                    this@TorDaemon,
                    "took ${time}ms before dispatching its first stdout line"
                )
            })

            process.stdoutFeed(
                startupFeed.stdout,
                OutputFeed { line ->
                    if (line == null) return@OutputFeed
                    notify?.execute()
                    NOTIFIER.stdout(this@TorDaemon, line)
                }
            ).stderrFeed(
                startupFeed.stderr,
                OutputFeed { line ->
                    if (line == null) return@OutputFeed
                    NOTIFIER.stderr(this@TorDaemon, line)
                }
            )
        }

        // If scope is cancelled when we go to launch
        // the coroutine to await process exit, it won't
        // trigger the try/finally block. So, this single
        // execution callback allows to ensure it is
        // executed either within the non-cancelled job,
        // or via invokeOnCompletion handler.
        val completion = Executable.Once.of(concurrent = true, executable = {
            state.stopMark = TimeSource.Monotonic.markNow()

            try {
                process.destroy()
                NOTIFIER.lce(Lifecycle.Event.OnStop(this@TorDaemon))
            } finally {
                manager.update(TorState.Daemon.Off)
            }
        })

        val processJob = scope.launch {
            try {
                process.waitForAsync()

                // Process has exited. If we have not completed the
                // startup, give OutputFeed a moment to fully flush
                // before Process.destroy() is called.
                if (!startupFeed.isClosed) {
                    timedDelay(50.milliseconds)
                }
            } finally {
                completion.execute()
            }
        }

        state.processJob = processJob

        processJob.invokeOnCompletion {
            completion.execute()
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this@TorDaemon))
        }

        return startupFeed to processJob
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun TorConfig.awaitCtrlConnection(
        feed: StartupFeedParser,
        checkCancellationOrInterrupt: () -> Unit,
    ): CtrlArguments.Connection {
        // Is **always** present in generated config.
        val ctrlPortFile = filterByKeyword<TorConfig.ControlPortWriteToFile.Companion>()
            .first()
            .argument
            .toFile()

        val lines = ctrlPortFile
            .awaitRead(feed, 10.seconds, checkCancellationOrInterrupt)
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
                if (!file.exists()) continue

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

        throw feed.createError("Failed to acquire control connection info from file[${ctrlPortFile.name}]")
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun TorConfig.awaitAuthentication(
        feed: StartupFeedParser,
        checkCancellationOrInterrupt: () -> Unit,
    ): TorCmd.Authenticate {
        // TODO: HashedControlPassword Issue #1

        return filterByKeyword<TorConfig.CookieAuthFile.Companion>()
            .firstOrNull()
            ?.argument
            ?.toFile()
            ?.awaitRead(feed, 1.seconds, checkCancellationOrInterrupt)
            ?.let { bytes -> TorCmd.Authenticate(cookie = bytes)  }
            ?: TorCmd.Authenticate() // Unauthenticated
    }

    @Throws(CancellationException::class, InterruptedException::class, IOException::class)
    private suspend fun File.awaitRead(
        feed: StartupFeedParser,
        timeout: Duration,
        checkCancellationOrInterrupt: () -> Unit,
    ): ByteArray {
        require(timeout >= 500.milliseconds) { "timeout must be greater than or equal to 500ms" }
        feed.checkError()
        checkCancellationOrInterrupt()

        val startMark = TimeSource.Monotonic.markNow()

        var notified = false
        var throwInactiveNext = false
        while (true) {
            // Ensure that stdout is flowing, otherwise wait until it is.
            if (feed.isReady) {

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
            }

            if (!notified) {
                notified = true
                NOTIFIER.d(this@TorDaemon, "Waiting for tor to write to $name")
            }

            timedDelay(50.milliseconds)
            feed.checkError()

            if (throwInactiveNext) {
                throw feed.createError("Process exited early...")
            }

            checkCancellationOrInterrupt()

            if (state.processJob?.isActive != true) {
                // Want to give Process time to close down resources
                // and dispatch null line, which will result in the
                // OutputFeed generating an error. Give it another 50ms.
                throwInactiveNext = true
            }

            if (startMark.elapsedNow() > timeout) break
        }

        throw feed.createError("$TIMED_OUT after ${timeout.inWholeMilliseconds}ms waiting for tor to write to file[$name]")
    }

    public override fun toString(): String = _toString

    private class StartArgs private constructor(val cmdLine: List<String>, val load: TorCmd.Config.Load) {

        companion object {

            @Throws(IOException::class)
            fun TorConfig.createStartArgs(env: TorRuntime.Environment): StartArgs {
                val cmdLine = mutableListOf<String>().apply {
                    add("--torrc-file")
                    add("-") // stdin (i.e. /dev/null)
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

                        if (lineItem.keyword.isCmdLineArg) {
                            cmdLine.add(key)
                            cmdLine.add(remainder)
                        }

                        if (lineItem.keyword.attributes.contains(TorConfig.Keyword.Attribute.Directory)) {
                            directories.add(lineItem.argument.toFile())
                        }
                    }
                }

                directories.forEach { directory ->
                    if (!directory.exists() && !directory.mkdirs()) {
                        throw IOException("Failed to create directory[$directory]")
                    }

                    try {
                        directory.setDirectoryPermissions()
                    } catch (t: Throwable) {
                        throw t.wrapIOException { "Failed to set permissions for directory[$directory]" }
                    }
                }

                return StartArgs(cmdLine.toImmutableList(), TorCmd.Config.Load(toString()))
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

            val daemon = TorDaemon(
                generator,
                manager,
                NOTIFIER,
                scope,
                state as FIDState,
            )

            val result: T? = try {
                daemon.start(checkCancellationOrInterrupt, connect)
            } catch (e: ProcessStartException) {
                // Can happen on iOS b/c of their shit implementation
                // of posix_spawn where it will fail but still creates a
                // zombie process.
                if (
                    e.message.contains("cause: $TIMED_OUT")
                    && e.message.contains("stdout: []")
                ) {
                    null
                } else {
                    throw e
                }
            }

            if (result != null) return result

            NOTIFIER.w(daemon, "ZOMBIE PROCESS! Retrying...")

            val daemonRetry = TorDaemon(
                generator,
                manager,
                NOTIFIER,
                scope,
                state,
            )

            return daemonRetry.start(checkCancellationOrInterrupt, connect)
        }

        private class FIDState {
            val lock = Mutex()

            @Volatile
            var processJob: Job? = null

            @Volatile
            var stopMark: TimeSource.Monotonic.ValueTimeMark? = null
        }

        private const val TIMED_OUT = "Timed out after"
    }
}
