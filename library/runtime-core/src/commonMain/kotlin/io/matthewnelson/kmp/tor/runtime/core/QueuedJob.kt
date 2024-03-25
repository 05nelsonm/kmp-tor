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
import io.matthewnelson.kmp.tor.runtime.core.QueuedJob.State.*
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName

/**
 * Base abstraction for single-use model that tracks the state
 * of a queue-able job. Once completed, either successfully or
 * by cancellation/error, the [QueuedJob] is dead and should be
 * discarded.
 * */
public abstract class QueuedJob protected constructor(
    @JvmField
    public val name: String,
    @Volatile
    private var onFailure: ItBlock<Throwable>?,
) {

    @Volatile
    private var _state: State = Enqueued
    @Volatile
    private var completionCallbacks: LinkedHashSet<ItBlock<Unit>>? = LinkedHashSet(1, 1.0f)
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
         * The initial state.
         * */
        Enqueued,

        /**
         * Point of no return where the job has been de-queued
         * and is being executed. Cancellation does nothing and
         * the next state transition will either be [onCompletion]
         * or [onError].
         * */
        Executing,

        /**
         * If the job completed by cancellation.
         * */
        Cancelled,

        /**
         * If the job completed successfully.
         * */
        Completed,

        /**
         * If the job completed by error.
         * */
        Error,
    }

    /**
     * Attach a [callback] to be invoked when this [QueuedJob]
     * completes, either successfully or by cancellation/error.
     *
     * If the job has already completed, [callback] is invoked
     * immediately.
     *
     * [callback] should **NOT** throw exception. In the event
     * that it does, it will be swallowed.
     * */
    public fun invokeOnCompletion(callback: ItBlock<Unit>) {
        @OptIn(InternalKmpTorApi::class)
        val invokeImmediately = synchronized(lock) {
            completionCallbacks?.add(callback)
        } == null

        if (!invokeImmediately) return
        try {
            callback(Unit)
        } catch (_: Throwable) {}
    }

    /**
     * Cancels the job.
     *
     * Does nothing if [state] is anything other than
     * [State.Enqueued].
     *
     * If cancelled, [onFailure] will be invoked with
     * [CancellationException] to indicate so.
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

        onCancellation(cause)

        try {
            if (onFailure != null) {
                val e = if (cause is CancellationException) {
                    cause
                } else {
                    CancellationException(toString(), cause)
                }

                onFailure(e)
            }
        } finally {
            doInvokeOnCompletion()
        }
    }

    /**
     * Notifies the implementation that the [QueuedJob]
     * has been cancelled in order to perform cleanup, if
     * necessary.
     *
     * @param [cause] The exception, if any, passed to [cancel].
     * */
    protected open fun onCancellation(cause: Throwable?) {}

    /**
     * Moves the state to [State.Executing]
     *
     * @throws [IllegalStateException] if job has already completed.
     * */
    @Throws(IllegalStateException::class)
    protected fun onExecuting() {
        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            if (!isActive) throw IllegalStateException(toString())
            _state = Executing
        }
    }

    /**
     * Sets the job state to [State.Completed] and invokes
     * [ItBlock] (onSuccess) returned by [withLock] with provided
     * [response]. Does nothing if the job is already completed.
     *
     * **NOTE:** [withLock] lambda should not call any other
     * functions which acquire the lock such as [cancel], [onError],
     * or [invokeOnCompletion].
     * */
    protected fun <T: Any> onCompletion(response: T, withLock: () -> ItBlock<T>?) {
        if (!isActive) return

        var onSuccess: ItBlock<T>? = null

        @OptIn(InternalKmpTorApi::class)
        val notify = synchronized(lock) {
            if (!isActive) return@synchronized false
            onSuccess = withLock()
            _state = Completed
            onFailure = null
            true
        }

        if (!notify) return

        try {
            onSuccess?.let { it(response) }
        } finally {
            doInvokeOnCompletion()
        }
    }

    /**
     * Sets the job state to [State.Error] and invokes [onFailure]
     * with provided [cause]. Does nothing if the job is already
     * completed.
     *
     * **NOTE:** [withLock] lambda should not call any other
     * functions which obtain the lock such as [cancel], [onCompletion],
     * or [invokeOnCompletion].
     * */
    protected fun onError(cause: Throwable, withLock: ItBlock<Unit>) {
        if (!isActive) return

        val onFailure = onFailure

        @OptIn(InternalKmpTorApi::class)
        val notify = synchronized(lock) {
            if (!isActive) return@synchronized false
            _state = Error
            this.onFailure = null
            withLock(Unit)
            true
        }

        if (!notify) return

        try {
            if (onFailure != null) {
                onFailure(cause)
            }
        } finally {
            doInvokeOnCompletion()
        }
    }

    private fun doInvokeOnCompletion() {
        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            val callbacks = completionCallbacks
            completionCallbacks = null
            callbacks
        }?.forEach { callback ->
            try {
                callback(Unit)
            } catch (_: Throwable) {}
        }
    }

    final override fun toString(): String = "QueuedJob[name=$name,state=$state]@${hashCode()}"
}
