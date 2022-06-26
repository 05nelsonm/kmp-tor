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
package io.matthewnelson.kmp.tor.manager.internal

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.manager.*
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_BIND
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_CREATE
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_DESTROY
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_START_COMMAND
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_TASK_REMOVED
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_TASK_RETURNED
import io.matthewnelson.kmp.tor.manager.common.exceptions.InterruptedException
import io.matthewnelson.kmp.tor.manager.common.state.TorNetworkState
import io.matthewnelson.kmp.tor.manager.common.state.TorState
import io.matthewnelson.kmp.tor.manager.common.state.TorStateManager
import io.matthewnelson.kmp.tor.manager.internal.notification.TorServiceNotification
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.system.exitProcess

internal class TorService: Service() {

    private val binder = TorServiceBinder()
    private val managerHolder = TorManagerHolder()
    private val supervisor = SupervisorJob()
    private val serviceScope = CoroutineScope(supervisor + Dispatchers.Main.immediate)
    private val config: TorServiceConfig by lazy(LazyThreadSafetyMode.NONE) {
        TorServiceConfig.getMetaData(this)
    }
    private var isServiceDestroyed = false
    private val notification: TorServiceNotification by lazy(LazyThreadSafetyMode.NONE) {
        TorServiceNotification.newInstance(
            config,
            this,
            serviceScope,
            isServiceDestroyed = { isServiceDestroyed },
            stopService = { stopService(setLastAction = true) },
            restartTor = { managerHolder.instance?.let { manager ->
                serviceScope.launch {
                    restart(manager)
                }
            }},
            managerProvider = { managerHolder.instance }
        )
    }

