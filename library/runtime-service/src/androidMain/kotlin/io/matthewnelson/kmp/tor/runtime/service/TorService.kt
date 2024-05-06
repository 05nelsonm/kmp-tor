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
import android.content.Context
import android.content.Intent
import androidx.startup.AppInitializer
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.*

@OptIn(ExperimentalKmpTorApi::class)
internal class TorService internal constructor(): AbstractTorService() {

    private class AndroidServiceFactory(
        private val app: Application,
        initializer: Initializer,
    ): TorRuntime.ServiceFactory(initializer) {

        private val connection = Connection(binder)

        @Throws(RuntimeException::class)
        protected override fun startService() {
            val i = Intent(app, TorService::class.java)
            app.startService(i)
            app.bindService(i, connection, Context.BIND_AUTO_CREATE)
        }
    }

    internal class Initializer internal constructor(): androidx.startup.Initializer<Initializer.Companion> {

        public override fun create(context: Context): Companion {
            val initializer = AppInitializer.getInstance(context)
            check(initializer.isEagerlyInitialized(javaClass)) {
                val classPath = "io.matthewnelson.kmp.tor.runtime.service.TorService$" + "Initializer"

                """
                    TorService.Initializer cannot be initialized lazily.
                    Please ensure that you have:
                    <meta-data
                        android:name='$classPath'
                        android:value='androidx.startup' />
                    under InitializationProvider in your AndroidManifest.xml
                """.trimIndent()
            }
            app = context.applicationContext as Application
            return Companion
        }

        public override fun dependencies(): List<Class<androidx.startup.Initializer<*>>> {
            return try {
                val clazz = Class
                    .forName("io.matthewnelson.kmp.tor.core.lib.locator.KmpTorLibLocator\$Initializer")

                @Suppress("UNCHECKED_CAST")
                listOf((clazz as Class<androidx.startup.Initializer<*>>))
            } catch (_: Throwable) {
                emptyList()
            }
        }

        internal companion object {

            @JvmSynthetic
            internal fun isInitialized(): Boolean = app != null
        }
    }

    internal companion object {

        private var app: Application? = null

        @JvmSynthetic
        internal fun loaderOrNull(): TorRuntime.ServiceFactory.Loader? {
            val app = app ?: return null

            return object : TorRuntime.ServiceFactory.Loader() {
                override fun loadProtected(
                    initializer: TorRuntime.ServiceFactory.Initializer,
                ): TorRuntime.ServiceFactory = AndroidServiceFactory(app, initializer)
            }
        }
    }
}
