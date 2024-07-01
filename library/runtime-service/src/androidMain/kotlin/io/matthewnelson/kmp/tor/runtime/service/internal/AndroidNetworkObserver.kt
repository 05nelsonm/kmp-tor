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
package io.matthewnelson.kmp.tor.runtime.service.internal

import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import io.matthewnelson.kmp.tor.runtime.NetworkObserver
import io.matthewnelson.kmp.tor.runtime.core.Disposable
import kotlinx.coroutines.Job
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.concurrent.Volatile

internal class AndroidNetworkObserver private constructor(
    private val manager: ConnectivityManager,
    private val service: Context,
    serviceJob: Job,
): NetworkObserver() {

    private val registry = SynchronizedInstance.of(ArrayList<Disposable.Once>(1))
    private val filter: IntentFilter

    init {
        val s = BigInteger(130, SecureRandom()).toString(32)
        filter = IntentFilter(s)
        @Suppress("DEPRECATION")
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
    }

    @Volatile
    private var receiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            @Suppress("DEPRECATION")
            if (intent?.action != ConnectivityManager.CONNECTIVITY_ACTION) return

            val connectivity = if (isNetworkConnected()) {
                Connectivity.Connected
            } else {
                Connectivity.Disconnected
            }

            notify(connectivity)
        }
    }

    override fun onObserversNotEmpty() {
        registry.withLock {
            val receiver = receiver ?: return@withLock

            service.register(
                receiver = receiver,
                filter = filter,
                permission = null,
                scheduler = null,
                exported = null,
            )

            add(Disposable.Once.of {
                service.unregisterReceiver(receiver)
            })
        }
    }

    override fun onObserversEmpty() {
        registry.withLock {
            while (isNotEmpty()) {
                removeAt(0).dispose()
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun isNetworkConnected(): Boolean {
        if (receiver == null) return false

        @Suppress("DEPRECATION")
        return manager.activeNetworkInfo?.isConnected ?: false
    }

    init {
        serviceJob.invokeOnCompletion {
            receiver = null
            onObserversEmpty()
        }
    }

    internal companion object {

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun of(
            service: Context,
            serviceJob: Job,
        ): AndroidNetworkObserver {
            check(service.isPermissionGranted(ACCESS_NETWORK_STATE)) {
                "Permission '$ACCESS_NETWORK_STATE' is missing"
            }

            val manager = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            return AndroidNetworkObserver(manager, service, serviceJob)
        }
    }
}