    companion object: Application.ActivityLifecycleCallbacks {

        @Volatile
        private var application: Application? = null

        // RealTorManagerAndroid.hashCode(), callback
        @Volatile
        private var startQuietly: Pair<Int, () -> Unit>? = null

        @Volatile
        private var isTaskRemoved: Boolean = false
        @Volatile
        private var lastAction: TorManagerEvent.Action = TorManagerEvent.Action.Stop

        private val requiredEvents: MutableSet<TorEvent> = mutableSetOf()
        private val processorLock = Mutex()

        @JvmSynthetic
        fun addEvents(events: Set<TorEvent>?) {
            if (events != null) {
                synchronized(this) {
                    requiredEvents.addAll(events)
                }
            }
        }

        @JvmSynthetic
        fun destroy(context: Context, hashCode: Int) {
            synchronized(this) {
                requiredEvents.clear()
                if (TorServiceConfig.getMetaData(context).enableForeground) {
                    requiredEvents.add(TorEvent.BandwidthUsed)
                }

                if (startQuietly?.first == hashCode) {
                    startQuietly = null
                }
            }
        }

        private fun getEvents() =
            synchronized(this) {
                requiredEvents.toSet()
            }

        @JvmSynthetic
        internal fun init(application: Application, startTor: Pair<Int, () -> Unit>) {
            synchronized(this) {
                if (this.application == null) {
                    if (TorServiceConfig.getMetaData(application).enableForeground) {
                        requiredEvents.add(TorEvent.BandwidthUsed)
                    }
                    application.registerActivityLifecycleCallbacks(this)
                    this.application = application
                }

                this.startQuietly = startTor
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (isTaskRemoved) {
                isTaskRemoved = false
                when (lastAction) {
                    is TorManagerEvent.Action.Controller,
                    is TorManagerEvent.Action.Stop -> { /* no-op */ }

                    is TorManagerEvent.Action.Restart,
                    is TorManagerEvent.Action.Start -> {
                        startQuietly?.second?.invoke()
                    }
                }
                TorServiceController.notify(
                    TorManagerEvent.Lifecycle(application ?: activity, ON_TASK_RETURNED)
                )
            }
        }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    private inner class TorManagerHolder {
        @Volatile
        var instance: TorManager? = null
            private set

        fun getOrCreate(loader: KmpTorLoader): TorManager =
            instance ?: synchronized(this) {
                instance ?: realTorManager(
                    loader,
                    instanceId = TorServiceController.DEFAULT_INSTANCE_ID,
                    processorLock = processorLock,
                    networkObserver = networkObserverOrNull(this@TorService),
                    requiredEvents = getEvents()
                ).also { torManager ->
                    if (config.enableForeground) {
                        torManager.addListener(notification)
                    }
                    TorServiceController.prepNewTorManagerInstance(torManager)
                    instance = torManager
                }
            }
    }

    internal inner class TorServiceBinder
        : Binder(),
        TorStateManager,
        TorEventProcessor<TorManagerEvent.SealedListener>
    {
        @JvmSynthetic
        fun manager(loader: KmpTorLoader): TorManager = managerHolder.getOrCreate(loader)

        @JvmSynthetic
        suspend fun start(loader: KmpTorLoader): Result<Any?> {
            val manager = managerHolder.getOrCreate(loader)
            val result = manager.start()
            if (result.isSuccess) {
                lastAction = TorManagerEvent.Action.Start
            } else {
                if (config.enableForeground) {
                    result.exceptionOrNull()?.let { ex ->
                        delay(100L)
                        if (!manager.isDestroyed) {
                            notification.postError(ex)
                            if (isTaskRemoved) {
                                stopService()
                            }
                        }
                    } ?: stopService()
                } else {
                    stopService()
                }
            }
            return result
        }

        @JvmSynthetic
        suspend fun restart(loader: KmpTorLoader): Result<Any?> {
            val manager = managerHolder.getOrCreate(loader)
            return restart(manager)
        }

        @JvmSynthetic
        fun stop(): Result<Any?> {
            stopService(setLastAction = true)
            return Result.success("TorService stopped")
        }

        @JvmSynthetic
        override fun addListener(listener: TorManagerEvent.SealedListener): Boolean {
            return managerHolder.instance?.addListener(listener) ?: false
        }

        @JvmSynthetic
        override fun removeListener(listener: TorManagerEvent.SealedListener): Boolean {
            return managerHolder.instance?.removeListener(listener) ?: true
        }

        @JvmSynthetic
        fun debug(enable: Boolean) {
            managerHolder.instance?.debug(enable)
        }

        @get:JvmSynthetic
        override val state: TorState
            get() = managerHolder.instance?.state ?: TorState.Off
        @get:JvmSynthetic
        override val networkState: TorNetworkState
            get() = managerHolder.instance?.networkState ?: TorNetworkState.Disabled
        @get:JvmSynthetic
        override val addressInfo: TorManagerEvent.AddressInfo
            get() = managerHolder.instance?.addressInfo ?: TorManagerEvent.AddressInfo.NULL_VALUES
    }

    private suspend fun restart(manager: TorManager): Result<Any?> {
        val result = manager.restart()
        if (result.isSuccess) {
            lastAction = TorManagerEvent.Action.Restart
        } else {
            if (config.enableForeground) {
                result.exceptionOrNull()?.let { ex ->
                    if (isTaskRemoved) {
                        stopService()
                    } else if (ex !is InterruptedException) {
                        // Restart returns an interrupted exception if Stop is in the queue
                        // before the restart action runs realStart (it stops early)
                        delay(100L)
                        if (!manager.isDestroyed) {
                            notification.postError(ex)
                        }
                    }
                } ?: stopService()
            } else {
                stopService()
            }
        }
        return result
    }

    override fun onCreate() {
        super.onCreate()
        TorServiceController.notify(TorManagerEvent.Lifecycle(this, ON_CREATE))
        if (config.enableForeground) {
            notification
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        TorServiceController.notify(TorManagerEvent.Lifecycle(this, ON_BIND))
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        TorServiceController.notify(TorManagerEvent.Lifecycle(this, ON_START_COMMAND))
        // TODO: Check RealTorManagerAndroid instance and if null, stop self
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isServiceDestroyed = true
        TorServiceController.notify(TorManagerEvent.Lifecycle(this, ON_DESTROY))
        supervisor.cancel()
        managerHolder.instance?.destroy(stopCleanly = config.enableForeground || !isTaskRemoved) {
            // onCompletion
            if (config.ifForegroundExitProcessOnDestroyWhenTaskRemoved && isTaskRemoved) {
                exitProcess(0)
            }
        }
        super.onDestroy()
    }

    private fun stopService(setLastAction: Boolean = false) {
        if (setLastAction) {
            lastAction = TorManagerEvent.Action.Stop
        }

        if (config.enableForeground) {
            managerHolder.instance?.removeListener(notification)
            notification.stoppingService()
        }
        TorServiceController.unbindService(this)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        isTaskRemoved = true
        TorServiceController.notify(TorManagerEvent.Lifecycle(application, ON_TASK_REMOVED))
        if (config.stopServiceOnTaskRemoved) {
            stopService()
        } else if (config.enableForeground && notification.isError) {
            stopService()
        }
    }
}
