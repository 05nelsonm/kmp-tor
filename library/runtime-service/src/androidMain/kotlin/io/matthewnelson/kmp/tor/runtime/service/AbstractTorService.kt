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

package io.matthewnelson.kmp.tor.runtime.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder as AndroidBinder
import android.os.IBinder
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.e
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.service.internal.SynchronizedInstance
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.AndroidServiceNotification
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.ServiceNotification
import kotlinx.coroutines.*
import io.matthewnelson.kmp.tor.runtime.TorRuntime.ServiceFactory.Binder as TorBinder

@OptIn(ExperimentalKmpTorApi::class)
internal sealed class AbstractTorService: Service() {

    private val holders = SynchronizedInstance.of(LinkedHashMap<TorBinder, Holder?>(1, 1.0f))

    private val config by lazy { TorServiceConfig.getMetaData(this) }

    private val supervisor = SupervisorJob()
    private val scope by lazy {
        CoroutineScope(context =
            CoroutineName("TorServiceScope@${hashCode()}")
            + supervisor
            + Dispatchers.Main.immediate
        )
    }

    private val notification: AndroidServiceNotification? by lazy {
        AndroidServiceNotification.of(
            service = this,
            config = config,
            serviceScope = scope,
        )
    }

    private val binder = object : Binder() {
        override fun inject(conn: Connection) {
            val service = this@AbstractTorService

            holders.withLock {
                val executables = ArrayList<Executable>(1)

                if (service.isDestroyed()) {
                    executables.add(Executable {
                        conn.binder.e(IllegalStateException("$service cannot be bound to. isDestroyed[true]"))
                    })
                    return@withLock executables
                }

                get(conn.binder)?.let { holder ->
                    // It's being destroyed right now, but its completion
                    // callback has not de-referenced itself from holders
                    // for this connection yet (we're holding the lock right
                    // now).
                    //
                    // Want to continue here so that it will fail to remove
                    // itself (because it's not there) and will not disconnect.
                    if (holder.runtime.isDestroyed()) return@let

                    executables.add(Executable {
                        conn.binder.w(
                            service,
                            "${holder.runtime} is still active, but onServiceConnected was called"
                        )
                    })
                    return@withLock executables
                }

                // This is the first bind for this TorService instance
                if (size == 0) {
                    executables.add(Executable {
                        conn.binder.lce(Lifecycle.Event.OnCreate(service))
                        conn.binder.lce(Lifecycle.Event.OnStart(service))
                    })
                }

                val holder = Holder(conn)
                put(conn.binder, holder)

                // Initialize lazy value
                executables.add(Executable {
                    conn.binder.lce(Lifecycle.Event.OnBind(service))
                    holder.runtime
                })

                executables
            }.forEach { it.execute() }
        }
    }

    private abstract class Binder: AndroidBinder() {
        public abstract fun inject(conn: Connection)
    }

    protected class Connection(
        val binder: TorBinder,
        val instanceConfig: ServiceNotification.Config,
    ): ServiceConnection {
        public override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is Binder) return
            service.inject(this)
        }

        public override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private fun isDestroyed(): Boolean = !supervisor.isActive

    public final override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    public final override fun onCreate() {
        super.onCreate()
        // Initialize things
        notification
        scope.launch {
            // TODO: stop service if nothing binds within 100ms
        }
    }

    public final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    public final override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        holders.withLock {
            keys.map { binder ->
                Executable {
                    binder.lce(Lifecycle.Event.OnRemoved(application))
                }
            }
        }.forEach { it.execute() }
    }

    public final override fun onDestroy() {
        scope.cancel()
        super.onDestroy()

        holders.withLock { entries.toImmutableSet() }.map { (binder, holder) ->
            holder?.runtime?.destroy()
            binder
        }.forEach { binder ->
            // Notify all instances that we're finally destroyed
            binder.lce(Lifecycle.Event.OnDestroy(this))
        }

        // Clean up
        holders.withLock { clear() }
    }

    private inner class Holder(private val conn: Connection) {

        private val binder get() = conn.binder

        private val instanceView by lazy {
            val notification = notification ?: return@lazy null

            notification.InstanceView(
                fid = binder,
                config = conn.instanceConfig,
                lazyRuntime = { runtime }
            )
        }

        // Want to lazily instantiate so that the holders map
        // entry can be created before we start calling things.
        public val runtime: Lifecycle.DestroyableTorRuntime by lazy {
            binder.onBind(
                serviceEvents = instanceView?.requiredEventTor ?: emptySet(),
                serviceObserverNetwork = null,
                serviceObserversTorEvent = instanceView?.observersEventTor ?: emptySet(),
                serviceObserversRuntimeEvent = instanceView?.observersEventRuntime ?: emptySet(),
            ).apply {
                invokeOnDestroy {
                    holders.withLock {
                        val isThis = get(binder) == this@Holder

                        if (!isThis) return@withLock null

                        // Leave the singleton binder, but de-reference
                        // this holder with the destroyed runtime.
                        put(binder, null)

                        if (this@AbstractTorService.isDestroyed()) return@withLock null

                        application.unbindService(conn)
                        Executable {
                            binder.lce(Lifecycle.Event.OnUnbind(this@AbstractTorService))
                        }
                    }?.execute()
                }
                invokeOnDestroy {
                    if (this@AbstractTorService.isDestroyed()) return@invokeOnDestroy

                    val isLastInstance = holders.withLock { count { it.value != null } } == 0
                    if (!isLastInstance) return@invokeOnDestroy

                    // Last instance destroyed. Kill it.
                    application.stopService(Intent(application, TorService::class.java))
                }
            }
        }
    }

    public final override fun toString(): String = "TorService@${hashCode()}"
}
