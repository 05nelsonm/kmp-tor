/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor

import io.matthewnelson.kmp.tor.controller.common.file.toFile
import io.matthewnelson.kmp.tor.internal.isStillAlive
import io.matthewnelson.kmp.tor.manager.KmpTorLoader
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.IOException
import kotlin.collections.ArrayList
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android implementation for loading and starting Tor.
 *
 * @see [TorConfigProviderAndroid]
 * @see [KmpTorLoader]
 * @sample [io.matthewnelson.kmp.tor.sample.android.App]
 * */
class KmpTorLoaderAndroid(provider: TorConfigProviderAndroid): KmpTorLoader(provider) {

    private val nativeDir = File(provider.appContext.applicationInfo.nativeLibraryDir)

    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(TorManagerException::class, CancellationException::class)
    override suspend fun startTor(configLines: List<String>) {
        val tor: File = nativeDir.listFiles().let { nDirFiles ->
            var libKmpTor: File? = null

            if (nDirFiles != null) {
                for (file in nDirFiles) {
                    if (file.name == "libKmpTor.so") {
                        libKmpTor = file
                        break
                    }
                }
            }

            libKmpTor
        } ?: throw TorManagerException("'libKmpTor.so' is missing")

        val newLines: MutableList<String> = ArrayList(configLines.size + 3)
        newLines.add(tor.absolutePath)

        // TODO: Move to TorConfig.Setting
        newLines.add("--__OwningControllerProcess")
        newLines.add(android.os.Process.myPid().toString())

        newLines.addAll(configLines)

        var process: Process? = null
        try {
            val builder = ProcessBuilder(newLines)
            val env = builder.environment()
            env["HOME"] = provider.workDir.toFile().absolutePath

            val p = builder.start()
            process = p

            // Process.waitFor() is runBlocking which we do not want here.
            // Below allows us to monitor a few things that, when no longer
            // true, will drop the while loop suspension and automatically destroy
            // our process:
            //  - The underlying coroutine (or dispatcher) is cancelled/closed
            //  - Tor stops running for some reason
            while (p.isStillAlive() && currentCoroutineContext().isActive) {
                delay(100L)
            }
        } catch (e: IOException) {
            throw TorManagerException("Failed to start Tor", e)
        } finally {
            process?.destroy()
        }
    }
}
