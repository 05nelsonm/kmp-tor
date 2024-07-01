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
@file:Suppress("FunctionName", "UnnecessaryOptInAnnotation", "RemoveRedundantQualifierName")

package io.matthewnelson.kmp.tor.runtime.service

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.startup.AppInitializer
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.core.resource.OSInfo
import io.matthewnelson.kmp.tor.runtime.Action
import io.matthewnelson.kmp.tor.runtime.NetworkObserver
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.service.TorService.Companion.serviceFactoryLoader
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI.Factory.Companion.unsafeCastAsType
import io.matthewnelson.kmp.tor.runtime.service.internal.ApplicationContext
import io.matthewnelson.kmp.tor.runtime.service.internal.ApplicationContext.Companion.toApplicationContext
import kotlin.concurrent.Volatile

/**
 * Configuration info for producing [TorRuntime.ServiceFactory] to
 * run tor within an [android.app.Service].
 *
 * **NOTE:** Only one [TorServiceConfig] instance can be instantiated.
 * Successive invocations of [Foreground.Companion.Builder] or
 * [Companion.Builder] will return the already created singleton instance.
 *
 * e.g. (A Background Service)
 *
 *     val config = TorServiceConfig.Builder {
 *         // configure...
 *     }
 *
 *     val environment = config.newEnvironment { installationDirectory ->
 *         // Assuming use of `kmp-tor:resource-tor` or
 *         // `kmp-tor:resource-tor-gpl` dependency
 *         // as well as the unit test dependency
 *         // `kmp-tor:resource-android-unit-test`
 *         TorResources(installationDirectory)
 *     }
 *
 *     val runtime = TorRuntime.Builder(environment) {
 *         // configure...
 *     }
 *
 * @see [Companion.Builder]
 * @see [Foreground.Companion.Builder]
 * @see [newEnvironment]
 * */
