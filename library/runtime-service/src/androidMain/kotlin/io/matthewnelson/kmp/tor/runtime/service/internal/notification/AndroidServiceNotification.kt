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
package io.matthewnelson.kmp.tor.runtime.service.internal.notification

import android.content.Context
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig
import kotlinx.coroutines.CoroutineScope

internal class AndroidServiceNotification
@Throws(IllegalStateException::class)
private constructor(
    private val service: Context,
    private val config: TorServiceConfig,
    serviceScope: CoroutineScope,
): ServiceNotification(
    serviceScope,
    Synthetic.INIT
) {

    override fun InstanceView.remove() {
        // TODO("Not yet implemented")
    }

    override fun InstanceView.render(old: NotificationState, new: NotificationState) {
        // TODO("Not yet implemented")
    }

    internal companion object {

        @JvmSynthetic
        @Throws(IllegalStateException::class)
        internal fun of(
            service: Context,
            config: TorServiceConfig,
            serviceScope: CoroutineScope,
        ): AndroidServiceNotification? {
            if (!config.enableForeground) return null

            return AndroidServiceNotification(
                service,
                config,
                serviceScope,
            )
        }
    }
}
