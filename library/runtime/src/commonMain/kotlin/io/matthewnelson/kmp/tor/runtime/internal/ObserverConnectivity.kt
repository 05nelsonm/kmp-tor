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
@file:Suppress("PrivatePropertyName")

package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.file.InterruptedException
import io.matthewnelson.kmp.tor.runtime.Lifecycle
import io.matthewnelson.kmp.tor.runtime.NetworkObserver
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.e
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.core.Destroyable
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.util.executeAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

internal open class ObserverConnectivity internal constructor(
    private val processor: TorCmd.Unprivileged.Processor,
    private val networkObserver: NetworkObserver,
    private val NOTIFIER: RuntimeEvent.Notifier,
    private val scope: CoroutineScope,
): OnEvent<NetworkObserver.Connectivity> {

    @Volatile
    private var _job: Job? = null

    internal fun subscribe() {
        if (!networkObserver.subscribe(this)) return
        NOTIFIER.lce(Lifecycle.Event.OnSubscribed(this))
    }

    internal fun unsubscribe() {
        if (!networkObserver.unsubscribe(this)) return
        _job?.cancel()
        NOTIFIER.lce(Lifecycle.Event.OnUnsubscribed(this))
    }

    public final override fun invoke(it: NetworkObserver.Connectivity) {
        if (processor is Destroyable && processor.isDestroyed()) return

        // NetworkObserver.notify is synchronized already

        _job?.cancel()
        _job = scope.launch {
            timedDelay(300.milliseconds)

            if (processor is Destroyable && processor.isDestroyed()) return@launch

            val setting = TorConfig.DisableNetwork.Builder {
                disable = when (it) {
                    NetworkObserver.Connectivity.Connected -> false
                    NetworkObserver.Connectivity.Disconnected -> true
                }
            }

            try {
                processor.executeAsync(TorCmd.Config.Set(setting))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (t is InterruptedException) return@launch
                NOTIFIER.e(t)
            }
        }
    }
}
