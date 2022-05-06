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

import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.FILE_NAME_KMPTOR_ZIP
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.FILE_NAME_KMPTOR_ZIP_SHA256
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_SHA256_LINUX_X64
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_SHA256_LINUX_X86
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_SHA256_MACOS_X64
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_SHA256_MINGW_X64
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_SHA256_MINGW_X86
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_MANIFEST_LINUX_X64
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_MANIFEST_LINUX_X86
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_MANIFEST_MACOS_X64
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_MANIFEST_MINGW_X64
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_MANIFEST_MINGW_X86
import io.matthewnelson.kmp.tor.binary.extract.ZipArchiveExtractor
import io.matthewnelson.kmp.tor.internal.doesContentMatchExpected
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * Choose between the following supported platforms:
 *  - [linuxX64]
 *  - [linuxX86]
 *  - [macosX64]
 *  - [mingwX64]
 *  - [mingwX86]
 *
 * This will automatically extract Tor Binaries in accordance with your
 * chosen [installOption] for the given platform and architecture.
 *
 * The binary dependencies for Java/NodeJs are not automatically imported
 * like other platforms for kmp-tor, and must be declared independently
 * from the `kmp-tor` dependency.
 * */
class PlatformInstaller private constructor(
    @JvmField
    val os: String,
    @JvmField
    val arch: String,
    @JvmField
    val sha256Sum: String,
    @JvmField
    val installOption: InstallOption,
    private val archiveManifestProvider: () -> List<String>,
) {

    enum class InstallOption {
        /**
         * Perform a clean install of Tor binaries every time Tor is
         * started.
         * */
        CleanInstallOnEachStart,

        /**
         * Perform a clean install of Tor binaries on first startup
         * of Tor for the Application's runtime. Subsequent startups
         * will only perform a clean install if binary files are missing.
         * */
        CleanInstallFirstStartOnly,

        /**
         * Perform a clean install of Tor binaries only if they are missing
         * from the specified directory.
         *
         * Note that upon successful extraction of binaries, the kmptor.zip
         * file's sha256sum value is always written to a file in the specified
         * installation directory (ie: [FILE_NAME_KMPTOR_ZIP_SHA256]). That
         * persisted value is checked against [sha256Sum] such that upon
         * dependency updates where new binary's are present, a clean install
         * can be triggered.
         * */
        CleanInstallIfMissing,
    }

    companion object {
        // os
        private const val LINUX = "linux"
        private const val MACOS = "macos"
        private const val MINGW = "mingw"

        // arch
        private const val X64 = "x64"
        private const val X86 = "x86"

        private const val TOR = "tor"

        @JvmStatic
        fun linuxX64(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                os = LINUX,
                arch = X64,
                sha256Sum = ZIP_SHA256_LINUX_X64,
                installOption = option,
                archiveManifestProvider = { ZIP_MANIFEST_LINUX_X64 },
            )
        }

        @JvmStatic
        fun linuxX86(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                os = LINUX,
                arch = X86,
                sha256Sum = ZIP_SHA256_LINUX_X86,
                installOption = option,
                archiveManifestProvider = { ZIP_MANIFEST_LINUX_X86 },
            )
        }

        @JvmStatic
        fun macosX64(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                os = MACOS,
                arch = X64,
                sha256Sum = ZIP_SHA256_MACOS_X64,
                installOption = option,
                archiveManifestProvider = { ZIP_MANIFEST_MACOS_X64 },
            )
        }

        @JvmStatic
        fun mingwX64(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                os = MINGW,
                arch = X64,
                sha256Sum = ZIP_SHA256_MINGW_X64,
                installOption = option,
                archiveManifestProvider = { ZIP_MANIFEST_MINGW_X64 },
            )
        }

        @JvmStatic
        fun mingwX86(option: InstallOption): PlatformInstaller {
            return PlatformInstaller(
                os = MINGW,
                arch = X86,
                sha256Sum = ZIP_SHA256_MINGW_X86,
                installOption = option,
                archiveManifestProvider = { ZIP_MANIFEST_MINGW_X86 },
            )
        }
    }

    private val lock = Mutex()
    private val installationDirs: MutableSet<String> = LinkedHashSet(1)

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
    internal suspend fun retrieveTor(
        installationDir: File,
        notify: (TorManagerEvent.Log.Debug) -> Unit,
    ): File {
        return when (installOption) {
            InstallOption.CleanInstallOnEachStart -> {
                doCleanInstall(installationDir, notify)
            }
            InstallOption.CleanInstallFirstStartOnly -> {
                val canonicalPath = try {
                    installationDir.canonicalPath
                } catch (e: IOException) {
                    throw TorManagerException(
                        "Failed to retrieve canonical path of installation dir", e
                    )
                }

                lock.withLock {
                    if (installationDirs.contains(canonicalPath)) {
                        doCleanInstallIfMissing(installationDir, notify)
                    } else {
                        val tor = doCleanInstall(installationDir, notify)
                        installationDirs.add(canonicalPath)
                        tor
                    }
                }
            }
            InstallOption.CleanInstallIfMissing -> {
                doCleanInstallIfMissing(installationDir, notify)
            }
        }
    }

    @Throws(TorManagerException::class)
    private fun doCleanInstall(
        installationDir: File,
        notify: (TorManagerEvent.Log.Debug) -> Unit,
    ): File {
        notify.invoke(TorManagerEvent.Log.Debug(
            "Performing clean install of Tor binaries to directory: $installationDir"
        ))

        var tor: File? = null
        val extractor = ZipArchiveExtractor.all(
            destinationDir = installationDir,
            postExtraction = {
                for (file in this) {
                    if (file.nameWithoutExtension.lowercase() == TOR) {
                        tor = file
                    }

                    file.setExecutable(true, true)
                }

                if (tor == null) {
                    for (file in this) {
                        file.delete()
                    }
                    // let complete to throw exception.
                } else {
                    try {
                        File(installationDir, FILE_NAME_KMPTOR_ZIP_SHA256).writeText(sha256Sum)
                    } catch (e: Exception) {
                        for (file in this) {
                            file.delete()
                        }
                        throw e
                    }
                }
            },
            zipFileStreamProvider = {
                val path = "/kmptor/$os/$arch/$FILE_NAME_KMPTOR_ZIP"
                javaClass.getResourceAsStream(path)
                    ?: throw IOException("Failed to obtain resource stream at path: $path")
            }
        )

        try {
            extractor.extract()
        } catch (e: Exception) {
            throw TorManagerException(e)
        }

        return tor ?: throw TorManagerException("Failed to extract and retrieve the Tor executable")
    }

    @Throws(TorManagerException::class)
    private fun doCleanInstallIfMissing(
        installationDir: File,
        notify: (TorManagerEvent.Log.Debug) -> Unit,
    ): File {
        val sha256SumFile = File(installationDir, FILE_NAME_KMPTOR_ZIP_SHA256)

        if (!sha256SumFile.doesContentMatchExpected(sha256Sum)) {
            return doCleanInstall(installationDir, notify)
        }

        var tor: File? = null
        var existsCount = 0
        val archiveManifestFiles = archiveManifestProvider.invoke().toManifestFiles(installationDir)

        for (file in archiveManifestFiles) {
            if (file.exists()) {
                existsCount++
                if (file.nameWithoutExtension.lowercase() == TOR) {
                    tor = file
                }
            }
        }

        return tor?.let {
            if (archiveManifestFiles.size == existsCount) {
                notify.invoke(TorManagerEvent.Log.Debug(
                    "Tor binaries detected. Skipping extraction"
                ))
                it
            } else {
                doCleanInstall(installationDir, notify)
            }
        } ?: doCleanInstall(installationDir, notify)
    }

    private fun List<String>.toManifestFiles(installationDir: File): List<File> {
        val newList: MutableList<File> = ArrayList(size)
        for (entry in this) {
            // exclude directories
            if (entry.endsWith('/')) {
                continue
            }

            newList.add(File(installationDir, entry))
        }
        return newList
    }
}
