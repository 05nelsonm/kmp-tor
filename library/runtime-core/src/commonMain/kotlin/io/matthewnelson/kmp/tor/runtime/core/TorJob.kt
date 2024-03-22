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
package io.matthewnelson.kmp.tor.runtime.core

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.TorJob.State.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName

/**
 * Base abstraction for single-use model that tracks the state
 * of a queue-able job. Once completed, either successfully or
 * by cancellation/error, the [TorJob] is dead and should be
 * discarded.
 * */
public abstract class TorJob
@InternalKmpTorApi
public constructor(
    @JvmField
    public val name: String,
    @Volatile
    private var onFailure: ItBlock<Throwable>?,
) {

    @Volatile
    private var _state: State = Enqueued
    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()

    /**
     * The current [State] of the job
     * */
    @get:JvmName("state")
    public val state: State get() = _state

    @get:JvmName("isActive")
    public val isActive: Boolean get() = when (state) {
        Cancelled,
        Completed,
        Error -> false
        Enqueued,
        Executing -> true
    }

    public enum class State {

        /**
         * The initial state of a call
         * */
        Enqueued,

        /**
         * Point of no return where the job has been
         * de-queued and is being executed.
         * */
        Executing,

        /**
         * If the call completed by cancellation.
         * */
        Cancelled,

        /**
         * If the call completed successfully.
         * */
        Completed,

        /**
         * If the call completed by error.
         * */
        Error,
    }

    /**
     * Cancels the job.
     *
     * Does nothing if [state] is anything other than [State.Enqueued].
     *
     * If cancelled, [onFailure] will invoke with [CancellationException].
     * */
    public fun cancel(cause: Throwable?) {
        if (state != Enqueued) return

        val onFailure = onFailure

        @OptIn(InternalKmpTorApi::class)
        val notify = synchronized(lock) {
            if (state != Enqueued) return@synchronized false
            _state = Cancelled
            this.onFailure = null
            true
        }

        if (!notify) return

        try {
            onCancellation(cause)
        } finally {
            if (onFailure != null) {
                val e = if (cause is CancellationException) {
                    cause
                } else {
                    CancellationException(toString(), cause)
                }

                onFailure(e)
            }
        }
    }

    protected abstract fun onCancellation(cause: Throwable?)

    // This is a nuanced API in which the following things should
    // **NOT** be done from within block's lambda.
    //
    //  - If the TorJob implementation returns some sort of response
    //    via a callback, that callback should be invoked using the
    //    return value of onCompletion and not from within block's
    //    lambda.
    //  - Other TorJob functions that acquire the lock (such as cancel,
    //    onError, and onExecuting) should not be invoked within block,
    //    nor should the implementation expose externally any uncontrolled
    //    functionality which may call the aforementioned TorJob functions
    @Throws(IllegalStateException::class)
    protected fun <T: Any?> onCompletion(block: () -> T): T {
        @OptIn(InternalKmpTorApi::class)
        return synchronized(lock) {
            if (!isActive) throw IllegalStateException(toString())
            val result = block()
            _state = Completed
            onFailure = null
            result
        }
    }

    @Throws(IllegalStateException::class)
    protected fun onExecuting() {
        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            if (!isActive) throw IllegalStateException(toString())
            _state = Executing
        }
    }

    protected fun onError(cause: Throwable) {
        if (!isActive) return

        val onFailure = onFailure

        @OptIn(InternalKmpTorApi::class)
        val notify = synchronized(lock) {
            if (!isActive) return@synchronized false
            _state = Error
            this.onFailure = null
            true
        }

        if (!notify || onFailure == null) return
        onFailure(cause)
    }

    final override fun toString(): String = "TorJob[action=$name,state=$state]@${hashCode()}"
}
