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
@file:Suppress("PrivatePropertyName")

package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.runtime.core.Destroyable
import io.matthewnelson.kmp.tor.runtime.core.Destroyable.Companion.checkDestroy
import io.matthewnelson.kmp.tor.runtime.core.ctrl.Reply
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.Debugger.Companion.d
import kotlinx.coroutines.delay
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

internal class Waiters(private val LOG: () -> Debugger?): Destroyable {

    private val rLock = ReentrantLock()
    private val waiters = ArrayList<Waiter>(1)
    @Volatile
    private var _isDestroyed: Boolean = false

    override fun isDestroyed(): Boolean = _isDestroyed

    override fun destroy() {
        if (_isDestroyed) return

        rLock.withLock {
            if (_isDestroyed) return@withLock
            _isDestroyed = true

            while (waiters.isNotEmpty()) {
                val w = waiters.removeFirst()
                w.response = ArrayList(0)
            }
        }
    }

    @Throws(IllegalStateException::class)
    internal fun respondNext(replies: ArrayList<Reply>) {
        checkDestroy()

        val waiter = rLock.withLock {
            checkDestroy()
            waiters.removeFirstOrNull()
        }

        if (waiter == null) {
            LOG().d { "No waiters found for replies$replies" }
            return
        }

        waiter.response = replies
    }

    @Throws(IllegalStateException::class)
    internal suspend fun create(
        writeCmd: suspend () -> Unit,
    ): Wait {
        checkDestroy()

        val waiter = rLock.withLockAsync {
            checkDestroy()
            writeCmd()
            val w = Waiter()
            waiters.add(w)
            w
        }

        return waiter
    }

    internal interface Wait {
        suspend operator fun invoke(): ArrayList<Reply>
    }

    private class Waiter: Wait {
        @Volatile
        var response: ArrayList<Reply>? = null

        override suspend operator fun invoke(): ArrayList<Reply> {
            while (true) {
                delay(1.milliseconds)
                response?.let { return it }
            }
        }
    }
}