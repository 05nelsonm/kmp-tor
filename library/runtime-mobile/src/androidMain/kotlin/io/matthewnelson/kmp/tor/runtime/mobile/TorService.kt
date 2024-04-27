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

import android.app.Application
import android.content.Context
import androidx.startup.AppInitializer
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.*
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent.Notifier.Companion.lce
import io.matthewnelson.kmp.tor.runtime.core.*
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd

@Suppress("unused")
internal class TorService internal constructor(): AbstractTorService() {

    @OptIn(InternalKmpTorApi::class)
    private class AndroidTorRuntime private constructor(
        private val factory: TorRuntime.ServiceFactory,
    ) : TorRuntime,
        TorEvent.Processor by factory,
        RuntimeEvent.Processor by factory,
        FileID by factory
    {

        init { TorRuntime.ServiceFactory.checkInstance(factory) }

        private val app: () -> Application = TorService.app
            ?: throw IllegalStateException("TorService.Initializer must be initialized")

        // TODO: Start service + bind + inject factory
        private val instance by lazy { factory.newRuntime(emptySet(), null) }

        public override fun environment(): TorRuntime.Environment = factory.environment

        public override fun enqueue(
            action: RuntimeAction,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Unit>,
        ): QueuedJob = instance.first.enqueue(action, onFailure, onSuccess)

        public override fun <Response : Any> enqueue(
            cmd: TorCmd.Unprivileged<Response>,
            onFailure: OnFailure,
            onSuccess: OnSuccess<Response>,
        ): QueuedJob = instance.first.enqueue(cmd, onFailure, onSuccess)

        init {
            factory.lce(Lifecycle.Event.OnCreate(this))
        }

        public override fun toString(): String = "AndroidTorRuntime[id=$fid]@${hashCode()}"

        companion object {

            @JvmStatic
            @OptIn(InternalKmpTorApi::class)
            @Throws(IllegalStateException::class)
            fun create(factory: TorRuntime.ServiceFactory): TorRuntime = AndroidTorRuntime(factory)
        }

        public override fun unsubscribeAll(tag: String) { factory.unsubscribeAll(tag) }
        public override fun unsubscribeAll(vararg events: RuntimeEvent<*>) { factory.unsubscribeAll(*events) }
        public override fun clearObservers() { factory.clearObservers() }
    }

    internal class Initializer internal constructor(): androidx.startup.Initializer<Initializer.Companion> {

        override fun create(context: Context): Companion {
            if (isInitialized()) return Companion

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
            val app = context.applicationContext as Application
            TorService.app = { app }
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

            @JvmSynthetic
            internal fun isInitialized(): Boolean = app != null
        }
    }

    private companion object {
        private var app: (() -> Application)? = null
    }
}
