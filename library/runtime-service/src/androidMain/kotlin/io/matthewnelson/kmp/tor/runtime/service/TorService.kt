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
package io.matthewnelson.kmp.tor.runtime.service


import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder as AndroidBinder
import android.os.IBinder
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.e
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.service.internal.SynchronizedInstance
import kotlinx.coroutines.*
import io.matthewnelson.kmp.tor.runtime.TorRuntime.ServiceFactory.Binder as TorBinder

@OptIn(ExperimentalKmpTorApi::class)
internal class TorService internal constructor(): Service() {

    private class AndroidServiceFactory(
        private val app: Application,
        config: TorServiceConfig,
        initializer: Initializer,
    ): TorRuntime.ServiceFactory(initializer) {

        private val connection = Connection(binder, config)

        @Throws(RuntimeException::class)
        protected override fun startService() {
            val i = Intent(app, TorService::class.java)
            app.startService(i)
            app.bindService(i, connection, Context.BIND_AUTO_CREATE)
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun Application.serviceFactoryLoader(
            config: TorServiceConfig,
        ): TorRuntime.ServiceFactory.Loader {
            val app = this

            return object : TorRuntime.ServiceFactory.Loader() {
                override fun loadProtected(
                    initializer: TorRuntime.ServiceFactory.Initializer,
                ): TorRuntime.ServiceFactory {
                    return AndroidServiceFactory(app, config, initializer)
                }
            }
        }
    }

    private val holders = SynchronizedInstance(LinkedHashMap<TorBinder, Holder?>(1, 1.0f))

    private val supervisor = SupervisorJob()
    private val scope by lazy {
        CoroutineScope(context =
            CoroutineName("TorServiceScope@${hashCode()}")
            + supervisor
            + Dispatchers.Main.immediate
        )
    }

    private fun isDestroyed(): Boolean = !supervisor.isActive

    private val binder = object : Binder() {
        override fun inject(conn: Connection) {
            val service = this@TorService

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

                    // TODO: setup notification config
                    //  if present
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

    private class Connection(
        @JvmField
        public val binder: TorBinder,
        @JvmField
        public val config: TorServiceConfig,
    ): ServiceConnection {
        public override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is Binder) return
            service.inject(this)
        }

        public override fun onServiceDisconnected(name: ComponentName?) {}
    }

    public override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    public override fun onCreate() {
        super.onCreate()
        scope.launch {
            // TODO: stop service if nothing binds within 100ms
        }
    }

    public override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    public override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        holders.withLock { entries.toImmutableSet() }.map { (binder, holder) ->
            binder.lce(Lifecycle.Event.OnRemoved(application))
            holder
        }.forEach { holder ->
            if (holder == null) return@forEach
            if (!holder.config.stopServiceOnTaskRemoved) return@forEach
            holder.runtime.destroy()
        }
    }

    public override fun onDestroy() {
        supervisor.cancel()
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

        val config: TorServiceConfig get() = conn.config
        private val binder get() = conn.binder

        // Want to lazily instantiate so that the holders map
        // entry can be created before we start calling things.
        public val runtime: Lifecycle.DestroyableTorRuntime by lazy {
            binder.onBind(
                serviceEvents = emptySet(),
                serviceObserverNetwork = null,
                serviceObserversTorEvent = emptySet(),
                serviceObserversRuntimeEvent = emptySet(),
            ).apply {
                invokeOnDestroy {
                    val service = this@TorService

                    holders.withLock {
                        val isThis = get(binder) == this@Holder

                        if (!isThis) return@withLock null

                        // Leave the singleton binder, but de-reference
                        // this holder with the destroyed runtime.
                        put(binder, null)

                        if (service.isDestroyed()) return@withLock null

                        application.unbindService(conn)
                        Executable {
                            binder.lce(Lifecycle.Event.OnUnbind(service))
                        }
                    }?.execute()
                }
                invokeOnDestroy {
                    if (this@TorService.isDestroyed()) return@invokeOnDestroy

                    val isLastInstance = holders.withLock { count { it.value != null } } == 0
                    if (!isLastInstance) return@invokeOnDestroy

                    // Last instance destroyed. Kill it.
                    application.stopService(Intent(application, TorService::class.java))
                }
            }
        }
    }

    public override fun toString(): String = "TorService@${hashCode()}"
}
