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
import io.matthewnelson.kmp.tor.runtime.ctrl.api.ThisBlock

/**
 * Android extension which utilizes [Context] to acquire default
 * tor directory locations when configuring [TorRuntime.Environment].
 * */
@JvmName("Builder")
public fun Context.toTorRuntimeEnvironment(
    installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
): TorRuntime.Environment = toTorRuntimeEnvironment(installer) {}

/**
 * Android extension which utilizes [Context] to acquire default
 * tor directory locations when configuring [TorRuntime.Environment].
 * */
@JvmName("Builder")
public fun Context.toTorRuntimeEnvironment(
    installer: (installationDir: File) -> ResourceInstaller<Paths.Tor>,
    block: ThisBlock<TorRuntime.Environment.Builder>,
): TorRuntime.Environment = TorRuntime.Environment.Builder(
    workDir = getDir("torservice", Context.MODE_PRIVATE),
    cacheDir = cacheDir.resolve("torservice"),
    installer = installer,
    block = block
)
