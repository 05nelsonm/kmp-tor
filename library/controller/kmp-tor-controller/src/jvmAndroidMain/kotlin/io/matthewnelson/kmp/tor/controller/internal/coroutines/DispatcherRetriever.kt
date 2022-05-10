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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

internal actual interface DispatcherRetriever {
    @JvmSynthetic
    actual fun dispatcher(): CoroutineDispatcher?
    @get:JvmSynthetic
    actual val isClosed: Boolean
    @JvmSynthetic
    actual fun close()

    actual companion object {
        @JvmSynthetic
        internal actual fun newInstance(): DispatcherRetriever = DispatcherRetrieverJvm()
    }
}

private class DispatcherRetrieverJvm: DispatcherRetriever {

    private val dispatcher: AtomicRef<ExecutorCoroutineDispatcher?> = atomic(null)
    private val _isClosed: AtomicBoolean = atomic(false)

    override fun dispatcher(): CoroutineDispatcher? {
        return synchronized(this) {
            dispatcher.updateAndGet {
                if (!_isClosed.value) {
                    it ?: Executors.newSingleThreadExecutor().asCoroutineDispatcher()
                } else {
                    if (it != null) {
                        it.close()
                        null
                    } else {
                        null
                    }
                }
            }
        }
    }

    override val isClosed: Boolean
        get() = _isClosed.value

    override fun close() {
        synchronized(this) {
            _isClosed.value = true
            dispatcher.update {
                it?.close()
                null
            }
        }
    }

}
