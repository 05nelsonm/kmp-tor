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
package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.internal.util.PortValidator
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Base abstraction for preparing and obtaining client [TorConfig].
 *
 * [KmpTorLoader.load] will [retrieve] the [TorConfig] and validate it
 * prior to starting Tor.
 *
 * @see [retrieve]
 * */
@Suppress("SpellCheckingInspection")
abstract class TorConfigProvider {
    abstract val workDir: Path
    abstract val cacheDir: Path

    open val torrcFile: Path by lazy {
        workDir.builder { addSegment("torrc") }
    }
    open val torrcDefaultsFile: Path by lazy {
        workDir.builder { addSegment("torrc-defaults") }
    }
    open val geoIpV4File: Path? = null
    open val geoIpV6File: Path? = null
    open val processId: Int? = null

    /**
     * Called from a background thread, so it is safe to perform IO from here.
     * */
    protected abstract fun provide(): TorConfig

    /**
     * Called from a background thread, so it is safe to perform IO from here.
     *
     * This will only be called if [TorConfig.Setting.GeoIPFile] is set
     * in the client config retrieved from [provide], or if [geoIpV4File] is not null.
     *
     * The [geoIpV4File] setting is always preferred over the client [TorConfig]
     * setting.
     *
     * [TorConfig.Setting.GeoIPFile] will automatically be added to the [TorConfig]
     * upon successful extraction if it is not present.
     *
     * @throws [TorManagerException] if extraction failed
     * */
    @Throws(TorManagerException::class)
    protected open fun extractGeoIpV4File(toLocation: Path) {
        throw TorManagerException("extratGeoIpV4File not implemented")
    }

    /**
     * Called from a background thread, so it is safe to perform IO from here.
     *
     * This will only be called if [TorConfig.Setting.GeoIpV6File] is set
     * in the client config retrieved from [provide], or if [geoIpV6File] is not null.
     *
     * The [geoIpV6File] setting is always preferred over the client [TorConfig]
     * setting.
     *
     * [TorConfig.Setting.GeoIpV6File] will automatically be added to the [TorConfig]
     * upon successful extraction if it is not present.
     *
     * @throws [TorManagerException] if extraction failed
     * */
    @Throws(TorManagerException::class)
    protected open fun extractGeoIpV6File(toLocation: Path) {
        throw TorManagerException("extratGeoIpV6File not implemented")
    }

    /**
     * Will be set lazily upon starting of Tor (and subsequently after
     * every start call), where [retrieve] parses the client config retrieved
     * from [provide] and:
     *  - Adds necessary settings
     *  - Removes settings depending on the platform
     *  - Checks for Port conflicts/availability
     *
     * @see [retrieve]
     * */
    private val _lastValidatedTorConfig: AtomicRef<ValidatedTorConfig?> = atomic(null)
    val lastValidatedTorConfig: ValidatedTorConfig? get() = _lastValidatedTorConfig.value

    data class ValidatedTorConfig(
        @JvmField
        val torConfig: TorConfig,
        @JvmField
        val configLines: List<String>,
        @JvmField
        val controlPortFile: Path,
        @get:JvmSynthetic
        internal val cookieAuthFile: Path?, // if null, cookieAuth is set to False
    )

