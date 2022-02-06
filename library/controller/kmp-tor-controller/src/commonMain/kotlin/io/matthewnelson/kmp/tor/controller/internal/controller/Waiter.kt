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
package io.matthewnelson.kmp.tor.controller.internal.controller

import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.exceptions.ControllerShutdownException
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic

@OptIn(InternalTorApi::class)
internal sealed interface Waiter {
    @Throws(CancellationException::class, ControllerShutdownException::class)
    suspend fun getResponse(): List<ReplyLine.SingleLine>
    fun setResponse(response: List<ReplyLine.SingleLine>)

    companion object {
        @JvmSynthetic
        internal fun newInstance(isConnected: () -> Boolean): Waiter =
            RealWaiter(isConnected)
    }
}

@OptIn(InternalTorApi::class)
private class RealWaiter(
    val isConnected: () -> Boolean
): Waiter {
    private val response: AtomicRef<List<ReplyLine.SingleLine>?> = atomic(null)
    private val lock = Mutex()

    @Throws(CancellationException::class, ControllerShutdownException::class)
    override suspend fun getResponse(): List<ReplyLine.SingleLine> {
        lock.withLock {
            while (true) {
                response.value?.let { return it }
                currentCoroutineContext().ensureActive()
                if (!isConnected.invoke()) throw ControllerShutdownException("Tor Stopped")
                delay(50L)
            }
        }
    }

    override fun setResponse(response: List<ReplyLine.SingleLine>) {
        this.response.value = response
    }
}
