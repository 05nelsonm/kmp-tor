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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "KotlinRedundantDiagnosticSuppress")

package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.awaitAsync
import io.matthewnelson.kmp.tor.runtime.core.util.awaitSync
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@OptIn(InternalKmpTorApi::class)
public actual enum class Action: EnqueuedJob.Argument {

    /**
     * Starts the tor daemon.
     *
     * If tor is already running, the [EnqueuedJob] returned
     * by [Processor.enqueue] will complete with success.
     *
     * **NOTE:** Tor's startup process is broken out
     * into five (5) phases:
     *
     *  1. **Installation**: Tor resources are extracted to the
     *     filesystem, if needed.
     *  2. **Configuration**: [TorConfig] is built and verified.
     *  3. **Process Start**: Tor is started in a child process.
     *  4. **Control Connection**: A control connection with tor
     *     is established and initial settings are configured.
     *  5. **Bootstrap**: Tor bootstraps itself with the network.
     *
     * This action completes after phase 4.
     * */
    StartDaemon,

    /**
     * Stops the tor daemon. All [TorCmd] that are
     * enqueued and awaiting execution will be interrupted.
     *
     * If tor is not running, the [EnqueuedJob] returned
     * by [Processor.enqueue] will complete with success.
     * */
    StopDaemon,

    /**
     * Stops, then starts the tor daemon. All [TorCmd]
     * that are enqueued and awaiting execution will be
     * interrupted.
     *
     * If tor is not running, it will be started via [StartDaemon].
     * */
    RestartDaemon;

    /**
     * Base interface for implementations that process [Action].
     *
     * **NOTE:** Implementors **MUST** process the action on a different
     * thread than what [enqueue] is called from for Jvm & Native.
     * */
    public actual interface Processor {

        /**
         * Enqueues the [Action] for execution.
         *
         * **NOTE:** If the returned [EnqueuedJob] gets cancelled,
         * [onFailure] will be invoked with [CancellationException]
         * indicating normal behavior.
         *
         * **NOTE:** If the returned [EnqueuedJob] gets interrupted,
         * [onFailure] will be invoked with [InterruptedException].
         * For example, if [StartDaemon] is enqueued and immediately
         * after, [StopDaemon] is enqueued too. [StopDaemon] will be
         * at the top of the stack by the time the processor starts.
         * The [StopDaemon] job will be executed, and all [StartDaemon]
         * or [RestartDaemon] jobs beneath will be interrupted. If there
         * are multiple [StopDaemon] jobs also enqueued, those jobs will
         * be attached as children to the executing [StopDaemon] job and
         * complete successfully alongside the job being executed. The
         * converse scenario where [StartDaemon] or [RestartDaemon] is
         * being executed, is the same whereby [StopDaemon] jobs are all
         * interrupted, and [StartDaemon] or [RestartDaemon] jobs are
         * attached as children, completing alongside the job that is
         * executing.
         *
         * @return [EnqueuedJob]
         * @see [OnFailure]
         * @see [OnSuccess]
         * @see [executeAsync]
         * @see [executeSync]
         * */
        public actual fun enqueue(
            action: Action,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Unit>,
        ): EnqueuedJob
    }

    public actual companion object {

        /**
         * Enqueues the [action], suspending the current coroutine
         * until completion or cancellation/error.
         *
         * @see [Processor.enqueue]
         * @see [startDaemonAsync]
         * @see [stopDaemonAsync]
         * @see [restartDaemonAsync]
         * @see [executeSync]
         * */
        @JvmStatic
        @Throws(Throwable::class)
        public actual suspend fun <T: Processor> T.executeAsync(action: Action): T {
            @Suppress("DEPRECATION_ERROR")
            action.awaitAsync(this::enqueue)
            return this
        }

        /**
         * Enqueues the [action], blocking the current thread
         * until completion or cancellation/error.
         *
         * **NOTE:** This is a blocking call and should be invoked from
         * a background thread.
         *
         * @see [Processor.enqueue]
         * @see [startDaemonSync]
         * @see [stopDaemonSync]
         * @see [restartDaemonSync]
         * @see [executeAsync]
         * @param [cancellation] optional callback which is invoked
         *   after every thread sleep (so, multiple times) in order
         *   to trigger job cancellation if a non-null exception
         *   value is returned.
         * */
        @JvmStatic
        @JvmOverloads
        @Throws(Throwable::class)
        public fun <T: Processor> T.executeSync(
            action: Action,
            cancellation: (() -> CancellationException?)? = null,
        ): T {
            @Suppress("DEPRECATION_ERROR")
            action.awaitSync(this::enqueue, cancellation)
            return this
        }

        /**
         * Starts the tor daemon, suspending the current coroutine
         * until completion or cancellation/error.
         *
         * @see [Processor.enqueue]
         * @see [Action.StartDaemon]
         * @see [startDaemonSync]
         * */
        @JvmStatic
        @Throws(Throwable::class)
        public actual suspend inline fun <T: Processor> T.startDaemonAsync(): T = executeAsync(StartDaemon)

        /**
         * Starts the tor daemon, blocking the current thread
         * until completion or cancellation/error.
         *
         * **NOTE:** This is a blocking call and should be invoked from
         * a background thread.
         *
         * @see [Processor.enqueue]
         * @see [Action.StartDaemon]
         * @see [startDaemonAsync]
         * @param [cancellation] optional callback which is invoked
         *   after every thread sleep (so, multiple times) in order
         *   to trigger job cancellation if a non-null exception
         *   value is returned.
         * */
        @JvmStatic
        @JvmOverloads
        @Throws(Throwable::class)
        @Suppress("NOTHING_TO_INLINE")
        public inline fun <T: Processor> T.startDaemonSync(
            noinline cancellation: (() -> CancellationException?)? = null,
        ): T = executeSync(StartDaemon, cancellation)

        /**
         * Stops the tor daemon, suspending the current coroutine
         * until completion or cancellation/error.
         *
         * @see [Processor.enqueue]
         * @see [Action.StopDaemon]
         * @see [stopDaemonSync]
         * */
        @JvmStatic
        @Throws(Throwable::class)
        public actual suspend inline fun <T: Processor> T.stopDaemonAsync(): T = executeAsync(StopDaemon)

        /**
         * Stops the tor daemon, blocking the current thread
         * until completion or cancellation/error.
         *
         * **NOTE:** This is a blocking call and should be invoked from
         * a background thread.
         *
         * @see [Processor.enqueue]
         * @see [Action.StopDaemon]
         * @see [stopDaemonAsync]
         * @param [cancellation] optional callback which is invoked
         *   after every thread sleep (so, multiple times) in order
         *   to trigger job cancellation if a non-null exception
         *   value is returned.
         * */
        @JvmStatic
        @JvmOverloads
        @Throws(Throwable::class)
        @Suppress("NOTHING_TO_INLINE")
        public inline fun <T: Processor> T.stopDaemonSync(
            noinline cancellation: (() -> CancellationException?)? = null,
        ): T = executeSync(StopDaemon, cancellation)

        /**
         * Stops and then starts the tor daemon, suspending the
         * current coroutine until completion or cancellation/error.
         *
         * @see [Processor.enqueue]
         * @see [Action.RestartDaemon]
         * @see [restartDaemonSync]
         * */
        @JvmStatic
        @Throws(Throwable::class)
        public actual suspend inline fun <T: Processor> T.restartDaemonAsync(): T = executeAsync(RestartDaemon)

        /**
         * Stops and then starts the tor daemon, blocking the
         * current thread until completion or cancellation/error.
         *
         * **NOTE:** This is a blocking call and should be invoked from
         * a background thread.
         *
         * @see [Processor.enqueue]
         * @see [Action.RestartDaemon]
         * @see [restartDaemonAsync]
         * @param [cancellation] optional callback which is invoked
         *   after every thread sleep (so, multiple times) in order
         *   to trigger job cancellation if a non-null exception
         *   value is returned.
         * */
        @JvmStatic
        @JvmOverloads
        @Throws(Throwable::class)
        @Suppress("NOTHING_TO_INLINE")
        public inline fun <T: Processor> T.restartDaemonSync(
            noinline cancellation: (() -> CancellationException?)? = null,
        ): T = executeSync(RestartDaemon, cancellation)
    }
}
