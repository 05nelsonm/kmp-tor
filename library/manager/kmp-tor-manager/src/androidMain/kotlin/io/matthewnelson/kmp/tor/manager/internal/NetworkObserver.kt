/*
 * Copyright (c) 2022 Matthew Nelson
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

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import io.matthewnelson.kmp.tor.manager.NetworkObserver
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_CREATE
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_REGISTER
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent.Lifecycle.Companion.ON_UNREGISTER
import io.matthewnelson.kmp.tor.manager.internal.ext.isPermissionGranted
import java.math.BigInteger
import java.security.SecureRandom

@JvmSynthetic
internal fun networkObserverOrNull(service: TorService): NetworkObserver? {
    val manager = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return null

    if (!service.isPermissionGranted(ACCESS_NETWORK_STATE)) {
        TorServiceController.notify(TorManagerEvent.Warn(
            "Permission ACCESS_NETWORK_STATE not granted. Disabling NetworkObserver"
        ))
        return null
    }

    return RealNetworkObserver(service, manager)
}

private class RealNetworkObserver(
    private val service: TorService,
    private val manager: ConnectivityManager
): NetworkObserver() {

    private val intentFilter: String = BigInteger(130, SecureRandom()).toString(32)
    private val receiver: ConnectivityReceiver by lazy { ConnectivityReceiver() }

    private inner class ConnectivityReceiver: BroadcastReceiver() {

        init {
            TorServiceController.notify(TorManagerEvent.Lifecycle(this, ON_CREATE))
        }

        override fun onReceive(context: Context?, intent: Intent?) {

            if (context == null || intent == null) return
            when (intent.action) {
                @Suppress("deprecation")
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    dispatchConnectivityChange(
                        if (isNetworkConnected()) {
                            Connectivity.Connected
                        } else {
                            Connectivity.Disconnected
                        }
                    )
                }
            }
        }
    }

    override fun onManagerAttach() {
        val filter = IntentFilter(intentFilter)
        @Suppress("deprecation")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        service.registerReceiver(receiver, filter)
        TorServiceController.notify(TorManagerEvent.Lifecycle(receiver, ON_REGISTER))
    }

    override fun onManagerDetach() {
        try {
            service.unregisterReceiver(receiver)
            TorServiceController.notify(TorManagerEvent.Lifecycle(receiver, ON_UNREGISTER))
        } catch (_: IllegalArgumentException) {}
    }

    @Suppress("deprecation", "MissingPermission")
    override fun isNetworkConnected(): Boolean {
        return manager.activeNetworkInfo?.isConnected ?: false
    }

    init {
        TorServiceController.notify(TorManagerEvent.Lifecycle(this, ON_CREATE))
    }
}
