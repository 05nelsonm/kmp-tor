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
import androidx.startup.AppInitializer
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.core.resource.OSInfo
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.service.TorService.Companion.serviceFactoryLoader
import kotlin.concurrent.Volatile

/**
 * Configuration info for producing [TorRuntime.ServiceFactory] to
 * run tor within an [android.app.Service].
 *
 * **NOTE:** Only one [TorServiceConfig] will be created. Successive
 * invocations of [Companion.Builder] will return the singleton
 * instance.
 *
 * e.g.
 *
 *     val config = TorServiceConfig.Builder {
 *         // configure ...
 *     }
 *
 *     val runtime = config.Environment { installationDirectory ->
 *         // Assuming use of `kmp-tor:resource-tor` dependency
 *         TorResources(installationDirectory)
 *     }
 *
 * @see [Companion.Builder]
 * @see [Companion.instanceOrNull]
 * @see [Environment]
 * */
public class TorServiceConfig private constructor(
    @JvmField
    public val stopServiceOnTaskRemoved: Boolean,
) {

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulator & device)
     *  - workDirectory: app_torservice
     *  - cacheDirectory: cache/torservice
     *
     * Directories (android unit tests)
     *  - workDirectory: {system temp}/kmp_tor_android_test/torservice/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/torservice/cache
     * */
    public fun Environment(
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
    ): TorRuntime.Environment = Environment(DEFAULT_DIRNAME, installer)

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulator & device)
     *  - workDirectory: app_torservice
     *  - cacheDirectory: cache/torservice
     *
     * Directories (android unit tests)
     *  - workDirectory: {system temp}/kmp_tor_android_test/torservice/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/torservice/cache
     * */
    public fun Environment(
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
        block: ThisBlock<TorRuntime.Environment.Builder>,
    ): TorRuntime.Environment = Environment(DEFAULT_DIRNAME, installer, block)

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulator & device)
     *  - workDirectory: app_[dirName]
     *  - cacheDirectory: cache/[dirName]
     *
     * Directories (android unit tests)
     *  - workDirectory: {system temp}/kmp_tor_android_test/[dirName]/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/[dirName]/cache
     * */
    public fun Environment(
        dirName: String,
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
    ): TorRuntime.Environment = Environment(dirName, installer) {}

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulator & device)
     *  - workDirectory: app_[dirName]
     *  - cacheDirectory: cache/[dirName]
     *
     * Directories (android unit tests)
     *  - workDirectory: {system temp}/kmp_tor_android_test/[dirName]/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/[dirName]/cache
     * */
    public fun Environment(
        dirName: String,
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
        block: ThisBlock<TorRuntime.Environment.Builder>,
    ): TorRuntime.Environment {
        val app = app

        @Suppress("LocalVariableName")
        val _dirName = dirName.ifBlank { DEFAULT_DIRNAME }

        if (app == null) {
            // Verify not Android runtime.
            @OptIn(InternalKmpTorApi::class)
            check(!OSInfo.INSTANCE.isAndroidRuntime()) {
                // Initializer error
                Initializer.errorMsg()
            }

            // Android unit tests
            val tmp = SysTempDir
                .resolve("kmp_tor_android_test")
                .resolve(_dirName)

            return TorRuntime.Environment.Builder(
                workDirectory = tmp.resolve("work"),
                cacheDirectory = tmp.resolve("cache"),
                installer = installer,
                block = block,
            )
        }

        return TorRuntime.Environment.Builder(
            workDirectory = app.getDir(_dirName, Context.MODE_PRIVATE),
            cacheDirectory = app.cacheDir.resolve(_dirName),
            installer = installer,
        ) {
            apply(block)

            @OptIn(ExperimentalKmpTorApi::class)
            serviceFactoryLoader = app.serviceFactoryLoader(this@TorServiceConfig)
        }
    }

    public companion object {

        private const val DEFAULT_DIRNAME: String = "torservice"

        private var app: Application? = null

        @Volatile
        private var _instance: TorServiceConfig? = null

        @JvmStatic
        internal fun instanceOrNull(): TorServiceConfig? = _instance

        @JvmStatic
        public fun Builder(
            block: ThisBlock<Builder>,
        ): TorServiceConfig {
            val b = Builder.get().apply(block)

            return _instance ?: synchronized(this) {
                _instance ?: run {
                    TorServiceConfig(
                        stopServiceOnTaskRemoved = b.stopServiceOnTaskRemoved,
                    ).also { _instance = it }
                }
            }
        }
    }

    @KmpTorDsl
    public class Builder private constructor() {

        @JvmField
        public var stopServiceOnTaskRemoved: Boolean = true

        internal companion object {

            @JvmSynthetic
            internal fun get(): Builder = Builder()
        }
    }

    internal class Initializer internal constructor(): androidx.startup.Initializer<Initializer.Companion> {

        public override fun create(context: Context): Companion {
            val initializer = AppInitializer.getInstance(context)
            check(initializer.isEagerlyInitialized(javaClass)) { errorMsg() }
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

            internal fun errorMsg(): String {
                val classPath = "io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig$" + "Initializer"

                return """
                    TorServiceConfig.Initializer cannot be initialized lazily.
                    Please ensure that you have:
                    <meta-data
                        android:name='$classPath'
                        android:value='androidx.startup' />
                    under InitializationProvider in your AndroidManifest.xml
                """.trimIndent()
            }
        }
    }
}