    @JvmSynthetic
    internal suspend fun retrieve(
        // remove certain settings if present, for example,
        // anything to do with Unix Sockets for Android,
        // Jvm (windows), NodeJs (windows), Windows targets.
        excludeSettings: Set<TorConfig.KeyWord>,
        isPortAvailable: (Port) -> Boolean
    ): ValidatedTorConfig {
        val clientConfig = provide()

        val dataDir = DataDirectory()
        val dataDirPath: Path = clientConfig
            .settings
            .filterIsInstance<DataDirectory>()
            .firstOrNull()
            ?.value
            ?.path
            ?: workDir.builder { addSegment(DataDirectory.DEFAULT_NAME) }
        dataDir.set(FileSystemDir(dataDirPath))

        val portValidator = PortValidator(dataDirPath)
        val builder: TorConfig.Builder = TorConfig.Builder {
            for (setting in clientConfig.settings) {
                if (excludeSettings.contains(setting.keyword)) {
                    continue
                }

                if (setting is UnixSockets) {
                    portValidator.add(setting)
                    continue
                }

                if (setting is Ports) {
                    portValidator.add(setting)
                    continue
                }

                put(setting)
            }

            put(portValidator.validate(isPortAvailable))
            
            // TorManager requires this to be initially set to true (disable network) for several reasons:
            //  - TorManager was passed a network observer and the device has no network, we
            //    want to leave the network disabled until we re-gain network (otherwise we can't
            //    bootstrap).
            //  - Setting to false (enable network) after we successfully connect over the control
            //    port means we can catch the NOTICE that is broadcast for what ports were opened
            //    so they can be dispatched after bootstrapping completion to cue clients they
            //    are good to go and can build their network clients with the appropriate proxy
            //    info.
            put(DisableNetwork().set(TorF.True))

            // TorManager requires that we not run Tor as a separate daemon as the coroutine
            // launched with a custom dispatcher handles the runblocking and clean up after
            // completion automatically for us. It also allows us to, in Java/Android, to kill
            // the process as a last resort if all attempts to shutdown cleanly (via the
            // controller) fail.
            put(RunAsDaemon().set(TorF.False))

            putIfAbsent(CacheDirectory().set(FileSystemDir(cacheDir)))
            putIfAbsent(dataDir)

            processId?.let { pid ->
                putIfAbsent(__OwningControllerProcess().set(ProcessId(pid)))
            }

            putIfAbsent(SyslogIdentityTag().set(FieldId("TorManager")))
        }

        // Always add control port file.
        // Prefer path set by client, but fallback to defaults if not set.
        val controlPortFile = ControlPortWriteToFile()
        val controlPortFilePath = clientConfig
            .settings
            .filterIsInstance<ControlPortWriteToFile>()
            .firstOrNull()
            ?.value
            ?.path
            ?: workDir.builder { addSegment(ControlPortWriteToFile.DEFAULT_NAME) }
        controlPortFile.set(FileSystemFile(controlPortFilePath))
        builder.put(controlPortFile)


        val cookieAuth = CookieAuthentication()
        val cookieAuthFile = CookieAuthFile()
        val cookieAuthFilePath: Path? = clientConfig
            .settings
            .filterIsInstance<CookieAuthFile>()
            .firstOrNull()
            ?.value
            .let { path ->

            val filePath = path
                ?.path
                ?: workDir.builder { addSegment(CookieAuthFile.DEFAULT_NAME) }

            when (clientConfig.settings.filterIsInstance<CookieAuthentication>().firstOrNull()?.value) {
                null -> {
                    // set cookie authentication if absent
                    builder.put(cookieAuth)

                    if (path == null) {
                        builder.put(cookieAuthFile.set(FileSystemFile(filePath)))
                    }

                    filePath
                }
                is TorF.True -> {
                    if (path == null) {
                        builder.put(cookieAuthFile.set(FileSystemFile(filePath)))
                    }
                    filePath
                }
                is TorF.False -> {
                    builder.remove(cookieAuthFile)
                    null
                }
            }
        }

        val geoIpFile = GeoIPFile()
        val geoIpFilePath: Path? = clientConfig
            .settings
            .firstOrNull { it.keyword.toString() == TorConfig.KeyWord.GeoIPFile.toString() }
            ?.value
            .let { option ->
                (option as? FileSystemFile)?.path ?: if (geoIpV4File?.value?.isNotEmpty() == true) geoIpV4File else null
            }

        if (geoIpFilePath != null) {
            try {
                extractGeoIpV4File(geoIpFilePath)
                builder.put(geoIpFile.set(FileSystemFile(geoIpFilePath)))
            } catch (e: TorManagerException) {
                builder.remove(geoIpFile)
                // TODO: debugger
                e.printStackTrace()
            }
        } else {
            builder.remove(geoIpFile)
        }

        val geoIp6File = GeoIpV6File()
        val geoIp6FilePath: Path? = clientConfig
            .settings
            .firstOrNull { it.keyword.toString() == TorConfig.KeyWord.GeoIPv6File.toString() }
            ?.value
            .let { option ->
                (option as? FileSystemFile)?.path ?: if (geoIpV6File?.value?.isNotEmpty() == true) geoIpV6File else null
            }

        if (geoIp6FilePath != null) {
            try {
                extractGeoIpV6File(geoIp6FilePath)
                builder.put(geoIp6File.set(FileSystemFile(geoIp6FilePath)))
            } catch (e: TorManagerException) {
                builder.remove(geoIp6File)
                // TODO: debugger
                e.printStackTrace()
            }
        } else {
            builder.remove(geoIp6File)
        }

        val configLoad = builder.build()

        // Filter out settings that are not required at
        // startup.
        val configStart = TorConfig.Builder {
            for (setting in configLoad.settings) {
                @OptIn(InternalTorApi::class)
                if (setting.isStartArgument) {
                    put(setting)
                }
            }

            this
        }.build()

        val lines = configStart.text.lines()
        val configLines: MutableList<String> = ArrayList((lines.size * 2) + 5)

        configLines.add("-f")
        configLines.add(torrcFile.value)
        configLines.add("--defaults-torrc")
        configLines.add(torrcDefaultsFile.value)
        configLines.add("--ignore-missing-torrc")

        // Mutate what torrc file (config.text) lines into what tor_run_main expects
        //
        // Before (single line)-> Setting Flag Flag Flag
        // After (multi-line)  -> --Setting
        //                        Flag Flag Flag
        for (line in lines) {
            val first = line.substringBefore(' ')
            val second = line.substringAfter(' ')

            if (first.isEmpty() || second.isEmpty()) {
                continue
            }

            configLines.add("--$first")
            configLines.add(second)
        }

        return ValidatedTorConfig(
            configLoad,
            configLines,
            controlPortFilePath,
            cookieAuthFilePath
        ).also {
            _lastValidatedTorConfig.value = it
        }
    }
}
