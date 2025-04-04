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
@file:Suppress("LocalVariableName")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch2
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression2
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCmdInterceptor
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@OptIn(InternalKmpTorApi::class)
internal class RealTorCtrl private constructor(
    factory: TorCtrl.Factory,
    dispatcher: CoroutineDispatcher,
    private val connection: CtrlConnection,
    private val closeDispatcher: ((LOG: TorCtrl.Debugger?) -> Unit)?,
): AbstractTorCtrl(
    factory.staticTag,
    factory.observers,
    factory.defaultExecutor,
    if (factory.handler is HandlerWithContext) {
        factory.handler
    } else {
        CloseableExceptionHandler(factory.handler)
    },
) {

    @Volatile
    protected override var LOG = factory.debug?.wrap(prefix = this)

    private val scope = CoroutineScope(context =
        CoroutineName(toString())
        + SupervisorJob()
        + dispatcher
    )

    @Volatile
    private var _isDisconnected = false
    @Volatile
    private var _closeException: IOException? = null
    private val lock = synchronizedObject()

    private val waiters = Waiters { LOG }
    private val processor = Processor(factory.interceptors)

    // TorCtrl.Factory.connect waits for this to
    // turn true before returning the instance
    internal val isReady: Boolean get() {
        if (isDestroyed()) return true
        return connection.isReading
    }

    private val parser = object : CtrlConnection.Parser() {
        internal override fun parse(line: String?) {
            if (line == null) {
                LOG.d { "End Of Stream" }
                waiters.destroy()
            } else {
                LOG.d { line.toLogLine() }
            }

            super.parse(line)
        }

        override fun onError(details: String) {
            // TODO
            LOG.d { details }
        }

        override fun TorEvent.notify(data: String) {
            notifyObservers(data)
        }

        override fun ArrayList<Reply>.respond() {
            waiters.respondNext(this)
        }

        private fun String.toLogLine(): String {
            var log = this

            when {
                // ADD_ONION NEW:ED25519-V3
                // 250-ServiceID=...
                // 250-PrivateKey=ED25519-V3:[REDACTED]
                log.startsWith("250-PrivateKey=") -> {
                    log = log.replaceAfter(':', "[REDACTED]")
                }

                // 250-CLIENT {onion address} x25519:[REDACTED]
                // 250-CLIENT {onion address} x25519:[REDACTED] Flags=...
                log.startsWith("250-CLIENT ") -> {
                    val iKey = log.indexOf(':')

                    if (iKey != -1) {
                        var iKeyEnd = iKey

                        while (iKeyEnd < log.length) {
                            if (log[iKeyEnd].isWhitespace()) break
                            iKeyEnd++
                        }

                        if (iKey != iKeyEnd) {
                            log = log.replaceRange(iKey + 1, iKeyEnd, "[REDACTED]")
                        }
                    }
                }
            }

            return "<< $log"
        }
    }

    override fun destroy() {
        if (_isDisconnected) return

        val disconnect = synchronized(lock) {
            if (_isDisconnected) return@synchronized false
            _isDisconnected = true
            true
        }

        if (!disconnect) return

        try {
            connection.close()
        } catch (e: IOException) {
            _closeException = e
        }

        LOG.d { "Connection Closed" }
    }

    override fun startProcessor() { processor.start() }

    // @Throws(UncaughtException::class)
    protected override fun onDestroy(): Boolean {
        // Already destroyed
        if (!scope.isActive) return false

        // ensure connection.close is called
        destroy()

        // Waiters must be destroyed BEFORE cancelling
        // scope (if they haven't already been b/c of EOS).
        // This is to ensure that all currently waiting
        // replies get cancelled, and no more EnqueuedJob
        // will be executed.
        waiters.destroy()

        scope.cancel()
        LOG.d { "Scope Cancelled" }

        var threw: Throwable? = null

        try {
            handler.withSuppression2 {
                val context = "RealTorCtrl.onDestroy"

                tryCatch2(context) { super.onDestroy() }

                _closeException?.let { t -> tryCatch2(context) { throw t } }
            }
        } catch (t: Throwable) {
            threw = t
        }

        val closeableHandler = handler.delegate as? CloseableExceptionHandler
        closeableHandler?.close()

        closeDispatcher?.let { callback ->
            val _LOG = if (closeableHandler == null) {
                // factory.handler was HandlerWithContext, so it is
                // coming from TorRuntime implementation. Allow logging
                // past destruction.
                LOG
            } else {
                null
            }

            LOG.d { "Closing Dispatchers" }
            callback.invoke(_LOG)
        }

        LOG = null

        // Will crash things if the provided TorCtrl.Factory.handler
        // threw the UncaughtException instead of dealing with it.
        threw?.let { throw it }

        return true
    }

    init {
        scope.launch {
            LOG.d { "Starting Read" }
            val start = TimeSource.Monotonic.markNow()

            try {
                connection.startRead(parser)
            } catch (_: IllegalStateException) {
                // Can happen if destroy is called immediately
                // after connect returns the instance (before
                // the coroutine lambda actually executes in bg
                // thread).
                //
                // If so, need to ensure parser's EOS gets triggered.
                parser.parse(null)
            }

            // Ensure there is a minimum of 50ms of runtime before
            // stopping coroutine, thus invoking onCompletion, thus
            // invoking onDestroy
            delay(50.milliseconds - start.elapsedNow())
        }.invokeOnCompletion {
            LOG.d { "Stopped Reading" }
            onDestroy()
        }
    }

    private inner class Processor(private val interceptors: Set<TorCmdInterceptor<*>>) {

        @Volatile
        private var processorJob: Job? = null
        private val processorLock = synchronizedObject()

        fun start() {
            synchronized(processorLock) {
                if (waiters.isDestroyed()) return@synchronized
                if (processorJob?.isActive == true) return@synchronized

                processorJob = scope.launch { loop() }
            }
        }

        private suspend fun loop() {
            LOG.d { "Processor Started" }

            while (!waiters.isDestroyed()) {
                yield()

                val cmdJob = synchronized(processorLock) {
                    if (waiters.isDestroyed()) return@synchronized null

                    val next = dequeueNextOrNull()
                    if (next == null) processorJob = null
                    next
                }

                if (cmdJob == null) break

                val command = try {
                    var intercepted: TorCmd<*>? = null

                    for (interceptor in interceptors) {
                        val result = interceptor.invoke(cmdJob) ?: continue
                        intercepted = result
                    }

                    (intercepted ?: cmdJob.cmd).encodeToByteArray(LOG)
                } catch (t: Throwable) {
                    cmdJob.error(t)
                    continue
                }

                val wait = try {
                    waiters.create(writeCmd = {
                        connection.write(command)
                    })
                } catch (t: Throwable) {
                    var e: Throwable = t
                    // CancellationException or destroyed
                    if (t is IllegalStateException) {
                        e = InterruptedException(t.message)
                    }
                    cmdJob.error(e)
                    continue
                } finally {
                    command.fill(0)
                }

                cmdJob.awaitReplies(wait)
            }
        }

        /**
         * As described in the spec, "Servers respond to messages
         * in the order messages are received". See
         * [Protocol outline](https://torproject.gitlab.io/torspec/control-spec.html#protocol-outline).
         *
         * This allows for moving the wait functionality to a separate
         * coroutine so that more queued jobs can be executed while
         * this one finishes up.
         * */
        private fun TorCmdJob<*>.awaitReplies(wait: Waiters.Wait) {
            val cmdJob = this

            scope.launch {
                val replies = try {
                    wait.invoke()
                } catch (e: CancellationException) {
                    // scope was cancelled while waiting
                    cmdJob.error(InterruptedException("CtrlConnection Stream Ended"))
                    return@launch
                }

                try {
                    cmdJob.respond(replies)
                } catch (e: Exception) {
                    cmdJob.error(e)
                }
            }.invokeOnCompletion { t ->
                if (!cmdJob.isActive || cmdJob.isCompleting) return@invokeOnCompletion

                // Will only occur if scope has been cancelled
                // and the coroutine never fired. This ensures
                // that the job will always be completed.
                val e = if (t is InterruptedException) {
                    t
                } else {
                    InterruptedException("CtrlConnection Stream Ended")
                }

                cmdJob.error(e)
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            factory: TorCtrl.Factory,
            dispatcher: CoroutineDispatcher,
            connection: CtrlConnection,
            closeDispatcher: ((LOG: TorCtrl.Debugger?) -> Unit)?,
        ): RealTorCtrl = RealTorCtrl(
            factory,
            dispatcher,
            connection,
            closeDispatcher,
        )
    }
}
