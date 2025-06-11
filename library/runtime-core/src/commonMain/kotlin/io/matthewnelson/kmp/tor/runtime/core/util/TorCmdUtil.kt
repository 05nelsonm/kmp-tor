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
package io.matthewnelson.kmp.tor.runtime.core.util

import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd

/**
 * Enqueues the [cmd], suspending the current coroutine until completion
 * or cancellation/error.
 *
 * See [executeSync](https://kmp-tor.matthewnelson.io/library/runtime-core/io.matthewnelson.kmp.tor.runtime.core.util/execute-sync.html)
 * @see [TorCmd.Privileged.Processor]
 * @throws [Throwable] when underlying [OnFailure] callback is invoked.
 * */
@Throws(Throwable::class)
public expect suspend fun <Success: Any> TorCmd.Privileged.Processor.executeAsync(
    cmd: TorCmd.Privileged<Success>,
): Success

/**
 * Enqueues the [cmd], suspending the current coroutine until completion
 * or cancellation/error.
 *
 * See [executeSync](https://kmp-tor.matthewnelson.io/library/runtime-core/io.matthewnelson.kmp.tor.runtime.core.util/execute-sync.html)
 * @see [TorCmd.Unprivileged.Processor]
 * @throws [Throwable] when underlying [OnFailure] callback is invoked.
 * */
@Throws(Throwable::class)
public expect suspend fun <Success: Any> TorCmd.Unprivileged.Processor.executeAsync(
    cmd: TorCmd.Unprivileged<Success>,
): Success
