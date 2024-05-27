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
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.OutputFeed
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.i
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.stderr
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.stdout
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress
import io.matthewnelson.kmp.tor.runtime.core.address.IPSocketAddress.Companion.toIPSocketAddressOrNull
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.internal.TorProcess.StartArgs.Companion.createStartArgs
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class TorProcess private constructor(
    private val generator: TorConfigGenerator,
    private val manager: TorState.Manager,
    private val NOTIFIER: RuntimeEvent.Notifier,
    private val scope: CoroutineScope,
    private val state: FIDState,
    private val checkInterrupt: () -> Unit,
): FileID by generator.environment {

    @Throws(Throwable::class)
    private suspend fun <T: Any?> start(connect: suspend CtrlArguments.() -> T): T = state.lock.withLock {
        try {
            NOTIFIER.lce(Lifecycle.Event.OnCreate(this))
            state.cancelAndJoinOtherProcess()
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        val (config, paths) = try {
            checkInterrupt()
            generator.generate(NOTIFIER)
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        val startArgs = try {
            checkInterrupt()
            config.createStartArgs(generator.environment)
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        NOTIFIER.i(
            this,
            "Starting Tor with the following settings:\n"
            + "------------------------------------------------------------------------\n"
            + startArgs.loadText
            + "\n------------------------------------------------------------------------"
        )

        val (feed, processJob) = startArgs.spawnProcess(paths)

        val result = try {
            val connection = config.awaitCtrlConnection(feed)
            checkInterrupt()
            val authenticate = config.awaitAuthentication(feed)
            checkInterrupt()
            val configLoad = TorCmd.Config.Load(startArgs.loadText)

            val arguments = CtrlArguments(
                processJob,
                authenticate,
                configLoad,
                connection,
            )

            checkInterrupt()
            connect(arguments)
        } catch (t: Throwable) {
            manager.update(TorState.Daemon.Stopping)
            processJob.cancel()
            throw t
        }

        feed.done()

        result
    }

    private suspend fun FIDState.cancelAndJoinOtherProcess() {
        processJob?.cancelAndJoin()
        yield()
        checkInterrupt()

        manager.update(TorState.Daemon.Starting, TorState.Network.Disabled)

        // Need to ensure there is at least 500ms between last
        // process' stop, and this process' start for TorProcess
        val lastStop = stopMark ?: return

        val delayTime = 500.milliseconds
        val delayIncrement = 50.milliseconds
        val start = TimeSource.Monotonic.markNow()
        var remainder = delayTime - lastStop.elapsedNow()
        var wasNotified = false

        while (remainder > 0.milliseconds) {

            if (!wasNotified) {
                wasNotified = true
                NOTIFIER.i(this@TorProcess, "Waiting ${remainder.inWholeMilliseconds}ms for previous process to clean up.")
            }

            delay(if (remainder < delayIncrement) remainder else delayIncrement)
            checkInterrupt()
            remainder = delayTime - lastStop.elapsedNow()
        }

        if (wasNotified) {
            NOTIFIER.i(this@TorProcess, "Resuming startup after waiting ~${start.elapsedNow().inWholeMilliseconds}ms")
        }
    }

    @Throws(Throwable::class)
    private suspend fun StartArgs.spawnProcess(paths: ResourceInstaller.Paths.Tor): Pair<StdoutFeed, Job> {
        val process = try {
            checkInterrupt()

            Process.Builder(command = paths.tor.path)
                .args(cmdLine)
                .environment { putAll(generator.environment.processEnv) }
                .stdin(Stdio.Null)
                .stdout(Stdio.Pipe)
                .stderr(Stdio.Pipe)
                .destroySignal(Signal.SIGTERM)
                .spawn()
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this@TorProcess))
            throw t
        }

        NOTIFIER.lce(Lifecycle.Event.OnStart(this@TorProcess))
        NOTIFIER.i(this@TorProcess, process.toString())

        val feed = StdoutFeed(process.startTime)

        process.stdoutFeed(feed).stderrFeed { line ->
            if (line == null) return@stderrFeed
            NOTIFIER.stderr(this@TorProcess, line)
        }

        // If scope is cancelled when we go to launch
        // the coroutine to await process exit, it won't
        // trigger the try/finally block. So, this single
        // execution callback allows to ensure it is
        // executed either within the non-cancelled job,
        // or via invokeOnCompletion handler.
        val completion = Executable.Once.of(concurrent = true, executable = {
            state.stopMark = TimeSource.Monotonic.markNow()
            process.destroy()

            try {
                NOTIFIER.lce(Lifecycle.Event.OnStop(this@TorProcess))
            } finally {
                manager.update(TorState.Daemon.Off)
            }
        })

        withContext(NonCancellable) {
            val exitCode = process.waitForAsync(250.milliseconds) ?: return@withContext

            // exitCode non-null. Process exited early.
            NOTIFIER.w(this@TorProcess, "Process exited early with code[$exitCode]")

            try {
                // Ensure OutputFeed are flushed before destroy is called
                timedDelay(50.milliseconds)

                // Need to destroy before awaiting stdout/stderr
                process.destroy()

                process.stdoutWaiter()
                    .awaitStopAsync()
                    .stderrWaiter()
                    .awaitStopAsync()
            } finally {
                completion.execute()
            }
        }

        val processJob = scope.launch {
            try {
                process.waitForAsync()
            } finally {
                completion.execute()
            }
        }

        state.processJob = processJob

        processJob.invokeOnCompletion {
            completion.execute()
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this@TorProcess))
        }

        return feed to processJob
    }

    @Throws(Throwable::class)
    private suspend fun TorConfig.awaitCtrlConnection(feed: StdoutFeed): CtrlArguments.Connection {
        // Is **always** present in generated config.
        val ctrlPortFile = filterByKeyword<TorConfig.ControlPortWriteToFile.Companion>()
            .first()
            .argument
            .toFile()

        val lines = ctrlPortFile
            .awaitRead(feed, 3.seconds)
            .decodeToString()
            .lines()
            .mapNotNull { it.ifBlank { null } }

        NOTIFIER.d(this@TorProcess, "ControlFile$lines")

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

        throw IOException("Failed to acquire control connection address from file[$ctrlPortFile]")
    }

    @Throws(Throwable::class)
    private suspend fun TorConfig.awaitAuthentication(feed: StdoutFeed): TorCmd.Authenticate {
        // TODO: HashedControlPassword Issue #1

        return filterByKeyword<TorConfig.CookieAuthFile.Companion>()
            .firstOrNull()
            ?.argument
            ?.toFile()
            ?.awaitRead(feed, 1.seconds)
            ?.let { bytes -> TorCmd.Authenticate(cookie = bytes)  }
            ?: TorCmd.Authenticate() // Unauthenticated
    }

    @Throws(CancellationException::class, IOException::class)
    private suspend fun File.awaitRead(
        feed: StdoutFeed,
        timeout: Duration,
    ): ByteArray {
        require(timeout >= 500.milliseconds) { "timeout must be greater than or equal to 500ms" }
        feed.checkError()
        checkInterrupt()

        val startMark = TimeSource.Monotonic.markNow()

        var notified = false
        while (true) {
            // Ensure that stdout is flowing, otherwise may still need to
            // wait for tor to clean up old files.
            if (feed.lines > 5 && exists()) {

                val content = readBytes()
                if (content.size > 5) {
                    return content
                }
            }

            if (!notified) {
                notified = true
                NOTIFIER.d(this@TorProcess, "Waiting for tor to write to $name")
            }

            timedDelay(50.milliseconds)
            feed.checkError()
            checkInterrupt()

            if (startMark.elapsedNow() > timeout) break
        }

        throw IOException("Timed out after ${timeout.inWholeMilliseconds}ms waiting for tor to write to file[$this]")
    }

    public override fun toString(): String = toFIDString(includeHashCode = true)

    // Helper for catching tor startup errors while waiting
    // for other startup steps to be completed. Tor will fail
    // to start and indicate so via stdout within the first
    // 10-20 lines of output.
    //
    // This will parse those lines and generate an IOException
    // which can be thrown while polling files that tor writes
    // data to (control port & cookie authentication).
    private inner class StdoutFeed(private val startTime: ComparableTimeMark): OutputFeed {

        @Volatile
        private var _lines = 0
        @Volatile
        private var _sb = StringBuilder()
        @Volatile
        private var _error: IOException? = null

        private val maxTimeNoOutput = 1_500.milliseconds

        val lines: Int get() = _lines

        override fun onOutput(line: String?) {
            if (_error == null && _lines < 30) {
                _lines++
                if (_sb.isEmpty()) {
                    // First line of output
                    val time = startTime.elapsedNow().inWholeMilliseconds
                    NOTIFIER.i(this@TorProcess, "took ${time}ms before dispatching its first stdout line")
                } else {
                    _sb.appendLine()
                }
                _sb.append(line ?: "Process Exited Unexpectedly...")
                line.parseForError()
            }

            if (line == null) return
            NOTIFIER.stdout(this@TorProcess, line)
        }

        @Throws(IOException::class)
        fun checkError() {
            _error?.let { err -> throw err }

            if (_lines > 0) return

            val runtime = startTime.elapsedNow()
            if (runtime < maxTimeNoOutput) return
            throw IOException(
                "Process failure."
                + " ${this@TorProcess} has been running for ${runtime.inWholeMilliseconds}ms"
                + " without any stdout output"
            )
        }

        fun done() {
            // Will inhibit from adding any more lines
            _lines = 50

            // clear and de-reference
            val sb = _sb
            this._sb = StringBuilder(/* capacity =*/ 1)

            try {
                val count = sb.count()
                sb.clear()
                repeat(count) { sb.append(' ') }
            } catch (_: Throwable) {}
        }

        private fun String?.parseForError() {
            if (this != null && !contains(PROCESS_ERR) && !contains(PROCESS_OTHER)) return
            // If line is null, process has stopped

            val message = _sb.toString()

            val count = _sb.count()
            _sb.clear()
            repeat(count) { _sb.append(' ') }

            _error = IOException("Process Failure\n$message")
        }
    }

    private class StartArgs private constructor(val cmdLine: List<String>, val loadText: String) {

        companion object {

            @Throws(IOException::class)
            fun TorConfig.createStartArgs(env: TorRuntime.Environment): StartArgs {
                val cmdLine = mutableListOf<String>().apply {
                    add("-f")
                    add(env.torrcFile.path)
                    add("--defaults-torrc")
                    add(env.torrcDefaultsFile.path)
                    add("--ignore-missing-torrc")
                }

                val createDirs = mutableSetOf<File>().apply {
                    add(env.workDirectory)
                    add(env.cacheDirectory)
                }

                val createFiles = mutableSetOf<File>().apply {
                    add(env.torrcFile)
                    add(env.torrcDefaultsFile)
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
                            createDirs.add(lineItem.argument.toFile())
                        }
                    }
                }

                createDirs.prepareFilesystem(areDirectories = true)
                createFiles.prepareFilesystem(areDirectories = false)

                // TODO: Incorporate torrc & torrc-defaults
                val loadText = toString()

                return StartArgs(cmdLine.toImmutableList(), loadText)
            }

            @Throws(IOException::class)
            private fun Set<File>.prepareFilesystem(areDirectories: Boolean) = forEach { file ->
                if (areDirectories) {
                    if (!file.exists() && !file.mkdirs()) {
                        throw IOException("Failed to create directory[$file]")
                    }

                    try {
                        file.setDirectoryPermissions()
                    } catch (t: Throwable) {
                        throw t.wrapIOException { "Failed to set permissions for directory[$file]" }
                    }
                } else {
                    if (!file.exists()) {
                        file.writeBytes(EMPTY_BYTES)
                    }

                    try {
                        file.setFilePermissions()
                    } catch (t: Throwable) {
                        throw t.wrapIOException { "Failed to set permissions for file[$file]" }
                    }
                }
            }

            private val EMPTY_BYTES = ByteArray(0)
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
        internal suspend fun <T: Any?> start(
            generator: TorConfigGenerator,
            manager: TorState.Manager,
            NOTIFIER: RuntimeEvent.Notifier,
            scope: CoroutineScope,
            checkInterrupt: () -> Unit,
            connect: suspend CtrlArguments.() -> T,
        ): T {
            val state = getOrCreateInstance(generator.environment.fid) { FIDState() }

            val process = TorProcess(
                generator,
                manager,
                NOTIFIER,
                scope,
                state as FIDState,
                checkInterrupt,
            )

            return process.start(connect)
        }

        private class FIDState {
            val lock = Mutex()

            @Volatile
            var processJob: Job? = null

            @Volatile
            var stopMark: TimeSource.Monotonic.ValueTimeMark? = null
        }

        private const val PROCESS_ERR: String = " [err] "
        private const val PROCESS_OTHER: String = " [warn] It looks like another Tor process is running with the same data directory."
    }
}
