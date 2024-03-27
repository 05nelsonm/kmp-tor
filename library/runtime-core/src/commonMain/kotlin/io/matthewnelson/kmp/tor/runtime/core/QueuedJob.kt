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
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
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
    // TODO:
    //  @JvmField
    //  public val canCancelWhileExecuting: Boolean
    @Volatile
    private var onFailure: Callback<Throwable>?,
    handler: UncaughtException.Handler,
) {

    @Volatile
    private var _state: State = Enqueued
    @Volatile
    private var completionCallbacks: LinkedHashSet<ItBlock<Unit>>? = LinkedHashSet(1, 1.0f)
    @Volatile
    private var handler: UncaughtException.Handler? = handler
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
     * Register a [handle] to be invoked when this [QueuedJob]
     * completes, either successfully or by cancellation/error.
     * If [handle] is already registered, [Disposable.NOOP] is
     * returned.
     *
     * If the job has already completed, [handle] is invoked
     * immediately and [Disposable.NOOP] is returned.
     *
     * [handle] should **NOT** throw exception. In the event
     * that it does, it will be delegated to the closest
     * [UncaughtException.Handler]. If [handle] is being invoked
     * immediately (job completed), exceptions will be delegated
     * to [UncaughtException.Handler.THROW]. [handle] should be
     * non-blocking, fast, and thread-safe.
     *
     * There is no guarantee on the execution context for which
     * [handle] is invoked from.
     *
     * @return [Disposable] to de-register [handle] if it is no
     *   longer needed.
     * */
    public fun invokeOnCompletion(handle: ItBlock<Unit>): Disposable {
        @OptIn(InternalKmpTorApi::class)
        val wasAdded = synchronized(lock) {
            // doFinal (which de-references callbacks) is not called
            // within synchronized block of cancel/onError/onCompletion
            // when state is updated. Cannot rely on completionCallbacks
            // being null to detect if still active. Need to check.
            if (!isActive) return@synchronized null

            completionCallbacks?.add(handle)
        }

        // Invoke immediately
        if (wasAdded == null) {
            UncaughtException.Handler.THROW.tryCatch(toString()) {
                handle(Unit)
            }
            return Disposable.NOOP
        }

        if (!wasAdded) return Disposable.NOOP

        return Disposable {
            if (!isActive) return@Disposable

            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) {
                completionCallbacks?.remove(handle)
            }
        }
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
    public fun cancel(cause: CancellationException?): Boolean {
        if (state != Enqueued) return false

        val onFailure = onFailure

        @OptIn(InternalKmpTorApi::class)
        val complete = synchronized(lock) {
            if (state != Enqueued) return@synchronized false
            _state = Cancelled
            this.onFailure = null
            true
        }

        if (!complete) return false

        onCancellation(cause)

        doFinal(action = {
            if (onFailure != null) {
                val e = cause ?: CancellationException(toString())
                onFailure(e)
            }
        })

        return true
    }

    /**
     * Notifies the implementation that the [QueuedJob]
     * has been cancelled in order to perform cleanup, if
     * necessary.
     *
     * @param [cause] The exception, if any, passed to [cancel].
     * */
    protected open fun onCancellation(cause: CancellationException?) {}

    /**
     * Moves the state from [Enqueued] to [Executing], indicating
     * that the caller has "taken ownership" of the [QueuedJob].
     *
     * If a different caller attempts to invoke [onExecuting] again,
     * an exception is raised to prevent duplicate execution.
     *
     * @throws [IllegalStateException] if current state is not [Enqueued].
     * */
    @Throws(IllegalStateException::class)
    protected fun onExecuting() {
        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            if (state != Enqueued) throw IllegalStateException(toString())
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
    protected fun <T: Any> onCompletion(response: T, withLock: () -> Callback<T>?) {
        if (!isActive) return

        var onSuccess: Callback<T>? = null

        @OptIn(InternalKmpTorApi::class)
        val complete = synchronized(lock) {
            if (!isActive) return@synchronized false
            onSuccess = withLock()
            _state = Completed
            onFailure = null
            true
        }

        if (!complete) return

        doFinal(action = {
            onSuccess?.let { it(response) }
        })
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
        val complete = synchronized(lock) {
            if (!isActive) return@synchronized false
            _state = Error
            this.onFailure = null
            withLock(Unit)
            true
        }

        if (!complete) return

        doFinal(action = {
            if (onFailure != null) {
                onFailure(cause)
            }
        })
    }

    private fun doFinal(action: () -> Unit) {
        val context = toString()

        handler.withSuppression {
            tryCatch(context) { action() }

            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) {
                val callbacks = completionCallbacks
                completionCallbacks = null
                handler = null
                callbacks
            }?.forEach { callback ->
                tryCatch("$context.invokeOnCompletion") {
                    callback(Unit)
                }
            }
        }
    }

    final override fun toString(): String = "QueuedJob[name=$name,state=$state]@${hashCode()}"
}