public open class TorServiceConfig private constructor(

    /**
     * See [Builder.stopServiceOnTaskRemoved]
     * */
    @JvmField
    public val stopServiceOnTaskRemoved: Boolean,

    /**
     * See [Builder.testUseBuildDirectory]
     * */
    @JvmField
    public val testUseBuildDirectory: Boolean,

    /**
     * See [Builder.useNetworkStateObserver]
     * */
    @JvmField
    public val useNetworkStateObserver: Boolean,
) {

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulators & devices):
     *  - workDirectory: app_torservice
     *  - cacheDirectory: cache/torservice
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `false`):
     *  - workDirectory: {system temp}/kmp_tor_android_test/torservice/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/torservice/cache
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `true`):
     *  - workDirectory: {module}/build/kmp_tor_android_test/torservice/work
     *  - cacheDirectory: {module}/build/kmp_tor_android_test/torservice/cache
     * */
    public fun newEnvironment(
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
    ): TorRuntime.Environment = newEnvironment(DEFAULT_DIRNAME, installer)

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulators & devices):
     *  - workDirectory: app_torservice
     *  - cacheDirectory: cache/torservice
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `false`):
     *  - workDirectory: {system temp}/kmp_tor_android_test/torservice/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/torservice/cache
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `true`):
     *  - workDirectory: {module}/build/kmp_tor_android_test/torservice/work
     *  - cacheDirectory: {module}/build/kmp_tor_android_test/torservice/cache
     * */
    public fun newEnvironment(
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
        block: ThisBlock<TorRuntime.Environment.Builder>,
    ): TorRuntime.Environment = newEnvironment(DEFAULT_DIRNAME, installer, block)

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulators & devices):
     *  - workDirectory: app_[dirName]
     *  - cacheDirectory: cache/[dirName]
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `false`):
     *  - workDirectory: {system temp}/kmp_tor_android_test/[dirName]/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/[dirName]/cache
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `true`):
     *  - workDirectory: {module}/build/kmp_tor_android_test/[dirName]/work
     *  - cacheDirectory: {module}/build/kmp_tor_android_test/[dirName]/cache
     * */
    public fun newEnvironment(
        dirName: String,
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
    ): TorRuntime.Environment = newEnvironment(dirName, installer) {}

    /**
     * Android implementation which creates the [TorRuntime.Environment] using
     * the provided [TorServiceConfig].
     *
     * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
     * automatically and tor will run inside an [android.app.Service].
     *
     * Directories (emulators & devices):
     *  - workDirectory: app_[dirName]
     *  - cacheDirectory: cache/[dirName]
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `false`):
     *  - workDirectory: {system temp}/kmp_tor_android_test/[dirName]/work
     *  - cacheDirectory: {system temp}/kmp_tor_android_test/[dirName]/cache
     *
     * Directories (android unit tests where [testUseBuildDirectory] = `true`):
     *  - workDirectory: {module}/build/kmp_tor_android_test/[dirName]/work
     *  - cacheDirectory: {module}/build/kmp_tor_android_test/[dirName]/cache
     * */
    public fun newEnvironment(
        dirName: String,
        installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
        block: ThisBlock<TorRuntime.Environment.Builder>,
    ): TorRuntime.Environment {
        val config = this

        @OptIn(ExperimentalKmpTorApi::class)
        return with(UTIL) {
            UTIL.ProvideLoader { appContext ->
                appContext.serviceFactoryLoader(config, instanceUIConfig = null)
            }.newEnvironment(config, dirName, installer, block)
        }
    }

    public companion object {

        private const val DEFAULT_DIRNAME: String = "torservice"

        private var appContext: ApplicationContext? = null

        @Volatile
        private var _instance: TorServiceConfig? = null

        /**
         * Opener for creating a [TorServiceConfig] which will run all instances
         * of [TorRuntime] that have been created via [newEnvironment] inside of
         * [TorService] operating as a Background Service.
         *
         * @see [Foreground.Companion.Builder]
         * */
        @JvmStatic
        public fun Builder(
            block: ThisBlock<TorServiceConfig.Builder>,
        ): TorServiceConfig {
            val b = TorServiceConfig.Builder().apply(block)

            return _instance ?: synchronized(UTIL) {
                _instance ?: TorServiceConfig(b)
                    .also { _instance = it }
            }
        }
    }

    @KmpTorDsl
    public open class Builder internal constructor() {

        /**
         * If [TorService] is running and your application is swiped from
         * the recent app's tray (user removes the Task), this setting
         * indicates the behavior of how you wish to react.
         *
         * If `true`, all instances of [TorRuntime] will be destroyed and
         * [android.app.Service.stopService] will be called. If `false`,
         * no reaction will be had and the service will either be:
         *  - If operating as a background service, killed when the
         *   application process is killed.
         *  - If operating as a foreground service, keep your application
         *   alive until [Action.StopDaemon] is executed for all instances
         *   of [TorRuntime] operating within the service.
         *
         * This can be useful if:
         *  - You are running [TorService] in the background alongside other
         *   services that are operating in the foreground which are keeping
         *   the application alive past Task removal.
         *  - You are running [TorService] in the foreground and wish to keep
         *   the application alive until [Action.StopDaemon] is executed.
         *
         * Default: `true`
         * */
        @JvmField
        public var stopServiceOnTaskRemoved: Boolean = true

        /**
         * For android unit tests, a setting of `true` will use the module build
         * directory, instead of the system temp directory, when setting up the
         * environment directories.
         *
         * **NOTE:** This has no effect if running on an emulator or device.
         *
         * Default: `false`
         * */
        @JvmField
        @ExperimentalKmpTorApi
        public var testUseBuildDirectory: Boolean = false

        /**
         * While [TorService] is running, a [NetworkObserver] that monitors the
         * device's connectivity state will be used for all instances of [TorRuntime]
         * operating within the service. If this is set to `false`, it will not be
         * used and the [TorRuntime] will be created with whatever was declared for
         * [TorRuntime.Builder.networkObserver].
         *
         * **NOTE:** Requires permission [android.Manifest.permission.ACCESS_NETWORK_STATE]
         * */
        @JvmField
        public var useNetworkStateObserver: Boolean = true
    }

    /**
     * An instance of [TorServiceConfig] which indicates to [TorService] that
     * it should start itself as a Foreground Service.
     *
     * This instance provides additional [newEnvironment] functions which enable
     * the passing of per-environment based configurations for the declared
     * [factory]. They are entirely optional and the regular [newEnvironment]
     * functions provided by [TorServiceConfig] will simply default to what
     * was declared for your [TorServiceUI.Factory.defaultConfig].
     *
     * TODO: AndroidManifest requirements
     *
     * TODO: README.md
     *
     * e.g. (A Foreground Service using the `kmp-tor:runtime-service-ui` dependency)
     *
     *     val factory = KmpTorServiceUI.Factory(
     *         defaultConfig = KmpTorServiceUI.Config(
     *             // ...
     *         ),
     *         info = TorServiceUI.NotificationInfo.of(
     *             // ...
     *         ),
     *         block = {
     *             // configure...
     *         },
     *     )
     *
     *     val config = TorServiceConfig.Foreground.Builder(factory) {
     *         // configure...
     *     }
     *
     *     val environment = config.newEnvironment { installationDirectory ->
     *         // Assuming use of `kmp-tor:resource-tor` or
     *         // `kmp-tor:resource-tor-gpl` dependency
     *         // as well as the unit test dependency
     *         // `kmp-tor:resource-android-unit-test`
     *         TorResources(installationDirectory)
     *     }
     *
     *     val runtime = TorRuntime.Builder(environment) {
     *         // configure...
     *     }
     *
     * @see [Foreground.Companion.Builder]
     * */
    public class Foreground <C: AbstractTorServiceUI.Config, F: TorServiceUI.Factory<C, *, *>> private constructor(
        @JvmField
        public val factory: F,
        b: Foreground.Builder,
    ): TorServiceConfig(b) {

        /**
         * See [Builder.exitProcessIfTaskRemoved]
         * */
        @JvmField
        public val exitProcessIfTaskRemoved: Boolean = b.exitProcessIfTaskRemoved

        /**
         * Android implementation which creates the [TorRuntime.Environment] using
         * the provided [TorServiceConfig].
         *
         * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
         * automatically and tor will run inside an [android.app.Service].
         *
         * Directories (emulators & devices):
         *  - workDirectory: app_torservice
         *  - cacheDirectory: cache/torservice
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `false`):
         *  - workDirectory: {system temp}/kmp_tor_android_test/torservice/work
         *  - cacheDirectory: {system temp}/kmp_tor_android_test/torservice/cache
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `true`):
         *  - workDirectory: {module}/build/kmp_tor_android_test/torservice/work
         *  - cacheDirectory: {module}/build/kmp_tor_android_test/torservice/cache
         *
         * @throws [Resources.NotFoundException] If [instanceConfig] fails validation
         *   checks (emulators & devices only).
         * @throws [IllegalArgumentException] if [instanceConfig] is not the same
         *   class type as the provided [TorServiceUI.Factory.defaultConfig].
         * */
        public fun newEnvironment(
            instanceConfig: C,
            installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
        ): TorRuntime.Environment = newEnvironment(
            instanceConfig = instanceConfig,
            installer = installer,
            block = {},
        )

        /**
         * Android implementation which creates the [TorRuntime.Environment] using
         * the provided [TorServiceConfig].
         *
         * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
         * automatically and tor will run inside an [android.app.Service].
         *
         * Directories (emulators & devices):
         *  - workDirectory: app_torservice
         *  - cacheDirectory: cache/torservice
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `false`):
         *  - workDirectory: {system temp}/kmp_tor_android_test/torservice/work
         *  - cacheDirectory: {system temp}/kmp_tor_android_test/torservice/cache
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `true`):
         *  - workDirectory: {module}/build/kmp_tor_android_test/torservice/work
         *  - cacheDirectory: {module}/build/kmp_tor_android_test/torservice/cache
         *
         * @throws [Resources.NotFoundException] If [instanceConfig] fails validation
         *   checks (emulators & devices only).
         * @throws [IllegalArgumentException] if [instanceConfig] is not the same
         *   class type as the provided [TorServiceUI.Factory.defaultConfig].
         * */
        public fun newEnvironment(
            instanceConfig: C,
            installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
            block: ThisBlock<TorRuntime.Environment.Builder>,
        ): TorRuntime.Environment = newEnvironment(
            dirName = DEFAULT_DIRNAME,
            instanceConfig = instanceConfig,
            installer = installer,
            block = block,
        )

        /**
         * Android implementation which creates the [TorRuntime.Environment] using
         * the provided [TorServiceConfig].
         *
         * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
         * automatically and tor will run inside an [android.app.Service].
         *
         * Directories (emulators & devices):
         *  - workDirectory: app_[dirName]
         *  - cacheDirectory: cache/[dirName]
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `false`):
         *  - workDirectory: {system temp}/kmp_tor_android_test/[dirName]/work
         *  - cacheDirectory: {system temp}/kmp_tor_android_test/[dirName]/cache
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `true`):
         *  - workDirectory: {module}/build/kmp_tor_android_test/[dirName]/work
         *  - cacheDirectory: {module}/build/kmp_tor_android_test/[dirName]/cache
         *
         * @throws [Resources.NotFoundException] If [instanceConfig] fails validation
         *   checks (emulators & devices only).
         * @throws [IllegalArgumentException] if [instanceConfig] is not the same
         *   class type as the provided [TorServiceUI.Factory.defaultConfig].
         * */
        public fun newEnvironment(
            dirName: String,
            instanceConfig: C,
            installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
        ): TorRuntime.Environment = newEnvironment(
            dirName = dirName,
            instanceConfig = instanceConfig,
            installer = installer,
            block = {},
        )

        /**
         * Android implementation which creates the [TorRuntime.Environment] using
         * the provided [TorServiceConfig].
         *
         * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
         * automatically and tor will run inside an [android.app.Service].
         *
         * Directories (emulators & devices):
         *  - workDirectory: app_[dirName]
         *  - cacheDirectory: cache/[dirName]
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `false`):
         *  - workDirectory: {system temp}/kmp_tor_android_test/[dirName]/work
         *  - cacheDirectory: {system temp}/kmp_tor_android_test/[dirName]/cache
         *
         * Directories (android unit tests where [testUseBuildDirectory] = `true`):
         *  - workDirectory: {module}/build/kmp_tor_android_test/[dirName]/work
         *  - cacheDirectory: {module}/build/kmp_tor_android_test/[dirName]/cache
         *
         * @throws [Resources.NotFoundException] If [instanceConfig] fails validation
         *   checks (emulators & devices only).
         * @throws [IllegalArgumentException] if [instanceConfig] is not the same
         *   class type as the provided [TorServiceUI.Factory.defaultConfig].
         * */
        public fun newEnvironment(
            dirName: String,
            instanceConfig: C,
            installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
            block: ThisBlock<TorRuntime.Environment.Builder>,
        ): TorRuntime.Environment {
            val config = this

            @OptIn(ExperimentalKmpTorApi::class)
            return with(UTIL) {
                UTIL.ProvideLoader { appContext ->
                    factory.validate(appContext.get(), instanceConfig)
                    instanceConfig.unsafeCastAsType(default = factory.defaultConfig)
                    appContext.serviceFactoryLoader(config, instanceUIConfig = instanceConfig)
                }.newEnvironment(config, dirName, installer, block)
            }
        }

        public companion object {

            /**
             * Opener for creating a [TorServiceConfig.Foreground] which will run all
             * instances of [TorRuntime] that have been created via [newEnvironment]
             * inside of [TorService] operating as a Foreground Service.
             *
             * **NOTE:** An [android.app.NotificationChannel] for API 26+ is set up
             * using the provided [TorServiceUI.Factory.info] (emulators &
             * devices only).
             *
             * @throws [ClassCastException] If an instance of [TorServiceConfig] has
             *   already been instantiated and is unable to be returned because it is
             *   not an instance of [Foreground].
             * @throws [Resources.NotFoundException] If [factory] fails validation
             *   checks (emulators & devices only).
             * @see [TorServiceConfig.Companion.Builder]
             * @see [io.matthewnelson.kmp.tor.runtime.service.ui.KmpTorServiceUI]
             * */
            @JvmStatic
            @Throws(ClassCastException::class, Resources.NotFoundException::class)
            public fun <C: AbstractTorServiceUI.Config, F: TorServiceUI.Factory<C, *, *>> Builder(
                factory: F,
                block: ThisBlock<Foreground.Builder>,
            ): Foreground<C, F> {
                val b = Foreground.Builder().apply(block)

                return _instance?.unsafeCast() ?: synchronized(UTIL) {
                    _instance?.unsafeCast() ?: run {
                        val app = appContext

                        if (app != null) {
                            factory.validate(app.get(), factory.defaultConfig)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val info = factory.info
                                val channel = NotificationChannel(
                                    info.channelID,
                                    info.channelName,
                                    NotificationManager.IMPORTANCE_DEFAULT,
                                ).apply {
                                    setShowBadge(info.channelShowBadge)
                                    description = info.channelDescription
                                    setSound(null, null)
                                }

                                (app.get().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                                    .createNotificationChannel(channel)
                            }
                        }

                        Foreground(factory, b)
                            .also { _instance = it }
                    }
                }
            }
        }

        /**
         * Extended builder options for [TorServiceConfig.Builder], specific to
         * [Foreground] operations.
         * */
        @KmpTorDsl
        public class Builder internal constructor(): TorServiceConfig.Builder() {

            /**
             * On Android API 26+, if a Foreground Service stops while the task
             * is removed (e.g. user swipes it away from the recent app's tray),
             * the OS does not kill the application process like it would on
             * Android API 25 and below.
             *
             * If `true`, this setting will modify that behavior for API 26+ such
             * that upon execution of [TorService.onDestroy], if the task is not
             * present, then [System.exit] will be called.
             *
             * If `false`, the application process will continue to run in the
             * background until either:
             *   - The OS kills the process to recoup memory (approximately 1m)
             *   - The user returns to it (warm start, no [Application.onCreate])
             *       - TODO: Saved State restarts
             *
             * Default: `true`
             * */
            @JvmField
            public var exitProcessIfTaskRemoved: Boolean = true
        }
    }

    protected object UTIL {

        @OptIn(ExperimentalKmpTorApi::class)
        internal fun interface ProvideLoader {
            fun newInstance(appContext: ApplicationContext): TorRuntime.ServiceFactory.Loader
        }

        internal fun ProvideLoader.newEnvironment(
            config: TorServiceConfig,
            dirName: String,
            installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
            block: ThisBlock<TorRuntime.Environment.Builder>,
        ): TorRuntime.Environment {
            val appContext = appContext

            @Suppress("LocalVariableName")
            val _dirName = dirName.ifBlank { DEFAULT_DIRNAME }

            if (appContext == null) {
                // Verify not Android runtime.
                @OptIn(InternalKmpTorApi::class)
                check(!OSInfo.INSTANCE.isAndroidRuntime()) {
                    // Startup initializer failed???
                    Initializer.errorMsg()
                }

                // Android unit tests
                val testDir = if (config.testUseBuildDirectory) {
                    "".toFile().absoluteFile.resolve("build")
                } else {
                    SysTempDir
                }.resolve("kmp_tor_android_test").resolve(_dirName)

                return TorRuntime.Environment.Builder(
                    workDirectory = testDir.resolve("work"),
                    cacheDirectory = testDir.resolve("cache"),
                    installer = installer,
                    block = block,
                )
            }

            val loader = this

            // Emulator or Device
            return TorRuntime.Environment.Builder(
                workDirectory = appContext.get().getDir(_dirName, Context.MODE_PRIVATE),
                cacheDirectory = appContext.get().cacheDir.resolve(_dirName),
                installer = installer,
                block = {
                    apply(block)

                    @OptIn(ExperimentalKmpTorApi::class)
                    serviceFactoryLoader = loader.newInstance(appContext)
                },
            )
        }
    }

    internal class Initializer internal constructor(): androidx.startup.Initializer<Initializer.Companion> {

        public override fun create(context: Context): Companion {
            val initializer = AppInitializer.getInstance(context)
            check(initializer.isEagerlyInitialized(javaClass)) { errorMsg() }
            appContext = context.toApplicationContext()
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
            internal fun isInitialized(): Boolean = appContext != null

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

    @OptIn(ExperimentalKmpTorApi::class)
    private constructor(b: Builder): this(
        stopServiceOnTaskRemoved = b.stopServiceOnTaskRemoved,
        testUseBuildDirectory = b.testUseBuildDirectory,
        useNetworkStateObserver = b.useNetworkStateObserver,
    )
}

@Suppress("NOTHING_TO_INLINE")
@Throws(ClassCastException::class)
private inline fun <C: AbstractTorServiceUI.Config, F: TorServiceUI.Factory<C, *, *>> TorServiceConfig.unsafeCast(): TorServiceConfig.Foreground<C, F> {
    if (this !is TorServiceConfig.Foreground<*, *>) {
        val msg = """
            Unable to return TorServiceConfig.Foreground. An instance was already
            configured without declaring a TorServiceUI.Factory and is not
            able to be cast.
        """.trimIndent()

        throw ClassCastException(msg)
    }

    @Suppress("UNCHECKED_CAST")
    return this as TorServiceConfig.Foreground<C, F>
}
