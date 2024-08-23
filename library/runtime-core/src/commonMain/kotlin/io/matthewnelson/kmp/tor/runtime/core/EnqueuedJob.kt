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
@file:Suppress("KotlinRedundantDiagnosticSuppress", "FunctionName")

package io.matthewnelson.kmp.tor.runtime.core

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob.ExecutionPolicy.Cancellation
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob.State.*
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.SuppressedHandler
import io.matthewnelson.kmp.tor.runtime.core.util.awaitAsync
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.*

/**
 * Base abstraction for single-use model that tracks the state
 * of a queue-able job. Once completed, either successfully or
 * by cancellation/error, the [EnqueuedJob] is dead and should be
 * discarded.
 *
 * Heavily inspired by [kotlinx.coroutines.Job]
 *
 * @see [toImmediateErrorJob]
 * @see [toImmediateSuccessJob]
 * */
public abstract class EnqueuedJob protected constructor(

    /**
     * The name for this job. Will be utilized with error handling
     * for contextual purposes.
     * */
    @JvmField
    public val name: String,
    onFailure: OnFailure,
    handler: UncaughtException.Handler,
) {

    @Volatile
    private var _cancellationAttempt: CancellationException? = null
    @Volatile
    private var _cancellationException: CancellationException? = null
    @Volatile
    private var _completionCallbacks: LinkedHashSet<ItBlock<CancellationException?>>? = LinkedHashSet(1, 1.0f)
    @Volatile
    private var _isCompleting: Boolean = false
    @Volatile
    private var _handler: UncaughtException.Handler? = if (handler is SuppressedHandler) handler.root() else handler
    @Volatile
    private var _onFailure: OnFailure? = onFailure
    @Volatile
    private var _state: State = Enqueued
    @OptIn(InternalKmpTorApi::class)
    private val lock = SynchronizedObject()

    /**
     * The [ExecutionPolicy] of this job.
     *
     * Default: [ExecutionPolicy.DEFAULT]
     * */
    public open val executionPolicy: ExecutionPolicy = ExecutionPolicy.DEFAULT

    /**
     * An intermediate "state" indicating that completion,
     * either by success or error/cancellation, is underway.
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
     * @return true when [state] is [Enqueued] or [Executing],
     *   otherwise false.
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
     * The current [State] of the job.
     * */
    @get:JvmName("state")
    public val state: State get() = _state

    /**
     * Register a [handle] to be invoked when this [EnqueuedJob]
     * completes, either successfully or by cancellation/error.
     * If [handle] is already registered, [Disposable.noOp] is
     * returned.
     *
     * If the job has already completed, [handle] is invoked
     * immediately and [Disposable.noOp] is returned.
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
            return Disposable.noOp()
        }

        if (!wasAdded) return Disposable.noOp()

        return Disposable.Once.of {
            if (!isActive) return@of

            @OptIn(InternalKmpTorApi::class)
            synchronized(lock) {
                _completionCallbacks?.remove(handle)
            }
        }
    }

    /**
     * Cancel the job.
     *
     * If the job's [state] is [Executing], cancellation will
     * defer to the job's [ExecutionPolicy.Cancellation].
     *
     * If cancelled, [cancellationException] will be set,
     * [onFailure] will be invoked with [CancellationException]
     * to indicate cancellation, and all [invokeOnCompletion]
     * callbacks will be run.
     *
     * @return true if cancellation was successful.
     * */
    public fun cancel(cause: CancellationException?): Boolean {
        return cancel(cause, signalAttempt = executionPolicy.cancellation.accessibilityOpen)
    }

    public companion object {

        /**
         * Creates a [EnqueuedJob] which immediately invokes
         * [OnFailure] with the provided [cause].
         * */
        @JvmStatic
        @JvmName("immediateErrorJob")
        public fun OnFailure.toImmediateErrorJob(
            name: String,
            cause: Throwable,
            handler: UncaughtException.Handler,
        ): EnqueuedJob = object : EnqueuedJob(
            name,
            this@toImmediateErrorJob,
            handler,
        ) {
            init {
                onExecuting()
                onError(cause, null)
            }
        }

        /**
         * Creates a [EnqueuedJob] which immediately invokes
         * [OnSuccess] with the provided [response].
         * */
        @JvmStatic
        @JvmName("immediateSuccessJob")
        public fun <T: Any?> OnSuccess<T>.toImmediateSuccessJob(
            name: String,
            response: T,
            handler: UncaughtException.Handler,
        ): EnqueuedJob = object : EnqueuedJob(
            name,
            OnFailure.noOp(),
            handler
        ) {
            init {
                onExecuting()
                onCompletion(response) { this@toImmediateSuccessJob }
            }
        }
    }

    public enum class State {

        /**
         * The initial state.
         * */
        Enqueued,

        /**
         * Point of no return where the job has been de-queued
         * and is being executed. Cancellation will defer to the
         * job's [ExecutionPolicy]. The next state transition
         * will be to either [Success] or [Error].
         * */
        Executing,

        /**
         * If the job completed by cancellation via [cancel].
         * */
        Cancelled,

        /**
         * If the job completed successfully via [onCompletion].
         * */
        Success,

        /**
         * If the job completed exceptionally via [onError].
         *
         * **NOTE:** Even if [onFailure] was invoked with
         * [CancellationException], [onError] will still
         * set [state] to this to indicate that the job
         * was [Executing].
         * */
        Error,
    }

    /**
     * Dynamic configuration for implementors of [EnqueuedJob]
     * in order to modify how it wants to handle interactions
     * while in a state of [Executing].
     *
     * @see [ExecutionPolicy.DEFAULT]
     * @see [Companion.Builder]
     * @see [Cancellation]
     * */
    public class ExecutionPolicy private constructor(

        /**
         * The [Cancellation] configuration of a job while it is
         * in a state of [State.Executing].
         * */
        @JvmField
        public val cancellation: Cancellation,
    ) {

        /**
         * If this [ExecutionPolicy] configuration is equal to
         * [ExecutionPolicy.DEFAULT]
         * */
        @get:JvmName("isDefault")
        public val isDefault: Boolean get() = this == DEFAULT

        public companion object {

            /**
             * The default [ExecutionPolicy] of all [EnqueuedJob]
             * implementations that do not override [executionPolicy].
             *
             *  - [cancellation] = [Cancellation.DEFAULT]
             * */
            @JvmField
            public val DEFAULT: ExecutionPolicy = ExecutionPolicy(
                cancellation = Cancellation.DEFAULT,
            )

            /**
             * Opener for creating a new [ExecutionPolicy] configuration.
             * */
            @JvmStatic
            public fun Builder(
                block: ThisBlock<BuilderScope>,
            ): ExecutionPolicy = BuilderScope.get().apply(block).build()
        }

        /**
         * How the job handles cancellation while in a state of [Executing]
         *
         * @see [Cancellation.DEFAULT]
         * */
        public class Cancellation private constructor(

            /**
             * If `true`, [cancellationAttempt] may be set while the job is
             * in a state of [Executing] in order to signal that cancellation
             * is desired.
             *
             * @see [cancellationAttempt]
             * */
            @JvmField
            public val allowAttempts: Boolean,

            /**
             * If `true`, allows the public [cancel] function the ability
             * to set [cancellationAttempt] while the job is in a state
             * of [Executing] (i.e. anyone with access to the [EnqueuedJob]
             * instance may signal for cancellation).
             *
             * If `false`, the public [cancel] function will **not** be
             * allowed to signal for cancellation. This constrains the
             * functionality to users of the [awaitAsync] and
             * [io.matthewnelson.kmp.tor.runtime.core.util.awaitSync]
             * APIs (i.e. only the callers of `executeAsync` and
             * `executeSync` extension functions may signal for cancellation).
             *
             * This has no effect if [allowAttempts] is set to `false`.
             * */
            @JvmField
            public val accessibilityOpen: Boolean,

            /**
             * If `true`, calls to [onError] will have their cause replaced
             * with [cancellationAttempt] if it is set.
             *
             * This has no effect if [allowAttempts] is set to `false`.
             * */
            @JvmField
            public val substituteErrorWithAttempt: Boolean,
        ) {

            /**
             * If this [ExecutionPolicy.Cancellation] configuration is equal
             * to [Cancellation.DEFAULT]
             * */
            @get:JvmName("isDefault")
            public val isDefault: Boolean get() = this == DEFAULT

            public companion object {

                /**
                 * The default [ExecutionPolicy.Cancellation] settings.
                 *
                 *  - [allowAttempts] = `false`
                 *  - [accessibilityOpen] = `false`
                 *  - [substituteErrorWithAttempt] = `true`
                 * */
                @JvmField
                public val DEFAULT: Cancellation = Cancellation(
                    allowAttempts = false,
                    accessibilityOpen = false,
                    substituteErrorWithAttempt = true,
                )
            }

            @KmpTorDsl
            public class BuilderScope private constructor() {

                /**
                 * Default: `false`
                 *
                 * @see [Cancellation.allowAttempts]
                 * */
                @JvmField
                public var allowAttempts: Boolean = DEFAULT.allowAttempts

                /**
                 * Default: `false`
                 *
                 * @see [Cancellation.accessibilityOpen]
                 * */
                @JvmField
                public var accessibilityOpen: Boolean = DEFAULT.accessibilityOpen

                /**
                 * Default: `true`
                 *
                 * @see [Cancellation.substituteErrorWithAttempt]
                 * */
                @JvmField
                public var substituteErrorWithAttempt: Boolean = DEFAULT.substituteErrorWithAttempt

                @JvmSynthetic
                internal fun build(): Cancellation {
                    val allow = allowAttempts
                    val accessibility = accessibilityOpen
                    val substitute = substituteErrorWithAttempt

                    if (
                        allow == DEFAULT.allowAttempts
                        && accessibility == DEFAULT.accessibilityOpen
                        && substitute == DEFAULT.substituteErrorWithAttempt
                    ) {
                        return DEFAULT
                    }

                    return Cancellation(
                        allowAttempts = allow,
                        accessibilityOpen = accessibility,
                        substituteErrorWithAttempt = substitute,
                    )
                }

                internal companion object {
                    @JvmSynthetic
                    internal fun get(): BuilderScope =
                        BuilderScope()
                }
            }

            /** @suppress */
            public override fun equals(other: Any?): Boolean = privateEquals(other)
            /** @suppress */
            public override fun hashCode(): Int = privateHashCode()
            /** @suppress */
            public override fun toString(): String = privateToString()
        }

        /**
         * Builder class for configuring a new [ExecutionPolicy]
         *
         * @see [ExecutionPolicy.Companion.Builder]
         * */
        @KmpTorDsl
        public class BuilderScope private constructor() {

            private val _cancellation = Cancellation.BuilderScope.get()

            @KmpTorDsl
            public fun cancellation(
                block: ThisBlock<Cancellation.BuilderScope>,
            ): BuilderScope = apply { _cancellation.apply(block) }

            @JvmSynthetic
            internal fun build(): ExecutionPolicy {
                val cancellation = _cancellation.build()

                if (
                    cancellation == DEFAULT.cancellation
                ) {
                    return DEFAULT
                }

                return ExecutionPolicy(
                    cancellation = cancellation,
                )
            }

            internal companion object {

                @JvmSynthetic
                internal fun get(): BuilderScope = BuilderScope()
            }
        }

        /** @suppress */
        public override fun equals(other: Any?): Boolean = privateEquals(other)
        /** @suppress */
        public override fun hashCode(): Int = privateHashCode()
        /** @suppress */
        public override fun toString(): String = privateToString()
    }

    /**
     * If [cancel] was called while the [EnqueuedJob] is in a
     * non-cancellable state (i.e. [Executing]), and the
     * implementing class has declared a [ExecutionPolicy]
     * allowing for it, this will be set in order to signal
     * for cancellation.
     *
     * It is the implementation's prerogative to check this
     * during execution of the job and cancel itself if able.
     *
     * This will **only** return non-null while the [EnqueuedJob]
     * is in the [Executing] state, [isCompleting] is `false`, and
     * [ExecutionPolicy.Cancellation.allowAttempts] is set to `true`.
     *
     * @see [ExecutionPolicy]
     * */
    protected fun cancellationAttempt(): CancellationException? {
        if (_isCompleting || _state != Executing) return null
        return _cancellationAttempt
    }

    /**
     * Notifies the implementation that the [EnqueuedJob]
     * has been cancelled in order to perform cleanup, if
     * necessary.
     *
     * @param [cause] The exception, if any, passed to [cancel].
     * */
    protected open fun onCancellation(cause: CancellationException?) {}

    /**
     * Moves the state from [Enqueued] to [Executing], indicating
     * that the caller has "taken ownership" of the [EnqueuedJob].
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
     * if the job is completed or completing.
     *
     * Implementations **must not** call [invokeOnCompletion] from within
     * the [withLock] lambda. It will result in a deadlock.
     *
     * @return `true` if it executed successfully, `false` if the job
     *   is completed or completing.
     * */
    protected fun <T: Any?> onCompletion(response: T, withLock: (() -> OnSuccess<T>?)?): Boolean {
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
     * with provided [cause]. Does nothing if the job is completed or
     * completing.
     *
     * [withLock] will be invoked with the final [Throwable] for which
     * [onFailure] will be called with. It may not be the same as
     * [cause], depending on the [executionPolicy] set.
     *
     * Implementations **must not** call [invokeOnCompletion] from within
     * the [withLock] lambda. It will result in a deadlock.
     *
     * @return `true` if it executed successfully, `false` if the job
     *   is completed or completing.
     * */
    protected fun onError(cause: Throwable, withLock: ItBlock<Throwable>?): Boolean {
        if (_isCompleting || !isActive) return false

        @OptIn(InternalKmpTorApi::class)
        val completion = synchronized(lock) {
            if (_isCompleting || !isActive) return@synchronized null

            val c = if (executionPolicy.cancellation.substituteErrorWithAttempt) {
                _cancellationAttempt ?: cause
            } else {
                cause
            }

            val isCancellation = c is CancellationException

            if (isCancellation) {
                _cancellationException = c as CancellationException
            }
            // Set before invoking withLock so that
            // if implementation calls again it will
            // not lock up.
            _isCompleting = true
            withLock?.invoke(c)

            Executable {
                Error.doFinal {
                    try {
                        _onFailure?.let { it(c) }
                    } finally {
                        if (isCancellation) {
                            onCancellation(_cancellationException)
                        }
                    }
                }
            }
        }

        if (completion == null) return false

        completion.execute()
        return true
    }

    @JvmSynthetic
    internal fun cancellationException(): CancellationException? = _cancellationException

    /*
     * This is strictly for the await APIs that back executeSync and executeAsync.
     * The `runtime` dependency having RuntimeEvent.EXECUTE events, as well as the
     * `runtime-ctrl` dependency having the TorCmdIntercept API means that someone
     * other than the call originator for that job could signal cancellation while
     * it is executing.
     * */
    @JvmSynthetic
    internal fun cancel(cause: CancellationException?, signalAttempt: Boolean): Boolean {
        if (_isCompleting || !isActive) return false

        @OptIn(InternalKmpTorApi::class)
        val completion = synchronized(lock) {
            if (_isCompleting || !isActive) return@synchronized null

            if (_state == Executing) {
                if (executionPolicy.cancellation.allowAttempts && signalAttempt) {
                    _cancellationAttempt = cause ?: CancellationException(toString(Cancelled))
                }
                return@synchronized null
            }

            val c = cause ?: CancellationException(toString(Cancelled))
            _cancellationException = c
            _isCompleting = true
            c
        }

        if (completion == null) return false

        Cancelled.doFinal {
            try {
                _onFailure?.let { it(completion) }
            } finally {
                onCancellation(cause)
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
                _cancellationAttempt = null
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

    /** @suppress */
    public final override fun toString(): String = toString(_state)

    private fun toString(state: State): String {
        val clazz = this::class.simpleName ?: "EnqueuedJob"
        return "$clazz[name=$name, state=$state]@${hashCode()}"
    }

    /**
     * Helper for creating argument based processor agnostic
     * extension functions for Async/Sync APIs.
     *
     * @see [io.matthewnelson.kmp.tor.runtime.Action.Processor]
     * @see [io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd.Privileged.Processor]
     * @see [io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd.Unprivileged.Processor]
     * @see [io.matthewnelson.kmp.tor.runtime.core.util.awaitAsync]
     * @see [io.matthewnelson.kmp.tor.runtime.core.util.awaitSync]
     * @suppress
     * */
    @InternalKmpTorApi
    public interface Argument
}

@Suppress("NOTHING_TO_INLINE")
private inline fun EnqueuedJob.ExecutionPolicy.privateEquals(
    other: Any?,
): Boolean {
    return other is EnqueuedJob.ExecutionPolicy
    && other.cancellation == cancellation
}

@Suppress("NOTHING_TO_INLINE")
private inline fun EnqueuedJob.ExecutionPolicy.privateHashCode(): Int {
    var result = 17
    result = result * 42 + cancellation.hashCode()
    return result
}

@Suppress("NOTHING_TO_INLINE")
private inline fun EnqueuedJob.ExecutionPolicy.privateToString(): String = buildString {
    appendLine("ExecutionPolicy: [")
    append("    cancellation: [")

    val lines = cancellation.toString().lines()
    for (i in 1..lines.lastIndex) {
        appendLine()
        append("    ")
        append(lines[i])
    }
    appendLine()
    append(']')
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Cancellation.privateEquals(
    other: Any?,
): Boolean {
    return other is Cancellation
    && other.allowAttempts == allowAttempts
    && other.accessibilityOpen == accessibilityOpen
    && other.substituteErrorWithAttempt == substituteErrorWithAttempt
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Cancellation.privateHashCode(): Int {
    var result = 17
    result = result * 42 + allowAttempts.hashCode()
    result = result * 42 + accessibilityOpen.hashCode()
    result = result * 42 + substituteErrorWithAttempt.hashCode()
    return result
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Cancellation.privateToString(): String = buildString {
    appendLine("ExecutionPolicy.Cancellation: [")
    append("    allowAttempts: ")
    appendLine(allowAttempts)
    append("    accessibilityOpen: ")
    appendLine(accessibilityOpen)
    append("    substituteErrorWithAttempt: ")
    appendLine(substituteErrorWithAttempt)
    append(']')
}
