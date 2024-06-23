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
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.service.internal.notification.ServiceNotification

@OptIn(ExperimentalKmpTorApi::class)
internal class TorService internal constructor(): AbstractTorService() {

    private class AndroidServiceFactory(
        private val app: Application,
        private val enableForeground: Boolean,
        instanceConfig: ServiceNotification.Config,
        initializer: Initializer,
    ): TorRuntime.ServiceFactory(initializer) {

        private val connection = Connection(binder, instanceConfig)

        @Throws(RuntimeException::class)
        protected override fun startService() {
            val intent = Intent(app, TorService::class.java)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // API 25-
                app.startService(intent)
                intent.bindService()
                return
            }

            // API 26+
            if (!enableForeground) {
                app.startService(intent)
                intent.bindService()
                return
            }

            val threw: IllegalStateException? = try {
                app.startForegroundService(intent)

                // Will only run if startForegroundService does not throw
                intent.bindService()
                null
            } catch (e: IllegalStateException) {
                e
            }

            // Good start.
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
            return app.bindService(this, connection, Context.BIND_AUTO_CREATE)
        }
    }

    internal companion object {

        @JvmSynthetic
        @Throws(Resources.NotFoundException::class)
        internal fun Application.serviceFactoryLoader(
            block: ThisBlock<TorServiceConfig.OverridesBuilder>,
        ): TorRuntime.ServiceFactory.Loader {
            val app = this

            val config = TorServiceConfig.getMetaData(app)
            val instanceConfig = TorServiceConfig
                .OverridesBuilder
                .build(app, config, block)

            return object : TorRuntime.ServiceFactory.Loader() {

                protected override fun loadProtected(
                    initializer: TorRuntime.ServiceFactory.Initializer,
                ): TorRuntime.ServiceFactory = AndroidServiceFactory(
                    app = app,
                    enableForeground = config.enableForeground,
                    instanceConfig = instanceConfig,
                    initializer = initializer,
                )
            }
        }
    }
}
