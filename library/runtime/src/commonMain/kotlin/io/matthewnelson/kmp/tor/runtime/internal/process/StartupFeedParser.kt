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
package io.matthewnelson.kmp.tor.runtime.internal.process

import io.matthewnelson.kmp.process.OutputFeed
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import kotlin.concurrent.Volatile

@OptIn(InternalKmpTorApi::class)
internal class StartupFeedParser(private val lineLimit: Int = 50, private val exitCodeOrNull: () -> Int?) {

    @Volatile
    private var _isReady: Boolean = false
    @Volatile
    private var _lines = 0
    @Volatile
    private var _error: ProcessStartException? = null
    @Volatile
    private var _stdout: StringBuilder? = StringBuilder()
    @Volatile
    private var _stderr: StringBuilder? = StringBuilder()

    private val lock = SynchronizedObject()

    internal val isClosed: Boolean get() = _stdout == null
    internal val isReady: Boolean get() = _isReady

    internal val stderr = OutputFeed { line ->
        val stderr = _stderr ?: return@OutputFeed
        if (line == null) return@OutputFeed
        stderr.appendLine(line, generateError = false)
    }

    internal val stdout = OutputFeed { line ->
        val sb = _stdout ?: return@OutputFeed

        if (_lines >= lineLimit) {
            if (!_isReady && _error == null) {
                // Feed is not closed, nor has tor output
                // control connection info within the first
                // {lineLimit} lines... something is wrong.
                @Suppress("ThrowableNotThrown")
                createError("Process has output $lineLimit lines without informing us of a control listener yet")
            }
            return@OutputFeed
        }

        _lines++

        if (line == null) {
            @Suppress("ThrowableNotThrown")
            createError("Process exited unexpectedly")
            return@OutputFeed
        }

        if (!_isReady) {
            if (line.contains(CHECK_READY, ignoreCase = true)) {
                _isReady = true
            }
        }

        sb.appendLine(
            line,
            generateError =
                line.contains(CHECK_ERR, ignoreCase = true)
                || line.contains(CHECK_ANOTHER, ignoreCase = true)
        )
    }

    fun close() {
        if (_stdout == null && _stderr == null) return

        synchronized(lock) {
            val stdout = _stdout ?: return
            val stderr = _stderr ?: return
            _stdout = null
            _stderr = null

            listOf(stdout, stderr).forEach { sb ->
                val count = sb.count()
                sb.clear()
                repeat(count) { sb.append(' ') }
            }
        }
    }

    @Throws(ProcessStartException::class)
    fun checkError() { _error?.let { t -> throw t } }

    fun createError(message: String): ProcessStartException {
        return NULL.appendLine(message, generateError = true)!!
    }

    private fun StringBuilder?.appendLine(
        line: String,
        generateError: Boolean
    ): ProcessStartException? = synchronized(lock) {
        if (!generateError) {
            if (this != null) {
                if (isNotEmpty()) appendLine()
                append(line)
            }
            return@synchronized null
        }

        // Generate error
        val stdout = _stdout?.toString() ?: ""
        val stderr = _stderr?.toString() ?: ""
        val exitCode = exitCodeOrNull()?.toString() ?: "not exited"

        // append to current StringBuilder after calling
        // toString on both (above) so that if another
        // error is generated, that line will be present
        if (this != null) {
            if (isNotEmpty()) appendLine()
            append(line)
        }

        val message = StringBuilder(stdout.length + stderr.length + 100)
            .appendLine("Process Failure: [")
            .append("    exitCode: ")
            .appendLine(exitCode)
            .append("    cause: ")
            .appendLine(line)
            .append("    stdout: [")
            .appendMultiLine(stdout)
            .append("    stderr: [")
            .appendMultiLine(stderr)
            .append(']')
            .toString()

        ProcessStartException(message).also { _error = it }
    }

    private fun StringBuilder.appendMultiLine(lines: String): StringBuilder {
        if (lines.isBlank()) {
            appendLine(']')
            return this
        }

        for (l in lines.lines()) {
            appendLine()
            append("        ")
            append(l)
        }

        appendLine()
        appendLine("    ]")
        return this
    }

    private companion object {
        private const val CHECK_ANOTHER = " [warn] It looks like another Tor process is running with the same data directory."
        private const val CHECK_ERR = " [err] "
        private const val CHECK_READY = " [notice] Opened Control listener connection (ready) on "

        private val NULL: StringBuilder? = null
    }
}
