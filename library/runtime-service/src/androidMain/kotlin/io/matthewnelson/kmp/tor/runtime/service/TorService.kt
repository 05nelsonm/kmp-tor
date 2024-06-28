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

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Binder as AndroidBinder
import android.os.IBinder
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.e
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.service.internal.ApplicationContext
import io.matthewnelson.kmp.tor.runtime.service.internal.ApplicationContext.Companion.toApplicationContext
import io.matthewnelson.kmp.tor.runtime.service.internal.SynchronizedInstance
import kotlinx.coroutines.*
import io.matthewnelson.kmp.tor.runtime.TorRuntime.ServiceFactory.Binder as TorBinder

@OptIn(ExperimentalKmpTorApi::class)
internal class TorService internal constructor(): Service() {

    private class AndroidServiceFactory(
        private val appContext: ApplicationContext,
        config: TorServiceConfig,
        instanceUIConfig: TorServiceUI.Config?,
        initializer: Initializer,
    ): TorRuntime.ServiceFactory(initializer) {

        private val connection = Connection(binder, config, instanceUIConfig)

        @Throws(RuntimeException::class)
        protected override fun startService() {
            val context = appContext.get()
            val intent = Intent(context, TorService::class.java)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // API 25-
                context.startService(intent)
                intent.bindService()
                return
            }

            // API 26+
            if (connection.config !is TorServiceConfig.Foreground<*, *>) {
                context.startService(intent)
                intent.bindService()
                return
            }

            val threw: IllegalStateException? = try {
                context.startForegroundService(intent)

                // Will only run if startForegroundService does not throw
                intent.bindService()
                null
            } catch (e: IllegalStateException) {
                e
            }

            // Good start
            if (threw == null) return

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // API 30-
                throw threw
            }

            // API 31+
            if (threw !is ForegroundServiceStartNotAllowedException) {
                throw threw
            }

            // TODO: Bypass background startup restrictions for foreground service specified in
            //  https://developer.android.com/develop/background-work/services/foreground-services#bg-access-restrictions
            throw threw
        }

        @Suppress("NOTHING_TO_INLINE")
        private inline fun Intent.bindService(): Boolean {
            return appContext.get().bindService(this, connection, Context.BIND_AUTO_CREATE)
        }
    }

    internal companion object {

        @JvmSynthetic
        internal fun ApplicationContext.serviceFactoryLoader(
            config: TorServiceConfig,
            instanceUIConfig: TorServiceUI.Config?,
        ): TorRuntime.ServiceFactory.Loader {
            val context = this

            return object : TorRuntime.ServiceFactory.Loader() {
                protected override fun loadProtected(
                    initializer: TorRuntime.ServiceFactory.Initializer,
                ): TorRuntime.ServiceFactory {
                    return AndroidServiceFactory(context, config, instanceUIConfig, initializer)
                }
            }
        }
    }

    private val appContext: ApplicationContext by lazy { toApplicationContext() }

    private val holders = SynchronizedInstance.of(LinkedHashMap<TorBinder, Holder?>(1, 1.0f))

    private val supervisor = SupervisorJob()
    private val scope by lazy {
        CoroutineScope(context =
            CoroutineName("TorServiceScope@${hashCode()}")
            + supervisor
            + Dispatchers.Main.immediate
        )
    }

    private fun isDestroyed(): Boolean = !supervisor.isActive

    @Volatile
    private var ui: TorServiceUI<*>? = null

    private val binder = object : Binder() {
        public override fun inject(conn: Connection) {
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

                // First bind for this TorService instance
                if (size == 0) {

                    executables.add(Executable {
                        conn.binder.lce(Lifecycle.Event.OnCreate(service))
                        service.ui?.let { ui -> conn.binder.lce(Lifecycle.Event.OnCreate(ui)) }
                        conn.binder.lce(Lifecycle.Event.OnStart(service))
                    })

                    // TorServiceConfig is a singleton and is the same for all
                    // AndroidServiceFactory. If this is the first bind for
                    // TorService and config is an instance of Foreground, need
                    // to instantiate and set a new instance before the Holder
                    // gets created.
                    if (conn.config is TorServiceConfig.Foreground<*, *>) {
                        val args = TorServiceUI.Args.of(
                            conn.config.factory.defaultConfig,
                            conn.config.factory.info,
                            service,
                            service.scope,
                        )

                        val ui = try {
                            conn.config.factory.newInstance(args)
                        } catch (e: IllegalStateException) {
                            // Implementation of TorServiceUI.Factory is bad.
                            // Report error and shutdown TorService.
                            executables.add(Executable {
                                conn.binder.e(e)
                                application.unbindService(conn)
                                conn.binder.lce(Lifecycle.Event.OnUnbind(service))
                                application.stopService(Intent(application, TorService::class.java))
                            })
                            null
                        }

                        if (ui == null) return@withLock executables

                        service.ui = ui
                    }
                }

                val holder = Holder(conn)
                put(conn.binder, holder)

                // Initialize lazy runtime value (outside of lock lambda)
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
        @JvmField
        public val instanceUIConfig: TorServiceUI.Config?,
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
        appContext
        scope.launch {
            // TODO: stop service if nothing binds within 250ms
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
            if (!holder.stopServiceOnTaskRemoved) return@forEach
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
            ui?.let { ui -> binder.lce(Lifecycle.Event.OnDestroy(ui)) }
            binder.lce(Lifecycle.Event.OnDestroy(this))
        }

        // Clean up
        holders.withLock { clear() }
        ui = null
    }

    private inner class Holder(private val conn: Connection) {

        public val stopServiceOnTaskRemoved: Boolean get() = conn.config.stopServiceOnTaskRemoved
        private val binder get() = conn.binder

        // TODO: Create new notification instance to get bind
        //  arguments

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

                        appContext.get().unbindService(conn)
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
                    // TODO: Needs testing to ensure notification is cleared.
                    ui?.stopForeground()
                    val context = appContext.get()
                    context.stopService(Intent(context, TorService::class.java))
                }
            }
        }
    }

    public override fun toString(): String = "TorService@${hashCode()}"
}
