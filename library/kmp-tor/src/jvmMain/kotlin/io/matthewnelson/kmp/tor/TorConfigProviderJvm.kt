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

import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.FILE_NAME_GEOIPS_ZIP
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.FILE_NAME_GEOIPS_ZIP_SHA256
import io.matthewnelson.kmp.tor.binary.extract.ConstantsBinaries.ZIP_SHA256_GEOIP
import io.matthewnelson.kmp.tor.binary.extract.ZipArchiveExtractor
import io.matthewnelson.kmp.tor.binary.extract.ZipExtractionException
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.file.toFile
import io.matthewnelson.kmp.tor.internal.doesContentMatchExpected
import io.matthewnelson.kmp.tor.internal.ProcessId
import io.matthewnelson.kmp.tor.manager.TorConfigProvider
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import java.io.File

/**
 * Jvm implementation for providing client [TorConfig] when loading Tor.
 *
 * Handles for you the extraction of geoip files to the specified locations, and
 * sets default directory/file locations for you (which can still be customized
 * if needed by overriding them).
 *
 * @see [TorConfigProvider]
 * @see [KmpTorLoaderJvm]
 * */
abstract class TorConfigProviderJvm: TorConfigProvider() {
    /**
     * Directory where Tor binaries will be extracted to when
     * loading/starting Tor.
     * */
    open val installationDir: Path by lazy {
        workDir.builder { addSegment(".kmptor") }
    }
    override val geoIpV4File: Path? by lazy {
        workDir.builder { addSegment("geoip") }
    }
    override val geoIpV6File: Path? by lazy {
        workDir.builder { addSegment("geoip6") }
    }
    override val processId: Int get() = ProcessId.get()

    @Throws(TorManagerException::class)
    override fun extractGeoIpV4File(toLocation: Path) {
        extract(toLocation, zipEntryName = "geoip", writeSha256SumOnExtraction = false)
    }

    @Throws(TorManagerException::class)
    override fun extractGeoIpV6File(toLocation: Path) {
        extract(toLocation, zipEntryName = "geoip6", writeSha256SumOnExtraction = true)
    }

    @Throws(TorManagerException::class)
    private fun extract(
        toLocation: Path,
        zipEntryName: String,
        writeSha256SumOnExtraction: Boolean,
    ) {
        val sha256SumFile = File(workDir.value, FILE_NAME_GEOIPS_ZIP_SHA256)
        val geoipFile = toLocation.toFile()
        val sha256SumMatches = sha256SumFile.doesContentMatchExpected(ZIP_SHA256_GEOIP)

        if (sha256SumMatches && geoipFile.exists()) {
            return
        }

        val extractor = ZipArchiveExtractor.selective(
            zipFileStreamProvider = {
                javaClass.getResourceAsStream("/kmptor/${FILE_NAME_GEOIPS_ZIP}")
                    ?: throw TorManagerException(
                        "Failed to open resource for $zipEntryName extraction"
                    )
            },
            postExtraction = {
                if (!sha256SumMatches && writeSha256SumOnExtraction) {
                    try {
                        sha256SumFile.writeText(ZIP_SHA256_GEOIP)
                    } catch (e: Exception) {
                        delete()
                        sha256SumFile.delete()
                        throw TorManagerException("Failed to write $sha256SumFile", e)
                    }
                }
            },
            extractToFile = {
                if (name == zipEntryName) {
                    geoipFile
                } else {
                    null
                }
            }
        )

        try {
            extractor.extract()
        } catch (e: ZipExtractionException) {
            throw TorManagerException(e)
        }
    }
}
