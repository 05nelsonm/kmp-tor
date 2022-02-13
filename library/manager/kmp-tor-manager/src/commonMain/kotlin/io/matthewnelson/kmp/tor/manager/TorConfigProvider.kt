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
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.jvm.JvmSynthetic
import kotlin.reflect.KClass

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

    /**
     * Called from a background thread, so it is safe to perform IO from here.
     * */
    protected abstract fun provide(): TorConfig

    /**
     * Called from a background thread, so it is safe to perform IO from here.
     *
     * This will only be called if [TorConfig.Setting.GeoIpV4File] is set
     * in the client config retrieved from [provide], or if [geoIpV4File] is not null.
     *
     * The [geoIpV4File] setting is always preferred over the client [TorConfig]
     * setting.
     *
     * [TorConfig.Setting.GeoIpV4File] will automatically be added to the [TorConfig]
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
        val torConfig: TorConfig,
        val configLines: List<String>,
        val controlPortFile: Path,
        internal val cookieAuthFile: Path?, // if null, cookieAuth is set to False
    )

    @JvmSynthetic
    internal suspend fun retrieve(
        // remove certain settings if present, for example,
        // anything to do with Unix Sockets for Android,
        // Jvm (windows), NodeJs (windows), Windows targets.
        excludeSettings: Set<TorConfig.Setting<*>>,
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

        val validatedPorts: Set<Ports> = validatePortOptions(isPortAvailable, clientConfig)

        val builder: TorConfig.Builder = TorConfig.Builder {
            put(validatedPorts)

            for (setting in clientConfig.settings) {
                if (setting is Ports) {
                    continue
                }

                put(setting)
            }

            for (setting in excludeSettings) {
                removeInstanceOf(setting::class)
            }
            
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

            putIfAbsent(SyslogIdentityTag().set(FieldId("TorManager")))
            putIfAbsent(CacheDirectory().set(FileSystemDir(cacheDir)))
            putIfAbsent(dataDir)

            this
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

        val geoIpFile = GeoIpV4File()
        val geoIpFilePath: Path? = clientConfig
            .settings
            .filterIsInstance<GeoIpV4File>()
            .firstOrNull()
            ?.value
            .let { path ->
                path?.path ?: if (geoIpV4File?.value?.isNotEmpty() == true) geoIpV4File else null
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
            .filterIsInstance<GeoIpV6File>()
            .firstOrNull()
            ?.value
            .let { path ->
                path?.path ?: if (geoIpV6File?.value?.isNotEmpty() == true) geoIpV6File else null
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

        val newConfig = builder.build()
        val lines = newConfig.text.lines()
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
            newConfig,
            configLines,
            controlPortFilePath,
            cookieAuthFilePath
        ).also {
            _lastValidatedTorConfig.value = it
        }
    }

    /**
     * Validates ports for:
     *  - Availability
     *  - No conflicting values
     *
     * Returns a list of ports that need modification
     * */
    @Suppress("unchecked_cast")
    private fun <T: Ports> validatePortOptions(
        isPortAvailable: (Port) -> Boolean,
        clientConfig: TorConfig,
    ): Set<T> {
        val validatedPorts: MutableSet<T> = mutableSetOf()
        val takenPorts: MutableSet<Port> = mutableSetOf()

        var hasControlPort = false
        for (setting in clientConfig.settings) {
            if (setting !is Ports) {
                continue
            }

            if (setting is Ports.Control) {
                hasControlPort = true
            }

            when (val option = setting.value) {
                is AorDorPort.Auto,
                is AorDorPort.Disable -> {
                    validatedPorts.add(setting as T)
                }
                is AorDorPort.Value -> {
                    when {
                        takenPorts.contains(option.port) -> {
                            // port already taken, set to auto
                            validatedPorts.add((setting.clone().set(AorDorPort.Auto) as T))
                        }
                        !isPortAvailable.invoke(option.port) -> {
                            // port unavailable, set to auto
                            validatedPorts.add((setting.clone().set(AorDorPort.Auto) as T))
                        }
                        else -> {
                            // add to already set port values
                            takenPorts.add(option.port)
                            validatedPorts.add(setting as T)
                        }
                    }
                }
            }
        }

        if (!hasControlPort) {
            validatedPorts.add(Ports.Control() as T)
        }

        return validatedPorts
    }
}
