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
package io.matthewnelson.kmp.tor.runtime.mobile

import android.content.Context
import android.content.Intent
import androidx.startup.AppInitializer
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import kotlin.concurrent.Volatile

internal class TorService internal constructor(): AbstractTorService() {

    @OptIn(InternalKmpTorApi::class)
    private class AndroidTorRuntime private constructor(
        private val factory: TorRuntime.ServiceFactory,
        private val newIntent: () -> Intent,
    ): TorRuntime, TorEvent.Processor by factory, RuntimeEvent.Processor by factory {

        override fun environment(): TorRuntime.Environment = factory.environment

        override fun startDaemon() {
            // TODO
        }

        override fun stopDaemon() {
            // TODO
        }

        override fun restartDaemon() {
            // TODO
        }

        companion object {

            @JvmStatic
            @OptIn(InternalKmpTorApi::class)
            @Throws(IllegalStateException::class)
            fun create(factory: TorRuntime.ServiceFactory): TorRuntime {
                TorRuntime.ServiceFactory.checkInstance(factory)

                val newIntent = newIntent

                check(newIntent != null) { "TorService.Initializer must be initialized" }

                return AndroidTorRuntime(factory, newIntent)
            }
        }

        override fun removeAll(tag: String) { factory.removeAll(tag) }
        override fun removeAll(vararg events: RuntimeEvent<*>) { factory.removeAll(*events) }
        override fun clearObservers() { factory.clearObservers() }
    }

    internal class Initializer internal constructor(): androidx.startup.Initializer<Initializer.Companion> {

        override fun create(context: Context): Companion {
            if (isInitialized) return Companion

            val initializer = AppInitializer.getInstance(context)
            check(initializer.isEagerlyInitialized(javaClass)) {
                val classPath = "io.matthewnelson.kmp.tor.runtime.mobile.TorService$" + "Initializer"

                """
                    TorService.Initializer cannot be initialized lazily.
                    Please ensure that you have:
                    <meta-data
                        android:name='$classPath'
                        android:value='androidx.startup' />
                    under InitializationProvider in your AndroidManifest.xml
                """.trimIndent()
            }
            val appContext = context.applicationContext
            newIntent = { Intent(appContext, TorService::class.java) }

            isInitialized = true
            return Companion
        }

        override fun dependencies(): List<Class<androidx.startup.Initializer<*>>> {
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

            @Volatile
            private var isInitialized: Boolean = false

            @JvmSynthetic
            internal fun isInitialized(): Boolean = isInitialized
        }
    }

    private companion object {
        private var newIntent: (() -> Intent)? = null
    }
}
