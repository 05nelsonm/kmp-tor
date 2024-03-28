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

import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlin.coroutines.cancellation.CancellationException

public enum class RuntimeAction {

    /**
     * Starts the tor daemon.
     *
     * If tor is running, the [QueuedJob] returned by
     * [Processor.enqueue] will complete immediately with
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
     * If tor is not running, the [QueuedJob] returned
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

    /**
     * Base interface for implementations that process [RuntimeAction].
     *
     * **NOTE:** Implementors **MUST** process the action on a different
     * thread than what [enqueue] is called from for Jvm & Native.
     * */
    public interface Processor {

        /**
         * Enqueues the [RuntimeAction] for execution.
         *
         * **NOTE:** If the returned [QueuedJob] gets cancelled,
         * [onFailure] will be invoked with [CancellationException].
         *
         * @return [QueuedJob]
         * @see [io.matthewnelson.kmp.tor.runtime.util.executeAsync]
         * @see [io.matthewnelson.kmp.tor.runtime.util.executeSync]
         * */
        public fun enqueue(
            action: RuntimeAction,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Unit>,
        ): QueuedJob
    }
}
