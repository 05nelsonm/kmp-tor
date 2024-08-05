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

import android.app.Activity
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Binder as AndroidBinder
import android.os.IBinder
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.EXECUTE.CMD.observeSignalNewNym
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.d
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.e
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.w
import io.matthewnelson.kmp.tor.runtime.TorRuntime.ServiceFactory.Binder as ServiceFactoryBinder
import io.matthewnelson.kmp.tor.runtime.core.EnqueuedJob
import io.matthewnelson.kmp.tor.runtime.core.Executable
import io.matthewnelson.kmp.tor.runtime.core.OnFailure
import io.matthewnelson.kmp.tor.runtime.core.OnSuccess
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.service.internal.AndroidNetworkObserver
import io.matthewnelson.kmp.tor.runtime.service.internal.ApplicationContext
import io.matthewnelson.kmp.tor.runtime.service.internal.ApplicationContext.Companion.toApplicationContext
import io.matthewnelson.kmp.tor.runtime.service.internal.SynchronizedInstance
import kotlinx.coroutines.*
import kotlin.concurrent.Volatile
import kotlin.system.exitProcess

@OptIn(ExperimentalKmpTorApi::class)
internal class TorService internal constructor(): Service() {

    private class AndroidServiceFactory(
        private val appContext: ApplicationContext,
        config: TorServiceConfig,
        instanceUIConfig: AbstractTorServiceUI.Config?,
        initializer: Initializer,
    ): TorRuntime.ServiceFactory(initializer) {

        private val connection = Connection(binder, config, instanceUIConfig)

        @Throws(RuntimeException::class)
        protected override fun startService() {
            val appContext = appContext.get()
            val intent = Intent(appContext, TorService::class.java)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // API 25-
                appContext.startService(intent)
                intent.bindService()
                return
            }

            // API 26+
            if (connection.config !is TorServiceConfig.Foreground<*, *>) {
                appContext.startService(intent)
                intent.bindService()
                return
            }

            val threw: IllegalStateException? = try {
                appContext.startForegroundService(intent)

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
            instanceUIConfig: AbstractTorServiceUI.Config?,
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

    private abstract class Binder: AndroidBinder() {
        public abstract fun inject(conn: Connection)
    }

    private class Connection(
        @JvmField
        public val binder: ServiceFactoryBinder,
        @JvmField
        public val config: TorServiceConfig,
        @JvmField
        public val instanceUIConfig: AbstractTorServiceUI.Config?,
    ): ServiceConnection {

        public override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service !is Binder) return
            service.inject(this)
        }

        public override fun onServiceDisconnected(name: ComponentName?) {}
    }

    private val appContext: ApplicationContext by lazy { toApplicationContext() }

    private val holders = SynchronizedInstance.of(LinkedHashMap<ServiceFactoryBinder, Holder?>(1, 1.0f))

    private val serviceJob = SupervisorJob()
    private val serviceScope by lazy {
        CoroutineScope(context =
            CoroutineName("TorServiceScope@${hashCode()}")
            + serviceJob
            + Dispatchers.Main.immediate
        )
    }

    @Volatile
    private var networkObserver: AndroidNetworkObserver? = null
    @Volatile
    private var ui: TorServiceUI<*, *>? = null

    // Gets set to whatever config is on first bind
    private var stopServiceOnTaskRemoved: Boolean = true
    // Gets set to whatever config is on first bind
    private var exitProcessIfTaskRemoved: Boolean = false

    private var taskReturnMonitor: TaskReturnMonitor? = null

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

                    service.stopServiceOnTaskRemoved = conn.config.stopServiceOnTaskRemoved

                    executables.add(Executable {
                        conn.binder.lce(Lifecycle.Event.OnCreate(service))
                        conn.binder.lce(Lifecycle.Event.OnStart(service))
                        conn.binder.lce(Lifecycle.Event.OnBind(service))
                        service.networkObserver?.let { o -> conn.binder.lce(Lifecycle.Event.OnCreate(o)) }
                        service.ui?.let { ui -> conn.binder.lce(Lifecycle.Event.OnCreate(ui)) }
                    })

