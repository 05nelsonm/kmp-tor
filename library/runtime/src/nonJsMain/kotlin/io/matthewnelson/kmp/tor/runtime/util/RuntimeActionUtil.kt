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
@file:JvmName("RuntimeActionUtil")
@file:Suppress("KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime.util

import io.matthewnelson.kmp.process.Blocking
import io.matthewnelson.kmp.tor.runtime.RuntimeAction
import io.matthewnelson.kmp.tor.runtime.internal.commonExecuteAsync
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.time.Duration.Companion.milliseconds

/**
 * Enqueues the [action], suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [startDaemonAsync]
 * @see [stopDaemonAsync]
 * @see [restartDaemonAsync]
 * @see [executeSync]
 * */
@Throws(Throwable::class)
public actual suspend fun <T: RuntimeAction.Processor> T.executeAsync(
    action: RuntimeAction,
): T = commonExecuteAsync(action)

/**
 * Enqueues the [action], blocking the current thread
 * until completion or cancellation/error.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @see [startDaemonSync]
 * @see [stopDaemonSync]
 * @see [restartDaemonSync]
 * @see [executeAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
public fun <T: RuntimeAction.Processor> T.executeSync(
    action: RuntimeAction,
    cancellation: (() -> CancellationException?)? = null,
): T {
    var failure: Throwable? = null
    var success: Unit? = null

    val job = enqueue(
        action = action,
        onFailure = { failure = it },
        onSuccess = { success = it },
    )

    var callback = cancellation

    while (true) {
        if (success != null) return this
        failure?.let { throw it }

        Blocking.threadSleep(25.milliseconds)

        // No possibility of cancellation. Skip.
        if (callback == null) continue

        val exception = try {
            callback() ?: continue
        } catch (t: Throwable) {
            if (t is CancellationException) t else CancellationException(t)
        }

        // de-reference callback as to not invoke it anymore
        // in the event that job is unable to be cancelled (e.g.
        // is in State.Executing), will just continue to loop
        // until execution completes.
        callback = null
        job.cancel(exception)
    }
}

/**
 * Starts the tor daemon, suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [RuntimeAction.StartDaemon]
 * @see [startDaemonSync]
 * */
@Throws(Throwable::class)
public actual suspend inline fun <T: RuntimeAction.Processor> T.startDaemonAsync(): T =
    executeAsync(RuntimeAction.StartDaemon)

/**
 * Stops the tor daemon, suspending the current coroutine
 * until completion or cancellation/error.
 *
 * @see [RuntimeAction.StopDaemon]
 * @see [stopDaemonSync]
 * */
@Throws(Throwable::class)
public actual suspend inline fun <T: RuntimeAction.Processor> T.stopDaemonAsync(): T =
    executeAsync(RuntimeAction.StopDaemon)

/**
 * Stops and then starts the tor daemon, suspending the
 * current coroutine until completion or cancellation/error.
 *
 * @see [RuntimeAction.RestartDaemon]
 * @see [restartDaemonSync]
 * */
@Throws(Throwable::class)
public actual suspend inline fun <T: RuntimeAction.Processor> T.restartDaemonAsync(): T =
    executeAsync(RuntimeAction.RestartDaemon)

/**
 * Starts the tor daemon, blocking the current thread
 * until completion or cancellation/error.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @see [RuntimeAction.StartDaemon]
 * @see [startDaemonAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
@Suppress("NOTHING_TO_INLINE")
public inline fun <T: RuntimeAction.Processor> T.startDaemonSync(
    noinline cancellation: (() -> CancellationException?)? = null,
): T = executeSync(RuntimeAction.StartDaemon, cancellation)

/**
 * Stops the tor daemon, blocking the current thread
 * until completion or cancellation/error.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @see [RuntimeAction.StopDaemon]
 * @see [stopDaemonAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
@Suppress("NOTHING_TO_INLINE")
public inline fun <T: RuntimeAction.Processor> T.stopDaemonSync(
    noinline cancellation: (() -> CancellationException?)? = null,
): T = executeSync(RuntimeAction.StopDaemon, cancellation)

/**
 * Stops and then starts the tor daemon, blocking the
 * current thread until completion or cancellation/error.
 *
 * **NOTE:** This is a blocking call and should be invoked from
 * a background thread.
 *
 * @see [RuntimeAction.RestartDaemon]
 * @see [restartDaemonAsync]
 * @param [cancellation] optional callback which is invoked
 *   after every thread sleep (so, multiple times) in order
 *   to trigger job cancellation if a non-null exception
 *   value is returned.
 * */
@JvmOverloads
@Throws(Throwable::class)
@Suppress("NOTHING_TO_INLINE")
public inline fun <T: RuntimeAction.Processor> T.restartDaemonSync(
    noinline cancellation: (() -> CancellationException?)? = null,
): T = executeSync(RuntimeAction.RestartDaemon, cancellation)
