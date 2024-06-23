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
@file:JvmName("TorServiceEnvironment")

package io.matthewnelson.kmp.tor.runtime.service

import android.app.Application
import android.content.Context
import android.content.res.Resources
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.service.TorService.Companion.serviceFactoryLoader

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire default tor directory locations within the application's data
 * directory to create [TorRuntime.Environment].
 *
 * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
 * automatically and tor will run inside an [android.app.Service].
 *
 * - workDirectory: app_torservice
 * - cacheDirectory: cache/torservice
 *
 * e.g. (using the `kmp-tor:resource-tor` dependency)
 *
 *     val env = myApp.createTorServiceEnvironment { installationDirectory ->
 *         TorResources(installationDirectory)
 *     }
 *
 * @throws [Resources.NotFoundException] if configured to run as a Foreground
 *   Service and [TorServiceConfig.getMetaData] throws exception.
 * */
@JvmName("Builder")
public fun Application.createTorServiceEnvironment(
    installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
<<<<<<< HEAD:library/runtime-service/src/androidMain/kotlin/io/matthewnelson/kmp/tor/runtime/service/TorRuntimeEnvironment.kt
): TorRuntime.Environment = createTorRuntimeEnvironment("torservice", installer)
=======
): TorRuntime.Environment = createTorServiceEnvironment(
    dirName = DEFAULT_DIRNAME,
    installer = installer,
)
>>>>>>> 1abe97b5 (WIP: Stub in ServiceNotification abstraction for Android usage):library/runtime-service/src/androidMain/kotlin/io/matthewnelson/kmp/tor/runtime/service/TorServiceEnvironment.kt

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire default tor directory locations within the application's data
 * directory to create [TorRuntime.Environment].
 *
 * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
 * automatically and tor will run inside an [android.app.Service].
 *
 * - workDirectory: app_torservice
 * - cacheDirectory: cache/torservice
 *
 * e.g. (using the `kmp-tor:resource-tor` dependency)
 *
 *     val env = myApp.createTorServiceEnvironment(
 *         installer = { installationDirectory ->
 *             TorResources(installationDirectory)
 *         }
 *     ) {
 *         // TorRuntime.Environment.Builder
 *     }
 *
 * @throws [Resources.NotFoundException] if configured to run as a Foreground
 *   Service and [TorServiceConfig.getMetaData] throws exception.
 * */
@JvmName("Builder")
public fun Application.createTorServiceEnvironment(
    installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
<<<<<<< HEAD:library/runtime-service/src/androidMain/kotlin/io/matthewnelson/kmp/tor/runtime/service/TorRuntimeEnvironment.kt
    block: ThisBlock<TorRuntime.Environment.Builder>,
): TorRuntime.Environment = createTorRuntimeEnvironment("torservice", installer, block)
=======
    environment: ThisBlock<TorRuntime.Environment.Builder>,
): TorRuntime.Environment = createTorServiceEnvironment(
    dirName = DEFAULT_DIRNAME,
    installer = installer,
    environment = environment,
)
>>>>>>> 1abe97b5 (WIP: Stub in ServiceNotification abstraction for Android usage):library/runtime-service/src/androidMain/kotlin/io/matthewnelson/kmp/tor/runtime/service/TorServiceEnvironment.kt

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire tor directory locations within the application's data directory
 * for specified [dirName] to create [TorRuntime.Environment] with.
 *
 * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
 * automatically and tor will run inside an [android.app.Service].
 *
 * - workDirectory: app_[dirName]
 * - cacheDirectory: cache/[dirName]
 *
 * e.g. (using the `kmp-tor:resource-tor` dependency)
 *
 *     val env = myApp.createTorServiceEnvironment("some_dirname") { installationDirectory ->
 *         TorResources(installationDirectory)
 *     }
 *
 * @throws [Resources.NotFoundException] if configured to run as a Foreground
 *   Service and [TorServiceConfig.getMetaData] throws exception.
 * */
