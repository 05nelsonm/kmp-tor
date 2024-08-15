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
package io.matthewnelson.kmp.tor.runtime.ctrl

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.checkIsNotDestroyed
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.*
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

/**
 * Helper for TorRuntime to maintain a temporary queue for
 * successive [enqueue] invocations after start is called,
 * while [TorCtrl] is being configured with initial settings
 * and authentication.
 *
 * After [attach] is invoked, all enqueued calls are transferred
 * and any further calls to [enqueue] will be delegated to
 * [TorCtrl].
 *
 * @see [TorCtrl.Factory.tempQueue]
 * @suppress
 * */
@OptIn(InternalKmpTorApi::class)
public class TempTorCmdQueue private constructor(
    private val handler: UncaughtException.Handler,
): Destroyable, TorCmd.Unprivileged.Processor {

    @Volatile
    private var _connection: AbstractTorCtrl? = null
    @Volatile
    private var _destroyed = false
    private val lock = SynchronizedObject()
    private var queue = ArrayDeque<TorCmdJob<*>>(10)

    @get:JvmName("connection")
    public val connection: TorCtrl? get() = _connection

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    public fun attach(connection: TorCtrl) {
        checkIsNotDestroyed()
        require(connection is AbstractTorCtrl) { "TorCtrl must implement ${AbstractTorCtrl::class.simpleName}" }

        synchronized(lock) {
            check(_connection == null) { "$_connection is already attached" }
            checkIsNotDestroyed()
            connection.transferAllUnprivileged(queue)
            _connection = connection
            queue = ArrayDeque(0)
        }

        connection.invokeOnDestroy { destroy() }
    }

    public override fun isDestroyed(): Boolean = _connection?.isDestroyed() ?: _destroyed

    // @Throws(UncaughtException::class)
    public override fun destroy() {
        if (_destroyed) return

        val interrupt = synchronized(lock) {
            if (_destroyed) return@synchronized false
            _destroyed = true
            true
        }

        if (!interrupt) return
        queue.interruptAndClearAll(message = "${this::class.simpleName}.onDestroy", handler)
        queue = ArrayDeque(0)
    }

    public override fun <Success: Any> enqueue(
        cmd: TorCmd.Unprivileged<Success>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Success>,
    ): EnqueuedJob {
        _connection
            ?.enqueue(cmd, onFailure, onSuccess)
            ?.let { return it }

        var job: EnqueuedJob? = null

        val instance: TorCtrl? = synchronized(lock) {
            _connection?.let { return@synchronized it }

            if (!isDestroyed()) {
                val nonNullJob = TorCmdJob.of(cmd, onSuccess, onFailure, handler)
                queue.add(nonNullJob)
                job = nonNullJob
            }

            null
        }

        if (instance != null) {
            job = instance.enqueue(cmd, onFailure, onSuccess)
        }

        return job ?: cmd.toDestroyedErrorJob(onFailure, handler)
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            handler: UncaughtException.Handler
        ): TempTorCmdQueue = TempTorCmdQueue(handler)
    }
}
