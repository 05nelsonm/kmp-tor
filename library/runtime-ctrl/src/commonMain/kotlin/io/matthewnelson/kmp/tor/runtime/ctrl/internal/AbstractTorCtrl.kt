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
package io.matthewnelson.kmp.tor.runtime.ctrl.internal

import io.matthewnelson.kmp.tor.common.api.InternalKmpTorApi
import io.matthewnelson.kmp.tor.common.core.SynchronizedObject
import io.matthewnelson.kmp.tor.common.core.synchronized
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import io.matthewnelson.kmp.tor.runtime.ctrl.internal.Debugger.Companion.d
import kotlin.concurrent.Volatile

@OptIn(InternalKmpTorApi::class)
internal abstract class AbstractTorCtrl internal constructor(
    staticTag: String?,
    observers: Set<TorEvent.Observer>,
    defaultExecutor: OnEvent.Executor,
    handler: UncaughtException.Handler,
):  AbstractTorCmdQueue(staticTag, observers, defaultExecutor, handler),
    TorCtrl
{

    @Volatile
    private var _destroyCallbacks: LinkedHashSet<ItBlock<TorCtrl>>? = LinkedHashSet(1, 1.0f)
    private val lock = SynchronizedObject()

    public final override fun invokeOnDestroy(handle: ItBlock<TorCtrl>): Disposable {
        val wasAdded = synchronized(lock) {
            _destroyCallbacks?.add(handle)
        }

        if (wasAdded == null) {
            // invoke immediately. Do not leak destroyed handler.
            handle.invokeDestroyed(UncaughtException.Handler.THROW, isImmediate = true)
            return Disposable.noOp()
        }

        if (!wasAdded) return Disposable.noOp()

        return Disposable.Once.of {
            synchronized(lock) {
                _destroyCallbacks?.remove(handle)
            }
        }
    }

    // @Throws(UncaughtException::class)
    override fun onDestroy(): Boolean {
        // check quick win
        if (isDestroyed()) return false

        val wasDestroyed = handler.withSuppression {
            // Potential for super invocation to throw when cancelling
            // jobs if Factory.handler is THROW. Need to wrap it up in
            // order to ensure destroy callbacks get invoked.
            tryCatch("AbstractTorCtrl.onDestroy") {
                super.onDestroy()
            }

            val callbacks = synchronized(lock) {
                val callbacks = _destroyCallbacks
                // de-reference
                _destroyCallbacks = null
                callbacks
            }

            if (!callbacks.isNullOrEmpty()) {
                LOG.d { "Invoking onDestroy callbacks" }
                val suppressed = this
                callbacks.forEach { callback -> callback.invokeDestroyed(suppressed) }
            }

            callbacks != null
        }

        return wasDestroyed
    }

    private fun ItBlock<TorCtrl>.invokeDestroyed(
        handler: UncaughtException.Handler,
        isImmediate: Boolean = false,
    ) {
        val context = "AbstractTorCtrl.invokeOnDestroy" +
            if (isImmediate) "[immediate]" else ""

        handler.tryCatch(context) { invoke(this@AbstractTorCtrl) }
    }
}