                    if (conn.config.useNetworkStateObserver) {
                        val observer = try {
                            AndroidNetworkObserver.of(service, service.serviceJob)
                        } catch (e: IllegalStateException) {
                            // Configured to be used, but permission was missing.
                            executables.add(Executable {
                                conn.binder.e(e)

                                val appContext = appContext.get()
                                appContext.unbindService(conn)
                                conn.binder.lce(Lifecycle.Event.OnUnbind(service))
                                appContext.stopService(Intent(appContext, TorService::class.java))
                            })
                            null
                        }

                        if (observer == null) return@withLock executables

                        service.networkObserver = observer
                    }

                    // TorServiceConfig is a singleton and is the same for all
                    // AndroidServiceFactory. If this is the first bind for
                    // TorService and config is an instance of Foreground, need
                    // to instantiate and set a new instance before the Holder
                    // gets created.
                    if (conn.config is TorServiceConfig.Foreground<*, *>) {
                        service.exitProcessIfTaskRemoved = conn.config.exitProcessIfTaskRemoved

                        val args = TorServiceUI.Args.of(
                            conn.config.factory.defaultConfig,
                            conn.config.factory.info,
                            service,
                            service.serviceScope,
                        )

                        val ui = try {
                            conn.config.factory.newInstanceUI(args)
                        } catch (e: IllegalStateException) {
                            // Implementation of TorServiceUI.Factory is bad.
                            // Report error and shutdown TorService.
                            executables.add(Executable {
                                conn.binder.e(e)

                                val appContext = appContext.get()
                                appContext.unbindService(conn)
                                conn.binder.lce(Lifecycle.Event.OnUnbind(service))
                                appContext.stopService(Intent(appContext, TorService::class.java))
                            })
                            null
                        }

                        if (ui == null) return@withLock executables

                        service.ui = ui
                    }
                } else {
                    executables.add(Executable {
                        conn.binder.lce(Lifecycle.Event.OnBind(service))
                    })
                }

                val holder = Holder(conn)
                put(conn.binder, holder)

                executables.add(Executable {
                    // Initialize lazy runtime value (outside of lock lambda)
                    holder.runtime
                })

                executables
            }.forEach { it.execute() }
        }
    }

    private fun isDestroyed(): Boolean = !serviceJob.isActive

    public override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    public override fun onCreate() {
        super.onCreate()
        appContext
        serviceScope.launch {
            // TODO: stop service if nothing binds within 250ms
        }
    }

    public override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    public override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        TaskReturnMonitor()

        holders.withLock { entries.toImmutableSet() }.map { (binder, holder) ->
            binder.lce(Lifecycle.Event.OnRemoved(application))
            holder
        }.forEach { holder ->
            if (holder == null) return@forEach
            if (!stopServiceOnTaskRemoved) return@forEach
            holder.runtime.destroy()
        }
    }

    public override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()

        holders.withLock { entries.toImmutableSet() }.map { (binder, holder) ->
            holder?.runtime?.destroy()
            binder
        }.forEach { binder ->
            // Notify all instances that we're finally destroyed
            networkObserver?.let { o -> binder.lce(Lifecycle.Event.OnDestroy(o)) }
            ui?.let { ui -> binder.lce(Lifecycle.Event.OnDestroy(ui)) }
            binder.lce(Lifecycle.Event.OnDestroy(this))
        }

        // Clean up
        holders.withLock { clear() }
        networkObserver = null
        ui = null

        if (taskReturnMonitor != null) {
            // So, task was removed and did not return.
            //
            // Monitor only sets itself in following scenarios:
            //  - Foreground Service
            //  - TorServiceConfig.Foreground.exitProcessIfTaskRemoved = true
            exitProcess(0)
        }
    }

    private inner class Holder(private val conn: Connection) {

        private val binder get() = conn.binder

        private val instanceJob: CompletableJob?
        private val instanceState: AbstractTorServiceUI.InstanceState<*>?
        private var newInstanceStateThrew: RuntimeException? = null

        @Volatile
        private var processorAction: Action.Processor? = null
        @Volatile
        private var processorCmd: TorCmd.Unprivileged.Processor? = null

        init {
            val ui = ui
            if (ui == null) {
                instanceJob = null
                instanceState = null
            } else {
                val factory = conn.config.let { config ->
                    if (config is TorServiceConfig.Foreground<*, *>) {
                        config.factory
                    } else {
                        null
                    }
                }

                fun log(lazyMessage: () -> String) {
                    if (processorAction == null) return
                    if (factory?.debug != true) return
                    if (!runtime.environment().debug) return

                    binder.d(null, lazyMessage())
                }

                val pair = try {
                    ui.newInstanceState(
                        conn.instanceUIConfig,
                        binder.fid,
                        debugger = debugger@ {
                            if (factory == null) return@debugger null
                            if (processorAction == null) return@debugger null
                            ::log
                        },
                        observeSignalNewNym = observe@ { tag, executor, onEvent ->
                            if (processorAction == null) return@observe null
                            runtime.observeSignalNewNym(tag, executor, onEvent)
                        },
                        processorAction = { processorAction },
                        processorTorCmd = { processorCmd }
                    )
                } catch (e: RuntimeException) {
                    newInstanceStateThrew = e
                    null
                }

                instanceJob = pair?.first
                instanceState = pair?.second
            }
        }

        // Want to lazily instantiate so that the holders map
        // entry can be created before we start calling things.
        public val runtime: Lifecycle.DestroyableTorRuntime by lazy {
            if (instanceState != null) {
                binder.lce(Lifecycle.Event.OnCreate(instanceState))
            }

            val instance = binder.onBind(
                serviceEvents = instanceState?.events ?: emptySet(),
                serviceObserverNetwork = networkObserver,
                serviceObserversTorEvent = instanceState?.observersTorEvent ?: emptySet(),
                serviceObserversRuntimeEvent = instanceState?.observersRuntimeEvent ?: emptySet(),
            )

            if (instanceJob != null) {
                processorAction = object : Action.Processor {
                    override fun enqueue(
                        action: Action,
                        onFailure: OnFailure,
                        onSuccess: OnSuccess<Unit>
                    ): EnqueuedJob = instance.enqueue(
                        action,
                        onFailure,
                        onSuccess,
                    )
                }
                processorCmd = object : TorCmd.Unprivileged.Processor {
                    override fun <Success : Any> enqueue(
                        cmd: TorCmd.Unprivileged<Success>,
                        onFailure: OnFailure,
                        onSuccess: OnSuccess<Success>
                    ): EnqueuedJob = instance.enqueue(
                        cmd,
                        onFailure,
                        onSuccess,
                    )
                }
                instance.invokeOnDestroy {
                    processorAction = null
                    processorCmd = null
                    instanceJob.cancel()
                    binder.lce(Lifecycle.Event.OnDestroy(instanceState!!))
                }
            }

            instance.invokeOnDestroy {
                val service = this@TorService

                holders.withLock {
                    val isThis = get(binder) == this@Holder
                    if (!isThis) return@withLock null

                    // Leave the singleton binder, but de-reference
                    // this holder with the destroyed runtime.
                    put(binder, null)

                    if (service.isDestroyed()) return@withLock null

                    val isLastInstance = count { it.value != null } == 0

                    ui?.let { ui ->
                        // Must call stopForeground before unbindService if this
                        // is the last instance running. Otherwise, notification
                        // will hang around for API 25- if the task is removed b/c
                        // unbindService will immediately start killing things.
                        if (isLastInstance) {
                            ui.stopForeground()
                        }
                    }

                    val appContext = appContext.get()
                    appContext.unbindService(conn)

                    if (isLastInstance) {
                        appContext.stopService(Intent(appContext, TorService::class.java))
                    }

                    Executable {
                        binder.lce(Lifecycle.Event.OnUnbind(service))
                    }
                }?.execute()
            }

            newInstanceStateThrew?.let { t ->
                binder.e(t)
                serviceScope.launch { instance.destroy() }
            }

            instance
        }
    }

    // Will set and register itself, if applicable. See onTaskRemoved & onDestroy
    private inner class TaskReturnMonitor: Application.ActivityLifecycleCallbacks {

        init {
            if (ui != null && exitProcessIfTaskRemoved) {
                taskReturnMonitor = this
                application.registerActivityLifecycleCallbacks(this)
            } else {
                taskReturnMonitor = null
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            taskReturnMonitor = null
            application.unregisterActivityLifecycleCallbacks(this)
            holders.withLock { keys.toImmutableSet() }.forEach { binder ->
                binder.lce(Lifecycle.Event.OnReturned(application))
            }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    public override fun toString(): String = "TorService@${hashCode()}"
}
