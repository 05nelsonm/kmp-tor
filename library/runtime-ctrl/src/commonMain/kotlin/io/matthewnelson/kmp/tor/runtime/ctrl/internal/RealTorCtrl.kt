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

import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.Debugger.Companion.d
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic

@OptIn(InternalKmpTorApi::class)
internal class RealTorCtrl private constructor(
    factory: TorCtrl.Factory,
    dispatcher: CoroutineDispatcher,
    private val connection: CtrlConnection,
): AbstractTorCtrl(
    factory.staticTag,
    factory.initialObservers,
    factory.handler,
) {

    @Volatile
    protected override var LOG = factory.debugger?.let { Debugger(it) }

    private val scope = CoroutineScope(context =
        // TODO: Handler?
        CoroutineName(toString())
        + SupervisorJob()
        + dispatcher
    )

    @Volatile
    private var _isDisconnected = false
    private val lock = SynchronizedObject()

    private val waiters = Waiters()
    private val processor = Processor()

    private val parser = object : CtrlConnection.Parser() {
        internal override fun parse(line: String?) {
            if (line == null) {
                LOG.d(this@RealTorCtrl) { "End Of Stream" }
                waiters.destroy()
            } else {
                LOG.d(null) { "<< $line" }
            }

            super.parse(line)
        }

        override fun onError(details: String) {
            // TODO
            LOG.d(this) { details }
        }

        override fun TorEvent.notify(output: String) {
            notifyObservers(output)
        }

        override fun ArrayList<Reply>.respond() {
            waiters.respondNext(this)
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

        connection.close()
        LOG.d(this) { "Connection Closed" }
    }

    override fun startProcessor() { processor.start() }

    // @Throws(UncaughtException::class)
    protected override fun onDestroy(): Boolean {
        try {
            // ensure connection.close is called
            destroy()
        } catch (_: IOException) {
            // TODO: do better
        }

        scope.cancel()
        LOG.d(this) { "Scope Cancelled" }

        waiters.destroy()

        val wasDestroyed = try {
            // May throw UncaughtException if handler is
            // UncaughtException.Handler.THROW
            super.onDestroy()
        } finally {
            LOG = null
        }

        return wasDestroyed
    }

    init {
        scope.launch {
            LOG.d(this@RealTorCtrl) { "Starting Read" }
            connection.startRead(parser)
        }.invokeOnCompletion {
            LOG.d(this) { "Stopped Reading" }
            onDestroy()
        }
    }

    private inner class Processor {

        @Volatile
        private var processorJob: Job? = null
        private val processorLock = SynchronizedObject()

        fun start() {
            synchronized(processorLock) {
                if (waiters.isDestroyed()) return@synchronized
                if (processorJob?.isActive == true) return@synchronized

                processorJob = scope.launch { loop() }
            }
        }

        private suspend fun CoroutineScope.loop() {
            LOG.d(this@RealTorCtrl) { "Processor Started" }

            while (isActive && !waiters.isDestroyed()) {
                val cmdJob = synchronized(processorLock) {
                    if (waiters.isDestroyed()) return@synchronized null

                    val next = dequeueNextOrNull()
                    if (next == null) processorJob = null
                    next
                }

                if (cmdJob == null) break

                val command = try {
                    cmdJob.cmd.encodeToByteArray(LOG)
                } catch (e: NotImplementedError) {
                    // TODO
                    cmdJob.error(e)
                    continue
                } catch (e: IllegalArgumentException) {
                    cmdJob.error(e)
                    continue
                }

                val replies = try {
                    waiters.wait(write = {
                        connection.write(command)
                    })
                } catch (t: Throwable) {
                    var e: Throwable = t
                    if (t is IllegalStateException && waiters.isDestroyed()) {
                        e = CancellationException(t)
                    }
                    cmdJob.error(e)
                    continue
                } finally {
                    command.fill(0)
                }

                try {
                    cmdJob.respond(replies)
                } catch (e: NotImplementedError) {
                    // TODO
                    cmdJob.error(e)
                    continue
                }
            }
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            factory: TorCtrl.Factory,
            dispatcher: CoroutineDispatcher,
            connection: CtrlConnection,
        ): RealTorCtrl = RealTorCtrl(
            factory,
            dispatcher,
            connection,
        )
    }
}
