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
package io.matthewnelson.kmp.tor.runtime.internal.observer

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.*
import io.matthewnelson.kmp.tor.runtime.TorCmdJob
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import kotlin.coroutines.CoroutineContext

internal inline fun <T: Processor, Data: Any?> T.newTorCmdObserver(
    tag: String?,
    executor: OnEvent.Executor?,
    onEvent: OnEvent<Data>,
    factory: (String?, OnEvent.Executor, OnEvent<Data>, (() -> T)) -> Observer<TorCmdJob>
): Disposable {
    val exec = executor ?: if (this is TorRuntime) {
        environment().defaultExecutor()
    } else {
        OnEvent.Executor.Immediate
    }

    val observer = factory(tag, exec, onEvent) { this }

    subscribe(observer)

    var disposed = false
    return Disposable {
        if (disposed) return@Disposable
        disposed = true
        unsubscribe(observer)
    }
}

internal fun <T: Processor> observeSignalNewNym(
    tag: String?,
    executor: OnEvent.Executor,
    onEvent: OnEvent<String?>,
    processor: (() -> T),
): Observer<TorCmdJob> = EXECUTE.CMD.observer(tag, OnEvent.Executor.Immediate) { job ->
    if (job.cmd != TorCmd.Signal.NewNym::class) return@observer

    val p = processor.invoke()

    var rateLimited: String? = null
    val stdout = PROCESS.STDOUT.observer(tag, OnEvent.Executor.Immediate) stdout@ { line ->
        val contains = line.contains("[notice] Rate limiting ", ignoreCase = true)
        if (!contains) return@stdout
        rateLimited = line.substringAfter(" [notice] ")
    }

    p.subscribe(stdout)
    job.invokeOnCompletion {
        p.unsubscribe(stdout)
        if (job.isError) return@invokeOnCompletion

        onEvent.notify(
            job.handlerContext(),
            rateLimited,
            executor,
        )
    }
}

private inline fun <Data: Any?> OnEvent<Data>.notify(
    handler: CoroutineContext,
    data: Data,
    executor: OnEvent.Executor,
) {
    val executable = when (executor) {
        is OnEvent.Executor.Immediate -> null
        is OnEvent.Executor.Main -> Executable {
            invoke(data)
        }
        else -> Executable.Once.of(concurrent = true) {
            invoke(data)
        }
    }

    if (executable == null) {
        invoke(data)
    } else {
        @OptIn(InternalKmpTorApi::class)
        executor.execute(handler, executable)
    }
}
