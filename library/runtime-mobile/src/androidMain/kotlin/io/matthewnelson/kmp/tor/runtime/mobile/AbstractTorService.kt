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
@file:Suppress("UnnecessaryOptInAnnotation")

package io.matthewnelson.kmp.tor.runtime.mobile

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder as AndroidBinder
import android.os.IBinder
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.e
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.mobile.internal.PersistentKeyMap
import io.matthewnelson.kmp.tor.runtime.TorRuntime.ServiceFactory.Binder as TorBinder

@OptIn(ExperimentalKmpTorApi::class)
internal sealed class AbstractTorService: Service() {

    @Volatile
    private var _isDestroyed: Boolean = false
    private val holders = PersistentKeyMap<TorBinder, Holder>()

    private val binder = object : Binder() {
        override fun inject(conn: Connection) {
            val service = this@AbstractTorService

            if (_isDestroyed) {
                conn.binder.e(IllegalStateException("$service cannot be bound to. isDestroyed[true]"))
                return
            }
            holders[conn.binder]?.let { holder ->
                // It's currently being destroyed
                if (holder.runtime.isDestroyed()) return@let

                conn.binder.w(service, "${holder.runtime} is still active, but onServiceConnected was called")
                return
            }

            // This is the first bind for this TorService instance
            if (holders.sizeKeys == 0) {
                conn.binder.lce(Lifecycle.Event.OnCreate(service))
                conn.binder.lce(Lifecycle.Event.OnStart(service))
            }

            val holder = Holder(conn)
            holders[conn.binder] = holder

            // Initialize lazy value
            holder.runtime
        }
    }

    private abstract class Binder: AndroidBinder() {
        public abstract fun inject(conn: Connection)
    }

    protected class Connection(val binder: TorBinder): ServiceConnection {
        public override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is Binder) return
            service.inject(this)
        }

        public override fun onServiceDisconnected(name: ComponentName?) {}
    }

    public final override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    public final override fun onCreate() {
        super.onCreate()
        // TODO: stop service if nothing binds within 100ms
    }

    public final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    public final override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        holders.keys.forEach { binder ->
            binder.lce(Lifecycle.Event.OnRemoved(application))
        }
    }

    public final override fun onDestroy() {
        _isDestroyed = true
        super.onDestroy()

        holders.entries.map { (binder, holder) ->
            holder?.runtime?.destroy()
            binder
        }.forEach { binder ->
            // Notify all instances that we're finally destroyed
            binder.lce(Lifecycle.Event.OnDestroy(this))
        }

        // Clean up
        holders.clear()
    }

    private inner class Holder(private val conn: Connection) {

        private val binder get() = conn.binder

        // Want to lazily instantiate so that the PersistentKeyMap
        // entry is created before we start calling things.
        public val runtime: Lifecycle.DestroyableTorRuntime by lazy {
            binder.onBind(
                serviceEvents = emptySet(),
                serviceObserverNetwork = null,
                serviceObserversTorEvent = emptySet(),
                serviceObserversRuntimeEvent = emptySet(),
            ).apply {
                invokeOnDestroy {
                    val wasRemoved = holders.removeInstance(binder, this@Holder)
                    if (!wasRemoved || _isDestroyed) return@invokeOnDestroy

                    unbindService(conn)
                    binder.lce(Lifecycle.Event.OnUnbind(this@AbstractTorService))
                }
                invokeOnDestroy {
                    if (_isDestroyed) return@invokeOnDestroy
                    if (holders.sizeValues != 0) return@invokeOnDestroy

                    // Last instance destroyed. Kill it.
                    stopSelf()
                }
            }
        }

        init {
            binder.lce(Lifecycle.Event.OnBind(this@AbstractTorService))
        }
    }

    public final override fun toString(): String = "TorService@${hashCode()}"
}
