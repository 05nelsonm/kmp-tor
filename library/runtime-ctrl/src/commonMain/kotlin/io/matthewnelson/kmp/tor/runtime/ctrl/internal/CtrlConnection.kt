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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.encoding.core.util.wipe
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import kotlin.coroutines.cancellation.CancellationException

internal interface CtrlConnection: Closeable {

    val isReading: Boolean

    @Throws(CancellationException::class, IllegalStateException::class)
    suspend fun startRead(parser: Parser)

    @Throws(CancellationException::class, IOException::class)
    suspend fun write(command: ByteArray)

    /**
     * [Asynchronous Events](https://torproject.gitlab.io/torspec/control-spec/#asynchronous-events)
     * */
    abstract class Parser internal constructor() {

        private var job: Job? = null

        internal open fun parse(line: String?) {
            if (line == null) {
                // Clean shutdown
                val job = job ?: return

                // Dirty shutdown, but acceptable because
                // it was not for a QueuedJob
                if (job.event != null) return

                if (job.multi != null || job.replies.isNotEmpty()) {
                    onError("Stream ended mid-response")
                }

                return
            }

            val job = job ?: try {
                Job(line)
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Job.init threw exception")
                return
            }

            // New job. Line was consumed via Job.init block
            if (this.job == null) {
                if (job.delim == ' ') {
                    job.dispatch()
                } else {
                    // Not done yet. More lines to come
                    this.job = job
                }

                return
            }

            // Working on a building a multi-line reply
            job.multi?.let { multi ->

                // End
                if (line == ".") {
                    job.replies.add(multi.build())
                    job.multi = null
                    return
                }

                multi.append(line)
                return
            }

            // Not a Multi-Line reply
            if (line.length < 4) {
                onError("line.length < 4")
                return
            }

            with(job) {
                delim = line[3]
                val message = line.substring(4)
                val status = line.substring(0, 3)

                multi = if (delim == '+') Multi(status, message) else null

                if (multi != null) return@with

                if (event != null && delim == ' ' && message == "OK") {
                    // Disregard final OK message for multi-line
                    // replies that are TorEvent
                    return@with
                }

                replies.add(Reply.of(status, message))
            }

            if (job.delim != ' ') return
            job.dispatch()
        }

        protected abstract fun ArrayList<Reply>.respond()

        protected abstract fun TorEvent.notify(data: String)

        protected abstract fun onError(details: String)

        private fun Job.dispatch() {
            check(delim == ' ') { "invalid delimiter[$delim]" }
            check(replies.isNotEmpty()) { "replies were empty" }
            check(multi == null) { "multi != null" }

            // Reset and prepare for next incoming job
            this@Parser.job = null

            if (event == null) {
                replies.respond()
                return
            }

            if (replies.size == 1) {
                event.notify(replies.first().message)
                return
            }

            // So, have an event with multiple reply lines.
            // Need to combine into single output like we
            // received it off the socket.
            //
            // Note, this does not affect Multi-Line TorEvent
            // because we disregard the final "650 OK", and
            // those are already built into a single output.
            //
            // Having multiple Reply lines here is a result
            // of something like a CONF_CHANGED which started
            // with a delimiter of '-' to indicate multiple
            // configuration changes were made.
            val sb = run {
                var capacity = replies.size - 1 // new line characters
                replies.forEach { capacity += it.message.length }
                StringBuilder(capacity)
            }
            sb.append(replies.first().message)

            for (i in 1 until replies.size) {
                sb.appendLine().append(replies[i].message)
            }

            val data = sb.toString()
            sb.wipe()
            event.notify(data)
        }

        private class Job(line: String) {

            var delim: Char
            val event: TorEvent?
            val replies = ArrayList<Reply>(1)
            var multi: Multi?

            init {
                require(line.length >= 4) { "line.length < 4" }

                delim = line[3]
                var message = line.substring(4)
                val status = line.substring(0, 3)

                event = if (status.startsWith('6')) {
                    // Asynchronous Reply
                    val index = message.indexOf(' ')

                    // Trim event name from message
                    val name = if (index == -1) {
                        // Entire message is the event name
                        val n = message
                        message = ""
                        n
                    } else {
                        val n = message.substring(0, index)
                        message = message.substring(index + 1)
                        n
                    }

                    TorEvent.valueOf(name)
                } else {
                    null
                }

                multi = if (delim == '+') Multi(status, message) else null

                // message
                run {
                    if (message.isEmpty()) return@run
                    if (multi != null) return@run

                    replies.add(Reply.of(status, message))
                }
            }
        }

        private class Multi private constructor(
            val status: String,
            message: String,
            private val sb: StringBuilder,
        ) {

            constructor(status: String, message: String): this(status, message, StringBuilder())

            // Message that came in with event could potentially
            // be a key value pair of a response (e.g. 250+/some/thing=)
            //
            // If this is the first Multi of the Job that is a
            // Multi-Line TorEvent response, then message will always
            // be empty as Job consumes it when resolving the Enum.
            private var maybeKVP: String? = message.ifBlank { null }

            fun append(line: String) {
                val maybeKVP = maybeKVP
                if (maybeKVP != null) {
                    this.maybeKVP = null
                    sb.append(maybeKVP)

                    // Not a Multi-Line KVP
                    if (!maybeKVP.endsWith('=')) {
                        sb.appendLine()
                    }

                    sb.append(line)
                    return
                }

                if (sb.isNotEmpty()) sb.appendLine()
                sb.append(line)
            }

            fun build(): Reply {
                val message = sb.toString()
                sb.wipe()
                return Reply.of(status, message)
            }
        }

        final override fun toString(): String = "CtrlConnection.Parser@${hashCode()}"
    }
}
