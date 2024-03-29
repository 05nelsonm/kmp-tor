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

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.ctrl.TorCtrl
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.tryCatch
import io.matthewnelson.kmp.tor.runtime.core.UncaughtException.Handler.Companion.withSuppression
import kotlin.concurrent.Volatile

@OptIn(InternalKmpTorApi::class)
internal abstract class AbstractTorCtrl internal constructor(
    staticTag: String?,
    initialObservers: Set<TorEvent.Observer>,
    handler: UncaughtException.Handler,
):  AbstractTorCmdQueue(staticTag, initialObservers, handler),
    TorCtrl
{

    private val lock = SynchronizedObject()
    @Volatile
    private var destroyCallbacks: LinkedHashSet<ItBlock<TorCtrl>>? = LinkedHashSet(1, 1.0f)

    public final override fun invokeOnDestroy(handle: ItBlock<TorCtrl>): Disposable {
        val wasAdded = synchronized(lock) {
            destroyCallbacks?.add(handle)
        }

        if (wasAdded == null) {
            // invoke immediately. Do not leak destroyed handler.
            handle.invokeDestroyed(UncaughtException.Handler.THROW)
            return Disposable.NOOP
        }

        if (!wasAdded) return Disposable.NOOP

        return Disposable {
            if (isDestroyed()) return@Disposable

            synchronized(lock) {
                destroyCallbacks?.remove(handle)
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
                val callbacks = destroyCallbacks
                // de-reference
                destroyCallbacks = null
                callbacks
            }

            if (!callbacks.isNullOrEmpty()) {
                val suppressed = this
                callbacks.forEach { callback -> callback.invokeDestroyed(suppressed) }
            }

            callbacks != null
        }

        return wasDestroyed
    }

    private fun ItBlock<TorCtrl>.invokeDestroyed(handler: UncaughtException.Handler) {
        val context = "AbstractTorCtrl.invokeOnDestroy"
        handler.tryCatch(context) { invoke(this@AbstractTorCtrl) }
    }
}
