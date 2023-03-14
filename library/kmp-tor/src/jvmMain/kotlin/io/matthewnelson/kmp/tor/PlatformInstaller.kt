/*
 * Copyright (c) 2022 Matthew Nelson
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

import io.matthewnelson.kmp.tor.binary.extract.*
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * Choose between the following supported platforms:
 *  - [linuxX64]
 *  - [linuxX86]
 *  - [macosArm64]
 *  - [macosX64]
 *  - [mingwX64]
 *  - [mingwX86]
 *
 * This will automatically extract Tor Binaries in accordance with your
 * chosen [installOption] for the given platform and architecture.
 *
 * The binary dependencies for Java/NodeJs are not automatically imported
 * like other platforms for kmp-tor, and must be declared independently
 * of the `kmp-tor` dependency.
 * */
class PlatformInstaller private constructor(
    @JvmField
    val installOption: InstallOption,
    private val resource: TorResource.Binaries,
) {

    enum class InstallOption {
        /**
         * Perform a clean installation of Tor binaries every time Tor is
         * started.
         * */
        CleanInstallOnEachStart,

        /**
         * Perform a clean installation of Tor binaries on first startup
         * of Tor for the Application's runtime. Subsequent startups
         * will only perform a clean install if binary files are missing.
         * */
        CleanInstallFirstStartOnly,

        /**
         * Perform a clean installation of Tor binaries only if they are missing
         * from the specified directory.
         *
         * Note that upon successful extraction of binaries, the
         * [TorResource.sha256sum] value is always written to a file in the
         * specified installation directory. That persisted value is checked
         * against [sha256Sum] such that upon dependency updates where new
         * binaries are present, a clean install can be triggered.
         * */
        CleanInstallIfMissing,
    }

    companion object {
        // os
        private const val LINUX = "linux"
        private const val MACOS = "macos"
        private const val MINGW = "mingw"

        // arch
        private const val ARM64 = "arm64"
        private const val X64 = "x64"
        private const val X86 = "x86"

        @JvmStatic
        fun linuxX64(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                installOption = option,
                resource = TorResourceLinuxX64,
            )
        }

        @JvmStatic
        fun linuxX86(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                installOption = option,
                resource = TorResourceLinuxX86,
            )
        }

        @JvmStatic
        fun macosArm64(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                installOption = option,
                resource = TorResourceMacosArm64,
            )
        }

        @JvmStatic
        fun macosX64(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                installOption = option,
                resource = TorResourceMacosX64,
            )
        }

        @JvmStatic
        fun mingwX64(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                installOption = option,
                resource = TorResourceMingwX64
            )
        }

        @JvmStatic
        fun mingwX86(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                installOption = option,
                resource = TorResourceMingwX86,
            )
        }
    }

    @JvmField
    val os: String = when (resource) {
        is TorResourceLinuxX64,
        is TorResourceLinuxX86 -> LINUX
        is TorResourceMacosX64,
        is TorResourceMacosArm64 -> MACOS
        is TorResourceMingwX64,
        is TorResourceMingwX86 -> MINGW
        is TorBinaryResource -> resource.osName
    }

    @JvmField
    val arch: String = when (resource) {
        is TorResourceLinuxX64 -> X64
        is TorResourceLinuxX86 -> X86
        is TorResourceMacosX64 -> X64
        is TorResourceMacosArm64 -> ARM64
        is TorResourceMingwX64 -> X64
        is TorResourceMingwX86 -> X86
        is TorBinaryResource -> resource.arch
    }

    @JvmField
    val sha256Sum: String = resource.sha256sum

    private val lock = Mutex()
    private val installationDirs: MutableSet<String> = LinkedHashSet(/* initialCapacity */1,/* loadFactor */ 1.0F)

    @get:JvmName("isLinux")
    val isLinux: Boolean get() = os == LINUX
    @get:JvmName("isMacos")
    val isMacos: Boolean get() = os == MACOS
    @get:JvmName("isMingw")
    val isMingw: Boolean get() = os == MINGW

    /**
     * Decompresses archive file contents and extracts them to specified
     * [installationDir] if needed. Returns file needed for starting Tor.
     * */
    @JvmSynthetic
    @Throws(TorManagerException::class)
    internal suspend fun retrieveTor(installationDir: File): File {
        val extractor = Extractor()

        return try {
            when (installOption) {
                InstallOption.CleanInstallOnEachStart -> {
                    extractor.extract(
                        resource = resource,
                        destinationDir = installationDir.path,
                        cleanExtraction = true,
                    )
                }
                InstallOption.CleanInstallFirstStartOnly -> {
                    val canonicalPath = try {
                        installationDir.canonicalPath
                    } catch (e: IOException) {
                        throw TorManagerException("Failed to retrieve canonical path of installation dir", e)
                    }

                    lock.withLock {
                        if (installationDirs.contains(canonicalPath)) {
                            extractor.extract(
                                resource = resource,
                                destinationDir = installationDir.path,
                                cleanExtraction = false,
                            )
                        } else {
                            val tor = extractor.extract(
                                resource = resource,
                                destinationDir = installationDir.path,
                                cleanExtraction = true,
                            )
                            installationDirs.add(canonicalPath)
                            tor
                        }
                    }
                }

                InstallOption.CleanInstallIfMissing -> {
                    extractor.extract(
                        resource = resource,
                        destinationDir = installationDir.path,
                        cleanExtraction = false,
                    )
                }
            }.let { path ->
                File(path)
            }
        } catch (e: ExtractionException) {
            throw TorManagerException(e)
        }
    }
}
