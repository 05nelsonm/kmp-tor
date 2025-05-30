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

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.common.core.synchronizedObject
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.checkIsNotDestroyed
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression2
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.AbstractTorEventProcessor
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl.Debugger
import kotlin.collections.removeFirst as kRemoveFirst
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmSynthetic

@OptIn(InternalKmpTorApi::class)
internal abstract class AbstractTorCmdQueue internal constructor(
    staticTag: String?,
    observers: Set<TorEvent.Observer>,
    defaultExecutor: OnEvent.Executor,
    handler: UncaughtException.Handler,
):  AbstractTorEventProcessor(staticTag, observers, defaultExecutor),
    Destroyable,
    TorCmd.Privileged.Processor
{

    private val lock = synchronizedObject()
    private val queueInterrupt: ArrayDeque<ItBlock<UncaughtException.Handler>> = ArrayDeque(1)
    private val queueExecute: ArrayDeque<TorCmdJob<*>> = ArrayDeque(64)
    @Volatile
    @Suppress("PropertyName")
    protected open var LOG: Debugger? = null
    protected final override val handler: HandlerWithContext = HandlerWithContext.of(handler)

    public final override fun isDestroyed(): Boolean = destroyed

    public final override fun <Success: Any> enqueue(
        cmd: TorCmd.Privileged<Success>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Success>,
    ): EnqueuedJob = enqueueImpl(cmd, onFailure, onSuccess)

    public final override fun <Success: Any> enqueue(
        cmd: TorCmd.Unprivileged<Success>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Success>,
    ): EnqueuedJob = enqueueImpl(cmd, onFailure, onSuccess)

    @JvmSynthetic
    @Throws(IllegalStateException::class)
    internal fun transferAllUnprivileged(queue: ArrayDeque<TorCmdJob<*>>) {
        checkIsNotDestroyed()
        if (queue.isEmpty()) return

        var start = false

        synchronized(lock) {
            checkIsNotDestroyed()

            val i = queue.iterator()
            while (i.hasNext()) {
                val job = i.next()
                if (job.cmd is TorCmd.Privileged) continue

                i.remove()
                queueExecute.add(job)
                start = true
            }
        }

        if (!start) return
        startProcessor()
    }

    private fun <Success: Any> enqueueImpl(
        cmd: TorCmd<Success>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Success>
    ): EnqueuedJob {
        if (isDestroyed()) {
            return cmd.toDestroyedErrorJob(onFailure, handler)
        }

        val job = synchronized(lock) {
            if (isDestroyed()) return@synchronized null

            when (cmd) {
                is TorCmd.Signal.Shutdown -> "SHUTDOWN"
                is TorCmd.Signal.Halt -> "HALT"
                else -> null
            }?.let { signal ->
                if (queueExecute.isEmpty()) return@let

                val interrupts = ArrayDeque(queueExecute)

                queueInterrupt.add(ItBlock { handler ->
                    interrupts.interruptAndClearAll(message = "${cmd.keyword} $signal", handler)
                })

                queueExecute.clear()
            }

            TorCmdJob.of(cmd, onSuccess, onFailure, handler).also { queueExecute.add(it) }
        }

        if (job != null) startProcessor()

        return job ?: cmd.toDestroyedErrorJob(onFailure, handler)
    }

    protected abstract fun startProcessor()

    protected fun dequeueNextOrNull(): TorCmdJob<*>? {
        if (isDestroyed()) return null
        doCancellations(handler)

        if (isDestroyed()) return null
        val job = synchronized(lock) {
            var job: TorCmdJob<*>? = null
            while (!isDestroyed() && queueExecute.isNotEmpty()) {
                job = queueExecute.kRemoveFirst()

                try {
                    job.executing()
                    break
                } catch (_: IllegalStateException) {
                    job = null
                }
            }
            job
        }

        if (job != null) LOG.d { "$job" }

        return job
    }

    // @Throws(UncaughtException::class)
    protected override fun onDestroy(): Boolean {
        val wasDestroyed = super.onDestroy()

        if (wasDestroyed) {
            handler.withSuppression2 {
                doCancellations(this)

                @OptIn(InternalKmpTorApi::class)
                synchronized(lock) {
                    if (queueExecute.isEmpty()) return@synchronized null

                    // dequeueNextOrNull could potentially be executing,
                    // so just to be on the safe side, copy over all
                    // before cancelling them.
                    ArrayDeque(queueExecute).also { queueExecute.clear() }
                }?.also {
                    LOG.d { "Interrupting EnqueuedJobs" }
                }?.interruptAndClearAll(message = "${this::class.simpleName}.onDestroy", this)
            }
        }

        return wasDestroyed
    }

    private fun doCancellations(handler: UncaughtException.Handler) {
        val interrupts = synchronized(lock) {
            if (queueInterrupt.isEmpty()) return@synchronized null
            ArrayDeque(queueInterrupt).also { queueInterrupt.clear() }
        } ?: return

        LOG.d { "Cancelling EnqueuedJobs" }
        handler.withSuppression2 {
            val suppressed = this

            while (interrupts.isNotEmpty()) {
                val interrupt = interrupts.kRemoveFirst()
                interrupt(suppressed)
            }
        }
    }

    public final override fun unsubscribeAll(tag: String) {
        super.unsubscribeAll(tag)
    }

    public final override fun clearObservers() {
        super.clearObservers()
    }

    protected final override fun registered(): Int {
        return super.registered()
    }

    private val name by lazy { this::class.simpleName ?: "TorCtrl" }

    final override fun toString(): String = "$name@${hashCode()}"
}
