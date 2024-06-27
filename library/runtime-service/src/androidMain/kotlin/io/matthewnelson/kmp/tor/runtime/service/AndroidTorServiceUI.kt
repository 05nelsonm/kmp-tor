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
import android.app.Notification
import android.content.Context
import android.content.res.Resources
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import kotlinx.coroutines.Job

/**
 * Core abstraction for Android which enables implementors the ability
 * to create a fully customized notification for the running instances of
 * [TorRuntime] as they operate within [TorService].
 *
 * Alternatively, use the default implementation `kmp-tor:runtime-service-ui`
 * dependency, [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI].
 *
 * @see [TorServiceConfig.Foreground]
 * */
@OptIn(ExperimentalKmpTorApi::class)
public abstract class AndroidTorServiceUI<C: AndroidTorServiceUI.Config>
@ExperimentalKmpTorApi
@Throws(IllegalStateException::class)
protected constructor(
    args: Args,
): TorServiceUI<AndroidTorServiceUI.Args, C>(
    args,
    args.defaultConfig(),
    INIT,
) {

    /**
     * Application [Context] for resolving android resources and creating
     * [Notification.Builder].
     * */
    @JvmField
    protected val appContext: Context = args.service().applicationContext

    /**
     * [NotificationInfo] from [Factory.info]
     * */
    @JvmField
    protected val info: NotificationInfo = args.info()

    private val service: Context = args.service()

    public class Args private constructor(
        private val _defaultConfig: Config,
        private val _info: NotificationInfo,
        private val _service: Context,
        serviceJob: Job,
    ): TorServiceUI.Args(serviceJob) {

        @JvmSynthetic
        @Suppress("UNCHECKED_CAST")
        internal fun <C: Config> defaultConfig(): C = _defaultConfig as C
        @JvmSynthetic
        internal fun info(): NotificationInfo = _info
        @JvmSynthetic
        internal fun service(): Context = _service

        internal companion object {

            @JvmSynthetic
            internal fun of(
                defaultConfig: Config,
                info: NotificationInfo,
                service: Context,
                serviceJob: Job,
            ): Args = Args(
                defaultConfig,
                info,
                service,
                serviceJob,
            )
        }
    }

    public abstract class Config
    @ExperimentalKmpTorApi
    @Throws(IllegalArgumentException::class)
    protected constructor(fields: Set<Field>): TorServiceUI.Config(fields) {

        /**
         * Implementations **MUST** ensure all resources specified
         * are valid for their given implementation. This is simply
         * a runtime check that is performed from [TorServiceConfig]
         * before instantiating new [TorRuntime.Environment] and
         * [TorServiceConfig.Foreground] instances to ensure errors
         * are caught and handled immediately.
         * */
        @Throws(Resources.NotFoundException::class)
        public abstract fun validate(app: Application)
    }

    /**
     * Core abstraction for Android which enables [TorService] the
     * ability to create new instances of [UI].
     * */
    public abstract class Factory<C: Config, UI: AndroidTorServiceUI<C>>
    @ExperimentalKmpTorApi
    protected constructor(
        defaultConfig: C,

        /**
         * Information for the notification. If API 26+, an
         * [android.app.NotificationChannel] is automatically set up using
         * the provided [NotificationInfo] when the [Factory] implementation
         * is utilized to instantiate [TorServiceConfig.Foreground].
         * */
        @JvmField
        public val info: NotificationInfo
    ): TorServiceUI.Factory<Args, C, UI>(defaultConfig, INIT)
}
