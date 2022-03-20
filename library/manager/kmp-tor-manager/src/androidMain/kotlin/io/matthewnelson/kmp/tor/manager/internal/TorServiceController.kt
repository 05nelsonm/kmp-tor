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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.events.TorEventProcessor
import io.matthewnelson.kmp.tor.controller.internal.controller.ListenersHandler
import io.matthewnelson.kmp.tor.manager.TorManager
import io.matthewnelson.kmp.tor.manager.TorServiceConfig
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import kotlin.Exception

@OptIn(InternalTorApi::class)
internal object TorServiceController:
    ServiceConnection,
    TorEventProcessor<TorManagerEvent.SealedListener>
{
    @Volatile
    @Suppress("ObjectPropertyName")
    private var _binderState: BinderState? = null

    @get:JvmSynthetic
    val binderState: BinderState? get() = _binderState

    private fun clear() {
        _binderState = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        if (service != null && service is TorService.TorServiceBinder) {
            _binderState = BinderState.Bound(service)
        } else {
            clear()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        clear()
    }

    @JvmSynthetic
    @Throws(RuntimeException::class)
    fun startService(context: Context) {
        val intent = Intent(context.applicationContext, TorService::class.java)

        if (
            TorServiceConfig.getMetaData(context).enableForeground &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            context.applicationContext.startForegroundService(intent)
        } else {
            context.applicationContext.startService(intent)
        }

        _binderState = BinderState.Starting
        bindService(context, intent)
    }

    @JvmSynthetic
    fun bindService(context: Context, intent: Intent) {
        context.applicationContext.bindService(
            intent,
            this,
            Context.BIND_AUTO_CREATE
        )
    }

    @JvmSynthetic
    fun unbindService(service: TorService) {
        clear()
        try {
            unbindService(service.applicationContext)
            notify(TorManagerEvent.Lifecycle(service, "onUnbind"))
        } catch (_: IllegalArgumentException) {}
    }

    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    fun unbindService(context: Context) {
        context.applicationContext.unbindService(this)
    }

    private val listeners: ListenersHandler = ListenersHandler.newInstance() {}

    @JvmSynthetic
    fun prepNewTorManagerInstance(manager: TorManager) {
        listeners.withLock {
            for (listener in this) {
                manager.addListener(listener as TorManagerEvent.SealedListener)
            }
        }
        manager.debug(debug)
    }

    @JvmSynthetic
    fun clearLocalListeners(event: TorManagerEvent) {
        listeners.withLock {
            for (listener in this) {
                try {
                    (listener as TorManagerEvent.SealedListener).onEvent(event)
                } catch (_: Exception) {}
            }
            this.clear()
        }
    }

    @JvmSynthetic
    fun notify(event: TorManagerEvent) {
        if (event is TorManagerEvent.Log.Debug && !debug) return

        listeners.withLock {
            for (listener in this) {
                try {
                    (listener as TorManagerEvent.SealedListener).onEvent(event)
                } catch (_: Exception) {}
            }
        }
    }

    override fun addListener(listener: TorManagerEvent.SealedListener): Boolean {
        val added = listeners.addListener(listener)
        binderState?.let {
            if (it is BinderState.Bound) {
                it.binder().addListener(listener)
            }
        }
        return added
    }

    override fun removeListener(listener: TorManagerEvent.SealedListener): Boolean {
        val removed = listeners.removeListener(listener)
        binderState?.let {
            if (it is BinderState.Bound) {
                it.binder().removeListener(listener)
            }
        }
        return removed
    }

    @Volatile
    private var debug: Boolean = false

    fun debug(enable: Boolean) {
        debug = enable
        binderState?.let {
            if (it is BinderState.Bound) {
                it.binder().debug(debug)
            }
        }
    }
}

internal sealed interface BinderState {
    object Starting: BinderState
    @JvmInline
    value class Bound(private val binder: TorService.TorServiceBinder): BinderState {
        @JvmSynthetic
        fun binder(): TorService.TorServiceBinder = binder
    }
}
