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
@file:JvmName("TorRuntimeEnvironment")

package io.matthewnelson.kmp.tor.runtime.mobile

import android.content.Context
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller
import io.matthewnelson.kmp.tor.core.api.ResourceInstaller.Paths
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire default tor directory locations within the application's data
 * directory to create [TorRuntime.Environment].
 *
 * - workDir: app_torservice
 * - cacheDir: cache/torservice
 * */
@JvmName("Builder")
public fun Context.createTorRuntimeEnvironment(
    installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
): TorRuntime.Environment = createTorRuntimeEnvironment("torservice", installer)

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire default tor directory locations within the application's data
 * directory to create [TorRuntime.Environment].
 *
 * - workDir: app_torservice
 * - cacheDir: cache/torservice
 * */
@JvmName("Builder")
public fun Context.createTorRuntimeEnvironment(
    installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
    block: ThisBlock<TorRuntime.Environment.Builder>,
): TorRuntime.Environment = createTorRuntimeEnvironment("torservice", installer, block)

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire tor directory locations within the application's data directory
 * for specified [dirName] to create [TorRuntime.Environment] with.
 *
 * - workDir: app_[dirName]
 * - cacheDir: cache/[dirName]
 * */
@JvmName("Builder")
public fun Context.createTorRuntimeEnvironment(
    dirName: String,
    installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
): TorRuntime.Environment = createTorRuntimeEnvironment(dirName, installer) {}

/**
 * Android extension which utilizes [Context.getDir] and [Context.getCacheDir]
 * to acquire tor directory locations within the application's data directory
 * for specified [dirName] to create [TorRuntime.Environment] with.
 *
 * - workDir: app_[dirName]
 * - cacheDir: cache/[dirName]
 * */
@JvmName("Builder")
public fun Context.createTorRuntimeEnvironment(
    dirName: String,
    installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
    block: ThisBlock<TorRuntime.Environment.Builder>,
): TorRuntime.Environment = TorRuntime.Environment.Builder(
    workDir = getDir(dirName.ifBlank { "torservice" }, Context.MODE_PRIVATE),
    cacheDir = cacheDir.resolve(dirName.ifBlank { "torservice" }),
    installer = installer,
    block = block
)