@JvmName("Builder")
public fun Application.createTorServiceEnvironment(
    dirName: String,
    installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
): TorRuntime.Environment = createTorServiceEnvironment(
    dirName = dirName,
    installer = installer,
    environment = {},
)

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire tor directory locations within the application's data directory
 * for specified [dirName] to create [TorRuntime.Environment] with.
 *
 * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
 * automatically and tor will run inside an [android.app.Service].
 *
 * - workDirectory: app_[dirName]
 * - cacheDirectory: cache/[dirName]
 *
 * e.g. (using the `kmp-tor:resource-tor` dependency)
 *
 *     val env = myApp.createTorServiceEnvironment(
 *         dirName = "some_dirname",
 *         installer = { installationDirectory ->
 *             TorResources(installationDirectory)
 *         }
 *     ) {
 *         // TorRuntime.Environment.Builder
 *     }
 *
 * @throws [Resources.NotFoundException] if configured to run as a Foreground
 *   Service and [TorServiceConfig.getMetaData] throws exception.
 * */
@JvmName("Builder")
public fun Application.createTorServiceEnvironment(
    dirName: String,
    installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
    environment: ThisBlock<TorRuntime.Environment.Builder>,
): TorRuntime.Environment = createTorServiceEnvironment(
    dirName = dirName,
    installer = installer,
    overrides = {},
    environment = environment,
)

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire tor directory locations within the application's data directory
 * for specified [dirName] to create [TorRuntime.Environment] with.
 *
 * **NOTE:** [TorRuntime.Environment.Builder.serviceFactoryLoader] is set
 * automatically and tor will run inside an [android.app.Service].
 *
 * [overrides] provides the ability to modify the "global defaults" of
 * [TorServiceConfig] for this [TorRuntime.Environment] if you are utilizing
 * [TorService] as a Foreground Service. See [TorServiceConfig.OverridesBuilder].
 *
 * - workDirectory: app_[dirName]
 * - cacheDirectory: cache/[dirName]
 *
 * e.g. (using the `kmp-tor:resource-tor` dependency)
 *
 *     val env = myApp.createTorServiceEnvironment(
 *         dirName = "some_dirname",
 *         installer = { installationDirectory ->
 *             TorResources(installationDirectory)
 *         },
 *         overrides = {
 *             // TorServiceConfig.OverridesBuilder
 *             enableActionRestart = true
 *             enableActionStop = false
 *
 *             // ...
 *         }
 *     ) {
 *         // TorRuntime.Environment.Builder
 *     }
 *
 * @throws [Resources.NotFoundException] if configured to run as a Foreground
 *   Service and [TorServiceConfig.getMetaData] throws exception, or if invalid
 *   resources are utilized when configuring the [TorServiceConfig.OverridesBuilder]
 *   options.
 * */
@JvmName("Builder")
public fun Application.createTorServiceEnvironment(
    dirName: String,
    installer: (installationDirectory: File) -> ResourceInstaller<Paths.Tor>,
    overrides: ThisBlock<TorServiceConfig.OverridesBuilder>,
    environment: ThisBlock<TorRuntime.Environment.Builder>,
): TorRuntime.Environment = TorRuntime.Environment.Builder(
    workDirectory = getDir(dirName.ifBlank { "torservice" }, Context.MODE_PRIVATE),
    cacheDirectory = cacheDir.resolve(dirName.ifBlank { "torservice" }),
    installer = installer,
) {
<<<<<<< HEAD:library/runtime-service/src/androidMain/kotlin/io/matthewnelson/kmp/tor/runtime/service/TorRuntimeEnvironment.kt
    this.apply(block)
=======
    apply(environment)
>>>>>>> 1abe97b5 (WIP: Stub in ServiceNotification abstraction for Android usage):library/runtime-service/src/androidMain/kotlin/io/matthewnelson/kmp/tor/runtime/service/TorServiceEnvironment.kt

    // Will not be null b/c TorService.Initializer
    // should be there if consumer is utilizing this
    // function which has Context available.
    @OptIn(ExperimentalKmpTorApi::class)
    serviceFactoryLoader = serviceFactoryLoader(overrides)
}
