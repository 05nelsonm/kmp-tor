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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.checkDestroy
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.ctrl.AbstractTorEventProcessor
import kotlin.coroutines.cancellation.CancellationException

@OptIn(InternalKmpTorApi::class)
internal abstract class AbstractTorCmdQueue internal constructor(
    staticTag: String?,
    initialObservers: Set<TorEvent.Observer>,
    protected final override val handler: UncaughtException.Handler,
):  AbstractTorEventProcessor(staticTag, initialObservers),
    Destroyable,
    TorCmd.Privileged.Processor
{

    private val lock = SynchronizedObject()
    private val queueCancellation = ArrayList<ItBlock<UncaughtException.Handler>>(1)
    private val queueExecute = ArrayList<TorCmdJob<*>>(1)

    public final override fun isDestroyed(): Boolean = destroyed

    @Throws(IllegalStateException::class)
    public final override fun <Response : Any> enqueue(
        cmd: TorCmd.Privileged<Response>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Response>,
    ): TorCmdJob<Response> = enqueueImpl(cmd, onFailure, onSuccess)

    @Throws(IllegalStateException::class)
    public final override fun <Response : Any> enqueue(
        cmd: TorCmd.Unprivileged<Response>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Response>,
    ): TorCmdJob<Response> = enqueueImpl(cmd, onFailure, onSuccess)

    @Throws(IllegalStateException::class)
    private fun <Response: Any> enqueueImpl(
        cmd: TorCmd<Response>,
        onFailure: OnFailure,
        onSuccess: OnSuccess<Response>
    ): TorCmdJob<Response> {
        checkDestroy()

        val job = synchronized(lock) {
            checkDestroy()

            when (cmd) {
                is TorCmd.Signal.Shutdown -> "SHUTDOWN"
                is TorCmd.Signal.Halt -> "HALT"
                else -> null
            }?.let { signal ->
                if (queueExecute.isEmpty()) return@let

                val cancellations = ArrayList(queueExecute)
                val cause = CancellationException("${cmd.keyword} $signal")

                queueCancellation.add(ItBlock { handler ->
                    cancellations.cancelAndClearAll(cause, handler)
                })

                queueExecute.clear()
            }

            TorCmdJob.of(cmd, onSuccess, onFailure, handler).also { queueExecute.add(it) }
        }

        startProcessor()
        return job
    }

    protected abstract fun startProcessor()

    protected fun dequeueNextOrNull(
        handler: UncaughtException.Handler = this.handler,
    ): TorCmdJob<*>? {
        if (isDestroyed()) return null
        doCancellations(handler)

        if (isDestroyed()) return null
        return synchronized(lock) {
            var job: TorCmdJob<*>? = null
            while (!isDestroyed() && queueExecute.isNotEmpty()) {
                job = queueExecute.removeFirst()

                try {
                    job.executing()
                    break
                } catch (_: IllegalStateException) {
                    job = null
                }
            }
            job
        }
    }

    // @Throws(UncaughtException::class)
    protected override fun onDestroy(): Boolean {
        val wasDestroyed = super.onDestroy()

        if (wasDestroyed) {
            val context = "${this::class.simpleName ?: "AbstractTorCmdQueue"}.onDestroy"
            val cause = CancellationException(context)

            handler.withSuppression {
                doCancellations(this)

                @OptIn(InternalKmpTorApi::class)
                synchronized(lock) {
                    if (queueExecute.isEmpty()) return@synchronized null

                    // dequeueNextOrNull could potentially be executing,
                    // so just to be on the safe side, copy over all
                    // before cancelling them.
                    ArrayList(queueExecute).also { queueExecute.clear() }
                }?.cancelAndClearAll(cause, this)
            }
        }

        return wasDestroyed
    }

    private fun doCancellations(handler: UncaughtException.Handler) {
        val cancellations = synchronized(lock) {
            if (queueCancellation.isEmpty()) return@synchronized null
            ArrayList(queueCancellation).also { queueCancellation.clear() }
        } ?: return

        handler.withSuppression {
            while (cancellations.isNotEmpty()) {
                val cancellation = cancellations.removeFirst()
                cancellation(this)
            }
        }
    }

    public final override fun removeAll(tag: String) {
        super.removeAll(tag)
    }

    public final override fun clearObservers() {
        super.clearObservers()
    }

    protected final override fun registered(): Int {
        return super.registered()
    }

    internal companion object {

        // Should only be invoked from OUTSIDE a lock lambda, and on
        // a local instance of MutableList containing the jobs to cancel.
        // as to not encounter ConcurrentModificationException.
        @Throws(ConcurrentModificationException::class)
        internal fun <T: QueuedJob> MutableList<T>.cancelAndClearAll(
            cause: CancellationException?,
            handler: UncaughtException.Handler,
        ) {
            handler.withSuppression {
                while (isNotEmpty()) {
                    val job = removeFirst()
                    tryCatch(job) { job.cancel(cause) }
                }
            }
        }
    }
}