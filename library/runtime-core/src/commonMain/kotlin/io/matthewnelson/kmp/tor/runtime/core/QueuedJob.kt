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
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.requireInstanceIsNotSuppressed
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic

/**
 * Base abstraction for single-use model that tracks the state
 * of a queue-able job. Once completed, either successfully or
 * by cancellation/error, the [QueuedJob] is dead and should be
 * discarded.
 *
 * Heavily inspired by [kotlinx.coroutines.Job]
 *
 * @throws [IllegalArgumentException] if handler is a leaked
 *   reference of [UncaughtException.SuppressedHandler]
 * */
public abstract class QueuedJob
@Throws(IllegalArgumentException::class)
protected constructor(
    @JvmField
    public val name: String,
    // TODO:
    //  @JvmField
    //  public val canCancelWhileExecuting: Boolean
    onFailure: OnFailure,
    handler: UncaughtException.Handler,
) {

    init { handler.requireInstanceIsNotSuppressed() }

    @Volatile
    private var _cancellationException: CancellationException? = null
    @Volatile
    private var _completionCallbacks: LinkedHashSet<ItBlock<CancellationException?>>? = LinkedHashSet(1, 1.0f)
    @Volatile
    private var _isCompleting: Boolean = false
    @Volatile
    private var _handler: UncaughtException.Handler? = handler
    @Volatile
    private var _onFailure: OnFailure? = onFailure
    @Volatile
    private var _state: State = Enqueued
    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()

    /**
     * If [cancel] was invoked successfully, this **will not** be null.
     * */
    @get:JvmName("cancellationException")
    public val cancellationException: CancellationException? get() = _cancellationException

    /**
     * An intermediate "state" indicating that completion,
     * either by success or error/cancellation is underway.
     *
     * Will be set back to false after all [invokeOnCompletion]
     * handles have been run.
     * */
    @get:JvmName("isCompleting")
    public val isCompleting: Boolean get() = _isCompleting

    /**
     * If [state] is [State.Cancelled]
     * */
    @get:JvmName("isCancelled")
    public val isCancelled: Boolean get() = _state == Cancelled

    /**
     * If [state] is [State.Error]
     * */
    @get:JvmName("isError")
    public val isError: Boolean get() = _state == Error

    /**
     * If [state] is [State.Success]
     * */
    @get:JvmName("isSuccess")
    public val isSuccess: Boolean get() = _state == Success

    /**
     * Checks if the job is in a completion state or not.
     *
     * @return true when [state] is [Enqueued] or [Executing]
     *   otherwise false
     * */
    @get:JvmName("isActive")
    public val isActive: Boolean get() = when (_state) {
        Cancelled,
        Success,
        Error -> false
        Enqueued,
        Executing -> true
    }

    /**
     * The current [State] of the job
     * */
    @get:JvmName("state")
    public val state: State get() = _state

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
        Success,

        /**
         * If the job completed exceptionally.
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
     * If the job completed by cancellation, [handle] will
     * be invoked with a [CancellationException] argument to
     * indicate as such.
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
    public fun invokeOnCompletion(handle: ItBlock<CancellationException?>): Disposable {
        @OptIn(InternalKmpTorApi::class)
        val wasAdded = synchronized(lock) {
            _completionCallbacks?.add(handle)
        }

        // Invoke immediately
        if (wasAdded == null) {
            UncaughtException.Handler.THROW.tryCatch(toString()) {
                handle(_cancellationException)
            }
            return Disposable.NOOP
        }

        if (!wasAdded) return Disposable.NOOP

        return Disposable {
            if (!isActive) return@Disposable

            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) {
                _completionCallbacks?.remove(handle)
            }
        }
    }

    /**
     * Cancels the job.
     *
     * Does nothing if [state] is anything other than
     * [State.Enqueued] or [isCompleting] is true.
     *
     * If cancelled, [cancellationException] will be set,
     * [_onFailure] will be invoked with [CancellationException]
     * to indicate cancellation, and all [invokeOnCompletion]
     * callbacks will be run.
     *
     * @return true if cancellation was successful
     * */
    public fun cancel(cause: CancellationException?): Boolean {
        if (_isCompleting || _state != Enqueued) return false

        @OptIn(InternalKmpTorApi::class)
        val complete = synchronized(lock) {
            if (_isCompleting || _state != Enqueued) return@synchronized false
            _cancellationException = cause ?: CancellationException(toString(Cancelled))
            _isCompleting = true
            true
        }

        if (!complete) return false

        Cancelled.doFinal {
            try {
                _onFailure?.let { it(_cancellationException!!) }
            } finally {
                onCancellation(cause)
            }
        }

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
     * an exception is raised to prevent duplicate executions.
     *
     * @throws [IllegalStateException] if current state is not [Enqueued]
     *   or [isCompleting] is true.
     * */
    @Throws(IllegalStateException::class)
    protected fun onExecuting() {
        if (_isCompleting || _state != Enqueued) throw IllegalStateException(toString())

        @OptIn(InternalKmpTorApi::class)
        synchronized(lock) {
            if (_isCompleting || _state != Enqueued) throw IllegalStateException(toString())
            _state = Executing
        }
    }

    /**
     * Sets the job state to [State.Success] and invokes [OnSuccess]
     * returned by [withLock] with provided [response]. Does nothing
     * if the job is already completed or [isCompleting] is true.
     *
     * Implementations **must not** call [invokeOnCompletion] from within
     * the [withLock] lambda. It will result in a deadlock.
     *
     * @return true if it executed successfully, false if the job
     *   was already completed.
     * */
    protected fun <T: Any> onCompletion(response: T, withLock: (() -> OnSuccess<T>?)?): Boolean {
        if (_isCompleting || !isActive) return false

        var onSuccess: OnSuccess<T>? = null

        @OptIn(InternalKmpTorApi::class)
        val complete = synchronized(lock) {
            if (_isCompleting || !isActive) return@synchronized false
            // Set before invoking withLock so that
            // if implementation calls again it will
            // not lock up.
            _isCompleting = true
            onSuccess = withLock?.invoke()
            true
        }

        if (!complete) return false

        Success.doFinal {
            onSuccess?.let { it(response) }
        }

        return true
    }

    /**
     * Sets the job state to [State.Error] and invokes [OnFailure]
     * with provided [cause]. Does nothing if the job is already
     * completed.
     *
     * If [cause] is [CancellationException], [cancellationException]
     * will be set and [State.Cancelled] will be utilized for the
     * completion state.
     *
     * Implementations **must not** call [invokeOnCompletion] from within
     * the [withLock] lambda. It will result in a deadlock.
     *
     * @return true if it executed successfully, false if the job
     *   was already completed.
     * */
    protected fun onError(cause: Throwable, withLock: ItBlock<Unit>?): Boolean {
        if (_isCompleting || !isActive) return false

        val isCancellation = cause is CancellationException

        @OptIn(InternalKmpTorApi::class)
        val complete = synchronized(lock) {
            if (_isCompleting || !isActive) return@synchronized false
            if (isCancellation) {
                _cancellationException = cause as CancellationException
            }
            // Set before invoking withLock so that
            // if implementation calls again it will
            // not lock up.
            _isCompleting = true
            withLock?.invoke(Unit)
            true
        }

        if (!complete) return false

        (if (isCancellation) Cancelled else Error).doFinal {
            try {
                _onFailure?.let { it(cause) }
            } finally {
                if (isCancellation) {
                    onCancellation(_cancellationException)
                }
            }
        }

        return true
    }

    private fun State.doFinal(action: () -> Unit) {
        check(_isCompleting) { "isCompleting must be true when doFinal is called" }
        check(isActive) { "isActive must be true when doFinal is called" }

        when (this) {
            Enqueued,
            Executing -> throw IllegalArgumentException("$this cannot be utilized with doFinal")
            Cancelled,
            Success,
            Error -> {}
        }

        val context = toString(this)

        _handler.withSuppression {
            tryCatch(context) { action() }

            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) {
                _state = this@doFinal

                // de-reference all the things
                _handler = null
                _onFailure = null

                // Must invoke outside synchronized
                // lambda to prevent deadlock
                val callbacks = _completionCallbacks
                _completionCallbacks = null
                callbacks
            }?.forEach { callback ->
                tryCatch("$context.invokeOnCompletion") {
                    callback(_cancellationException)
                }
            }

            _isCompleting = false
        }
    }

    public final override fun toString(): String = toString(_state)

    private fun toString(state: State): String {
        val clazz = this::class.simpleName ?: "QueuedJob"
        return "$clazz[name=$name, state=$state]@${hashCode()}"
    }

    public companion object {

        /**
         * Creates a [QueuedJob] which immediately invokes
         * [OnFailure] with the provided [cause].
         *
         * @throws [IllegalArgumentException] if handler is an
         *   instance of [UncaughtException.SuppressedHandler]
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun OnFailure.toImmediateErrorJob(
            name: String,
            cause: Throwable,
            handler: UncaughtException.Handler,
        ): QueuedJob = object : QueuedJob(
            name,
            this@toImmediateErrorJob,
            handler,
        ) {
            init { onError(cause, null) }
        }

        /**
         * Creates a [QueuedJob] which immediately invokes
         * [OnSuccess] with the provided [response].
         *
         * @throws [IllegalArgumentException] if handler is an
         *   instance of [UncaughtException.SuppressedHandler]
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun <T: Any> OnSuccess<T>.toImmediateSuccessJob(
            name: String,
            response: T,
            handler: UncaughtException.Handler,
        ): QueuedJob = object : QueuedJob(
            name,
            ON_FAILURE,
            handler
        ) {
            init { onCompletion(response) { this@toImmediateSuccessJob } }
        }

        private val ON_FAILURE = OnFailure {}
    }
}
