/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.controller.internal.coroutines

import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.jvm.JvmSynthetic

internal interface TorCoroutineManager: DispatcherRetriever {
    @JvmSynthetic
    fun torScope(): CoroutineScope?

    companion object {
        @JvmSynthetic
        internal fun newInstance(): TorCoroutineManager =
            RealTorCoroutineManager()
    }
}

private class RealTorCoroutineManager: SynchronizedObject(), TorCoroutineManager {

    companion object {
        const val SCOPE_NAME = "tor_controller_scope"

        private val count: AtomicInt = atomic(0)
    }

    private val retriever: DispatcherRetriever = DispatcherRetriever.newInstance()
    private val torScope: AtomicRef<Pair<CompletableJob, CoroutineScope>?> = atomic(null)

    override fun torScope(): CoroutineScope? {
        return synchronized(this) {
            torScope.updateAndGet {
                it ?: retriever.dispatcher()?.let { dispatcher ->
                    val sup = SupervisorJob()
                    Pair(
                        sup,
                        CoroutineScope(sup + dispatcher + CoroutineName(SCOPE_NAME + "_${count.getAndIncrement()}"))
                    )
                }
            }?.second
        }
    }

    override fun dispatcher(): CoroutineDispatcher? {
        return retriever.dispatcher()
    }

    override val isClosed: Boolean
        get() = retriever.isClosed

    override fun close() {
        synchronized(this) {
            torScope.update {
                it?.first?.cancel()
                retriever.close()
                null
            }
        }
    }
}

@JvmSynthetic
internal fun TorCoroutineManager.launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job? = torScope()?.launch(context, start, block)

@JvmSynthetic
internal fun <T> TorCoroutineManager.async(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Deferred<T>? = torScope()?.async(context, start, block)
