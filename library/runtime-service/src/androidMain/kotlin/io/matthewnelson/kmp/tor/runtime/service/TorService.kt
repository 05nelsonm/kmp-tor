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

    internal companion object {

        @JvmSynthetic
        internal fun Application.serviceFactoryLoader(): TorRuntime.ServiceFactory.Loader? {
            val app = this

            return object : TorRuntime.ServiceFactory.Loader() {
                override fun loadProtected(
                    initializer: TorRuntime.ServiceFactory.Initializer,
                ): TorRuntime.ServiceFactory {
                    return AndroidServiceFactory(app, initializer)
                }
            }
        }
    }
}
