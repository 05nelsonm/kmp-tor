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

import android.content.Context
import io.matthewnelson.kmp.tor.binary.extract.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.manager.TorConfigProvider
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException

/**
 * Android implementation for providing client [TorConfig] when loading Tor.
 *
 * Handles for you the extraction of geoip files to the specified locations, and
 * sets default directory/file locations for you (which can still be customized
 * if needed by overriding them).
 *
 * @see [TorConfigProvider]
 * @see [KmpTorLoaderAndroid]
 * @sample [io.matthewnelson.kmp.tor.sample.kotlin.android.SampleApp]
 * */
abstract class TorConfigProviderAndroid(context: Context): TorConfigProvider() {
    val appContext: Context =
        context.applicationContext
    override val workDir: Path =
        Path(appContext.getDir("torservice", Context.MODE_PRIVATE))
    override val cacheDir: Path =
        Path(appContext.cacheDir).builder { addSegment("torservice") }
    override val geoIpV4File: Path? by lazy {
        workDir.builder { addSegment("geoip") }
    }
    override val geoIpV6File: Path? by lazy {
        workDir.builder { addSegment("geoip6") }
    }
    override val processId: Int get() = android.os.Process.myPid()

    @Throws(TorManagerException::class)
    override fun extractGeoIpV4File(toLocation: Path) {
        try {
            Extractor(appContext).extract(
                resource = TorResourceGeoip,
                destination = toLocation.value,
                cleanExtraction = false
            )
        } catch (e: ExtractionException) {
            throw TorManagerException(e)
        }
    }

    @Throws(TorManagerException::class)
    override fun extractGeoIpV6File(toLocation: Path) {
        try {
            Extractor(appContext).extract(
                resource = TorResourceGeoip6,
                destination = toLocation.value,
                cleanExtraction = false
            )
        } catch (e: ExtractionException) {
            throw TorManagerException(e)
        }
    }
}
