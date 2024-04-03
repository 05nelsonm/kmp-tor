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
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.Debugger.Companion.d
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmSynthetic

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
        CoroutineName(toString())
        + SupervisorJob()
        + dispatcher
    )

    @Volatile
    private var _isDisconnected = false
    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()

    private val parser = object : CtrlConnection.Parser() {
        internal override fun parse(line: String?) {
            if (line == null) {
                LOG.d(this@RealTorCtrl) { "End Of Stream" }
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
            // TODO: Waiters
        }
    }

    override fun destroy() {
        if (_isDisconnected) return

        @OptIn(InternalKmpTorApi::class)
        val disconnect = synchronized(lock) {
            if (_isDisconnected) return@synchronized false
            _isDisconnected = true
            true
        }

        if (!disconnect) return

        connection.close()
        LOG.d(this) { "Connection Closed" }
    }

    override fun startProcessor() {
        // TODO
    }

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
