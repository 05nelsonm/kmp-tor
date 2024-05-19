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
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.process.Signal
import io.matthewnelson.kmp.process.Stdio
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.toFIDString
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.i
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.p
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress
import io.matthewnelson.kmp.tor.runtime.core.address.ProxyAddress.Companion.toProxyAddressOrNull
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class TorProcess private constructor(
    private val generator: TorConfigGenerator,
    private val NOTIFIER: RuntimeEvent.Notifier,
    private val scope: CoroutineScope,
    private val state: FIDState,
): FileID by generator.environment {

    @Throws(Throwable::class)
    private suspend fun <T: Any?> start(
        connect: suspend CtrlArguments.() -> T
    ): T = state.lock.withLock {
        state.cancelAndJoinOtherProcess()

        val (config, paths) = try {
            generator.generate(NOTIFIER)
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        val startArgs = try {
            config.createStartArgs(generator.environment)
        } catch (t: Throwable) {
            NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
            throw t
        }

        try {
            startArgs.verifyConfig(paths)
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

        val processJob = startArgs.awaitProcessStart(paths)
        val connection = config.awaitCtrlConnection(processJob)
        val authenticate = config.awaitAuthentication(processJob)
        val configLoad = TorCmd.Config.Load(startArgs.loadText)

        val arguments = CtrlArguments(
            processJob,
            authenticate,
            configLoad,
            connection,
        )

        val result = try {
            connect(arguments)
        } catch (t: Throwable) {
            processJob.cancel()
            throw t
        }

        result
    }

    @Throws(IOException::class)
    private fun FIDState.ensureActive() {
        if (processJob?.isActive == true) return
        throw IOException("Process exited early... Check logs.")
    }

    private suspend fun FIDState.cancelAndJoinOtherProcess() {
        val job = processJob ?: return

        // Ensures that if the process was active that we yield
        // briefly such that the try/finally block can set its
        // stopMark.
        val ensureFinally = (if (job.isActive) 5 else -1).milliseconds

        job.cancelAndJoin()
        delay(ensureFinally)

        // Need to ensure there is at least 350ms between last
        // process' stop, and this process' start for TorProcess
        val lastStop = stopMark ?: return

        var wasNotified = false
        while (true) {
            val duration = 500.milliseconds - lastStop.elapsedNow()
            if (duration < 1.milliseconds) break

            if (!wasNotified) {
                wasNotified = true
                NOTIFIER.i(this@TorProcess, "Waiting ${duration.inWholeMilliseconds}ms for previous process to clean up.")
            }

            delay(duration)
        }

        if (wasNotified) {
            NOTIFIER.i(this@TorProcess, "Resuming start")
        }
    }

    @Throws(Throwable::class)
    private suspend fun StartArgs.verifyConfig(paths: ResourceInstaller.Paths.Tor) {
        val sbStdout = StringBuilder()
        val sbStderr = StringBuilder()
        val timeout = 500.milliseconds

        val (process, exitCode) = Process.Builder(command = paths.tor.path)
            .args("--verify-config")
            .args(cmdLine)
            .environment("HOME", generator.environment.workDirectory.path)
            .stdin(Stdio.Null)
            .stdout(Stdio.Pipe)
            .stderr(Stdio.Pipe)
            .destroySignal(Signal.SIGTERM)
            .spawn { process ->

                process.stdoutFeed { line ->
                    if (line == null) return@stdoutFeed
                    if (sbStdout.isNotEmpty()) {
                        sbStdout.appendLine()
                    }
                    sbStdout.append(line)
                }.stderrFeed { line ->
                    if (line == null) return@stderrFeed
                    if (sbStderr.isNotEmpty()) {
                        sbStderr.appendLine()
                    }
                    sbStderr.append(line)
                }

                timedDelay(150.milliseconds)

                val exitCode = process.waitForAsync(timeout - 200.milliseconds)

                timedDelay(50.milliseconds)

                process to exitCode
            } // << destroy called

        val exitCodeFinal = process
            .stdoutWaiter()
            .awaitStopAsync()
            .stderrWaiter()
            .awaitStopAsync()
            .waitForAsync()

        val stdout = sbStdout.toString()
        val stderr = sbStderr.toString()

        val validMsg = "Configuration was valid"
        if (stdout.contains(validMsg, ignoreCase = true)) {
            NOTIFIER.i(this@TorProcess, validMsg)
            return
        }

        if (stdout.isBlank() && exitCode == null) {
            NOTIFIER.w(
                this@TorProcess,
                "--verify-config timed out after ${timeout.inWholeMilliseconds}ms."
                + " Continuing without config verification..."
            )
            return
        }

        buildString {
            appendLine("Configuration Was Invalid: [")
            append("    stdout: [")
            stdout.takeIf { it.isNotBlank() }?.let { stdout ->
                for (line in stdout.lines()) {
                    appendLine()
                    append("        ")
                    append(line)
                }
                appendLine()
                appendLine("    ]")
            } ?: appendLine(']')

            append("    stderr: [")
            stderr.takeIf { it.isNotBlank() }?.let { stderr ->
                for (line in stderr.lines()) {
                    appendLine()
                    append("        ")
                    append(line)
                }
                appendLine()
                append("    ]")
            } ?: append(']')

            appendLine()
            append("    exitCode: ")
            append(exitCode ?: exitCodeFinal)

            appendLine()
            append("    processError: ")

            if (exitCode == null) {
                append("Timed out after ${timeout.inWholeMilliseconds}ms")
            } else {
                append("null")
            }
            appendLine()
            append(']')
        }.let { message -> throw IllegalArgumentException(message) }
    }

    @Throws(Throwable::class)
    private suspend fun StartArgs.awaitProcessStart(paths: ResourceInstaller.Paths.Tor): Job {
        // Do not use current suspend function's job as parent, as we
        // want to intercept the cause w/o cancelling the current coroutine
        // as this will be utilized to propagate process start failures
        // before returning.
        val latch = Job()

        @OptIn(InternalKmpTorApi::class)
        val processJob = scope.launch {
            val p: Process = try {
                Process.Builder(command = paths.tor.path)
                    .args(cmdLine)
                    // TODO: Add to TorRuntime.Environment ability to pass
                    //  process environment arguments.
                    .environment("HOME", generator.environment.workDirectory.path)
                    .stdin(Stdio.Null)
                    .stdout(Stdio.Pipe)
                    .stderr(Stdio.Pipe)
                    .destroySignal(Signal.SIGTERM)
                    .spawn()
            } catch (e: IOException) {
                NOTIFIER.lce(Lifecycle.Event.OnDestroy(this))
                latch.completeExceptionally(e)
                return@launch
            }

            NOTIFIER.lce(Lifecycle.Event.OnStart(this@TorProcess))
            NOTIFIER.i(this@TorProcess, p.toString())

            var _lock: SynchronizedObject? = SynchronizedObject()
            var _stdout: ArrayList<String>? = ArrayList(30)

            p.stdoutFeed { line ->
                if (line == null) return@stdoutFeed

                _lock?.let { synchronized(it) { _stdout?.add(line) } }

                NOTIFIER.p(this@TorProcess, line)
            }.stderrFeed { line ->
                if (line == null) return@stderrFeed
                NOTIFIER.w(this@TorProcess, line)
            }

            try {
                // If tor fails to start, it will fail within the first
                // little bit. The process may not end though if, for
                // example, another process is running in some other
                // application instance. We want to await initial output
                // to check for a few things before closing the latch
                val exitCode = withContext(NonCancellable) {
                    // non-cancellable b/c we must close the latch
                    p.waitForAsync(250.milliseconds)
                }

                val (containsErr, stdout) = run {
                    val stdout = _stdout!!
                    val lock = _lock!!
                    _lock = null
                    _stdout = null
                    var containsErr = false

                    val out = synchronized(lock) {
                        buildString {
                            append("stdout: [")
                            if (stdout.isEmpty()) {
                                append(']')
                                return@buildString
                            }

                            for (line in stdout) {
                                if (line.contains(" [err] ")) {
                                    containsErr = true
                                }

                                appendLine()
                                append("    ")
                                append(line)
                            }

                            appendLine()
                            append(']')
                        }
                    }

                    containsErr to out
                }

                run {
                    if (exitCode != null || containsErr) {
                        latch.completeExceptionally(IOException(
                            "Process failure\n"
                            + if (exitCode != null) {
                                "exitCode: $exitCode\n"
                            } else {
                                ""
                            }
                            + stdout
                        ))
                        return@run
                    }

                    // Success
                    latch.cancel()

                    // Suspend until job cancellation or process stops
                    p.waitForAsync()
                }
            } finally {
                state.stopMark = TimeSource.Monotonic.markNow()
                p.destroy()
                NOTIFIER.lce(Lifecycle.Event.OnDestroy(this@TorProcess))
            }
        }

        state.processJob = processJob

        try {
            withContext(latch) {
                // withContext will always register as
                // active b/c latch is not using current
                // suspend function's job as its parent.
                //
                // If scope is cancelled, processJob
                // will launch with immediate cancellation,
                // and be unable to complete the latch job.
                // So, check it here first before calling join
                // on the latch.
                //
                // This is a limitation of coroutines and the
                // way CompletableJob.complete() functions,
                // requiring processStart to call cancel()
                // on the latch for success in order to pop
                // us out of the try/catch block here.
                processJob.ensureActive()
                latch.join()
            }
        } catch (e: CancellationException) {
            e // JobCancellationException
                .cause // JobCancellationException
                ?.cause // completeExceptionally
                ?.let { cause ->
                    if (cause is IOException) {
                        throw cause
                    }
                }

            // Process started successfully and
            // called cancel on latch. Ensure that
            // it is still active before continuing.
            processJob.ensureActive()
        } finally {
            latch.complete()
        }

        return processJob
    }

    @Throws(Throwable::class)
    private suspend fun TorConfig.awaitCtrlConnection(
        processJob: Job,
    ): CtrlArguments.Connection = try {
        // Is **always** present in generated config.
        val ctrlPortFile = filterByKeyword<TorConfig.ControlPortWriteToFile.Companion>()
            .first()
            .argument
            .toFile()

        val lines = ctrlPortFile
            .awaitRead(3.seconds)
            .decodeToString()
            .lines()
            .mapNotNull { it.ifBlank { null } }

        NOTIFIER.d(this@TorProcess, "ControlFile$lines")

        run {
            val addresses = ArrayList<String>(1)

            for (line in lines) {
                val argument = line.substringAfter('=', "")
                if (argument.isBlank()) continue

                // UNIX_PORT=/tmp/kmp_tor_ctrl/data/ctrl.sock
                if (line.startsWith("UNIX_PORT")) {
                    val file = argument.toFile()
                    if (!file.exists()) continue

                    // Prefer UnixDomainSocket if present
                    return@run CtrlArguments.Connection(file)
                }

                // PORT=127.0.0.1:9055
                if (line.startsWith("PORT")) {
                    addresses.add(argument)
                }
            }

            // No UnixDomainSocket, fallback to first TCP address
            for (address in addresses) {
                val proxyAddress = address.toProxyAddressOrNull() ?: continue
                return@run CtrlArguments.Connection(proxyAddress)
            }

            throw IOException("Failed to acquire control connection address from file[$ctrlPortFile]")
        }
    } catch (t: Throwable) {
        processJob.cancel()
        throw t
    }

    @Throws(Throwable::class)
    private suspend fun TorConfig.awaitAuthentication(
        processJob: Job,
    ): TorCmd.Authenticate = try {
        // TODO: HashedControlPassword Issue #1

        filterByKeyword<TorConfig.CookieAuthFile.Companion>()
            .firstOrNull()
            ?.argument
            ?.toFile()
            ?.awaitRead(1.seconds)
            ?.let { bytes -> TorCmd.Authenticate(cookie = bytes)  }

    } catch (t: Throwable) {
        processJob.cancel()
        throw t
    } ?: TorCmd.Authenticate() // Unauthenticated

    @Throws(CancellationException::class, IOException::class)
    private suspend fun File.awaitRead(timeout: Duration): ByteArray {
        require(timeout >= 500.milliseconds) { "timeout must be greater than or equal to 500ms" }

        val startMark = TimeSource.Monotonic.markNow()

        var notified = false
        while (true) {
            if (exists()) {

                val content = readBytes()
                if (content.isNotEmpty()) {
                    return content
                }
            }

            if (!notified) {
                notified = true
                NOTIFIER.d(this@TorProcess, "Waiting for tor to write to $name")
            }

            delay(25.milliseconds)
            state.ensureActive()
            if (startMark.elapsedNow() > timeout) break
        }

        throw IOException("Timed out after ${timeout.inWholeMilliseconds}ms waiting for tor to write to file[$this]")
    }

    public override fun toString(): String = toFIDString(includeHashCode = true)

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
            private val tcp: ProxyAddress?,
            private val uds: File?,
        ) {

            constructor(tcp: ProxyAddress): this(tcp, null)
            constructor(uds: File): this(null, uds)

            @Throws(CancellationException::class, IOException::class)
            internal suspend fun openWith(factory: TorCtrl.Factory): TorCtrl {
                uds?.let { return factory.connectAsync(it) }
                return factory.connectAsync(tcp!!)
            }
        }
    }

    init {
        NOTIFIER.lce(Lifecycle.Event.OnCreate(this))
    }

    internal companion object: InstanceKeeper<String, Any>() {

        @JvmSynthetic
        @Throws(Throwable::class)
        internal suspend fun <T: Any?> start(
            generator: TorConfigGenerator,
            NOTIFIER: RuntimeEvent.Notifier,
            scope: CoroutineScope,
            connect: suspend CtrlArguments.() -> T,
        ): T {
            val state = getOrCreateInstance(generator.environment.fid) { FIDState() }

            val process = TorProcess(
                generator,
                NOTIFIER,
                scope,
                state as FIDState,
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
    }
}
