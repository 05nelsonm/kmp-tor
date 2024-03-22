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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.tor.runtime.core.ItBlock
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.TorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlin.jvm.JvmStatic

public enum class RuntimeAction {

    /**
     * Starts the tor daemon.
     *
     * If tor is running, the [TorJob] returned
     * by [Processor.enqueue] will complete immediately with
     * success.
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
     * queued and awaiting execution will be cancelled.
     *
     * If tor is not running, the [TorJob] returned
     * by [Processor.enqueue] will complete immediately with
     * success.
     * */
    StopDaemon,

    /**
     * Stops, and then starts the tor daemon. All [TorCmd]
     * that are queued and awaiting execution will be
     * cancelled.
     *
     * If tor is not running, it will be started via [StartDaemon].
     * */
    RestartDaemon;

    public interface Processor {

        /**
         * Enqueues the [RuntimeAction] for execution.
         *
         * @see [startDaemon]
         * @see [stopDaemon]
         * @see [restartDaemon]
         * @see [enqueueAsync]
         * @see [startDaemonAsync]
         * @see [stopDaemonAsync]
         * @see [restartDaemonAsync]
         * */
        public fun enqueue(
            action: RuntimeAction,
            onFailure: ItBlock<Throwable>?,
            onSuccess: ItBlock<Unit>,
        ): TorJob
    }

    public companion object {

        /**
         * Starts the tor daemon.
         *
         * @see [StartDaemon]
         * */
        @JvmStatic
        public fun Processor.startDaemon(
            onFailure: ItBlock<Throwable>?,
            onSuccess: ItBlock<Unit>,
        ): TorJob = enqueue(StartDaemon, onFailure, onSuccess)

        /**
         * Starts the tor daemon.
         *
         * @see [StartDaemon]
         * */
        @JvmStatic
        public suspend fun Processor.startDaemonAsync(): Unit = enqueueAsync(StartDaemon)

        /**
         * Stops the tor daemon.
         *
         * @see [StopDaemon]
         * */
        @JvmStatic
        public fun Processor.stopDaemon(
            onFailure: ItBlock<Throwable>?,
            onSuccess: ItBlock<Unit>,
        ): TorJob = enqueue(StopDaemon, onFailure, onSuccess)

        /**
         * Stops the tor daemon.
         *
         * @see [StopDaemon]
         * */
        @JvmStatic
        public suspend fun Processor.stopDaemonAsync(): Unit = enqueueAsync(StopDaemon)

        /**
         * Restarts the tor daemon.
         *
         * @see [RestartDaemon]
         * */
        @JvmStatic
        public fun Processor.restartDaemon(
            onFailure: ItBlock<Throwable>?,
            onSuccess: ItBlock<Unit>,
        ): TorJob = enqueue(RestartDaemon, onFailure, onSuccess)

        /**
         * Restarts the tor daemon.
         *
         * @see [RestartDaemon]
         * */
        @JvmStatic
        public suspend fun Processor.restartDaemonAsync(): Unit = enqueueAsync(RestartDaemon)

        @JvmStatic
        public suspend fun Processor.enqueueAsync(
            action: RuntimeAction
        ): Unit = suspendCancellableCoroutine { continuation ->
            var job: TorJob? = null

            job = enqueue(
                action = action,
                onFailure = { t ->
                    continuation.resumeWithException(t)
                },
                onSuccess = { result ->
                    @OptIn(ExperimentalCoroutinesApi::class)
                    continuation.resume(result, onCancellation = { t -> job?.cancel(t) })
                }
            )

            continuation.invokeOnCancellation { t -> job.cancel(t) }
        }
    }
}
