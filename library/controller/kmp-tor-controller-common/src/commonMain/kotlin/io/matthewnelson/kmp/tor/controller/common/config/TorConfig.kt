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
package io.matthewnelson.kmp.tor.controller.common.config

import io.matthewnelson.kmp.tor.common.address.Port
import io.matthewnelson.kmp.tor.common.address.PortProxy
import io.matthewnelson.kmp.tor.common.annotation.ExperimentalTorApi
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import io.matthewnelson.kmp.tor.common.annotation.SealedValueClass
import io.matthewnelson.kmp.tor.common.util.TorStrings.REDACTED
import io.matthewnelson.kmp.tor.common.util.TorStrings.SP
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.TorF.False
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.TorF.True
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.controller.common.internal.PlatformUtil
import io.matthewnelson.kmp.tor.controller.common.internal.appendTo
import io.matthewnelson.kmp.tor.controller.common.internal.isUnixPath
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlin.jvm.*
import kotlin.reflect.KClass

/**
 * Holder for Tor configuration information.
 *
 * @see [Builder]
 * @see [Setting]
 * @see [Option]
 * @see [KeyWord]
 * */
@OptIn(ExperimentalTorApi::class, InternalTorApi::class)
@Suppress("RemoveRedundantQualifierName", "SpellCheckingInspection")
class TorConfig private constructor(
    @JvmField
    val settings: Set<TorConfig.Setting<*>>,
    @JvmField
    val text: String,
) {

    fun newBuilder(action: Builder.() -> Builder): Builder = Builder {
        put(this@TorConfig)
        action.invoke(this)
    }

    fun newBuilder(): Builder = Builder().put(this)

    override fun equals(other: Any?): Boolean {
        return  other != null               &&
                other is TorConfig          &&
                other.text == text
    }

    override fun hashCode(): Int {
        return 17 * 31 + text.hashCode()
    }

    override fun toString(): String {
        return "TorConfig(settings=$REDACTED, text=$REDACTED)"
    }

    /**
     * Build your [TorConfig] to write to disk (torrc file), or load via the controller.
     *
     * Ex:
     *
       import io.matthewnelson.kmp.tor.common.address.Port
       import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
       // importing subclasses makes things less painful.
       import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.*
       import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Setting.*
       import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.*

       class TorConfig_Builder_Example {

           val myConfigBuilder: Builder = Builder {
               put(DisableNetwork().set(TorF.False))
           }

           val myConfig: TorConfig = myConfigBuilder.build()

           val myUpdatedConfig: TorConfig = myConfig.newBuilder {
               put(Ports.Control().set(AorDorPort.Value(Port(9051))))
               remove(DisableNetwork())
               removeInstanceOf(DisableNetwork::class)
           }.build()
       }
     * */
    class Builder {

        init {
            // Reference so that localhostAddress for JVM can have its initial
            // value set immediately from BG thread.
            PlatformUtil
        }

        private val settings: MutableSet<TorConfig.Setting<*>> = mutableSetOf()

        fun remove(setting: TorConfig.Setting<*>): Builder = apply {
            settings.remove(setting)
        }

        @Suppress("unchecked_cast")
        fun <T: Setting<*>> removeInstanceOf(clazz: KClass<T>): Builder = apply {
            val toRemove = mutableListOf<T>()
            for (setting in settings) {
                if (setting::class == clazz) {
                    toRemove.add(setting as T)
                }
            }

            for (setting in toRemove) {
                settings.remove(setting)
            }
        }

        fun put(config: TorConfig): Builder = apply {
            for (setting in config.settings) {
                if (!settings.add(setting)) {
                    settings.remove(setting)
                    settings.add(setting)
                }
            }
        }

        fun put(setting: TorConfig.Setting<*>): Builder = apply {
            setting.value?.let {
                val clone = setting.clone()
                if (!settings.add(clone)) {
                    settings.remove(clone)
                    settings.add(clone)
                }
            } ?: remove(setting)
        }

        fun put(settings: Collection<TorConfig.Setting<*>>): Builder = apply {
            for (setting in settings) {
                put(setting)
            }
        }

        fun putIfAbsent(setting: TorConfig.Setting<*>): Builder = apply {
            setting.value?.let { settings.add(setting.clone()) } ?: remove(setting)
        }

        fun build(): TorConfig {
            val sb = StringBuilder()

            val disabledPorts = mutableSetOf<KeyWord>()
            val sorted = settings.sortedBy { setting ->
                when (setting) {
                    // Ports must come before UnixSocket to ensure
                    // disabled ports are removed
                    is Setting.Ports -> {
                        if (setting.value is Option.AorDorPort.Disable) {
                            disabledPorts.add(setting.keyword)
                        }
                        "AAAA${setting.keyword}"
                    }
                    is Setting.UnixSockets -> {
                        "AAA${setting.keyword}"
                    }
                    else -> {
                        setting.keyword.toString()
                    }
                }
            }

            val writtenDisabledPorts: MutableSet<KeyWord> = LinkedHashSet(disabledPorts.size)

            val newSettings = mutableSetOf<Setting<*>>()
            for (setting in sorted) {

                when (setting) {
                    is Setting.Ports -> {
                        if (disabledPorts.contains(setting.keyword)) {
                            if (!writtenDisabledPorts.contains(setting.keyword)) {
                                sb.append(setting.keyword)
                                sb.append(SP)
                                sb.append(Option.AorDorPort.Disable.value)
                                sb.appendLine()
                                writtenDisabledPorts.add(setting.keyword)
                            }

                            if (setting.value is Option.AorDorPort.Disable) {
                                newSettings.add(setting.setImmutable())
                            }

                            continue
                        }

                        if (!setting.appendTo(sb, isWriteTorConfig = true)) {
                            continue
                        }
                    }
                    is Setting.UnixSockets -> {
                        if (disabledPorts.contains(setting.keyword)) continue

                        if (!setting.appendTo(sb, isWriteTorConfig = true)) {
                            continue
                        }
                    }
                    else -> {
                        if (!setting.appendTo(sb, isWriteTorConfig = true)) {
                            continue
                        }
                    }
                }

                newSettings.add(setting.setImmutable())
                sb.appendLine()
            }

            return TorConfig(newSettings.toSet(), sb.toString())
        }

        companion object {
            operator fun invoke(block: Builder.() -> Builder): Builder =
                block.invoke(Builder())
        }
    }

    /**
     * Settings's for configuring Tor.
     *
     * Ex:
     *
     * val socksPort = Setting.Ports.Socks()
     *     .setFlags(flags = setOf(
     *         Setting.Ports.Socks.Flag.OnionTrafficOnly
     *     ))
     *     .setIsolationFlags(flags = setOf(
     *         Setting.Ports.IsolationFlag.IsolateClientProtocol,
     *         Setting.Ports.IsolationFlag.IsolateDestAddr,
     *     ))
     *     .set(Option.AorDorPort.Value(port = Port(9250)))
     *
     * https://2019.www.torproject.org/docs/tor-manual.html.en
     * */
    @Suppress("PropertyName", "CanBePrimaryConstructorProperty")
    sealed class Setting<T: Option?>(
        @JvmField
        val keyword: TorConfig.KeyWord,
        @JvmField
        val default: T,
        isStartArgument: Boolean,
    ) {

        private val _value: AtomicRef<T> = atomic(default)
        @get:JvmName("value")
        val value: T get() = _value.value

        @get:JvmName("isDefault")
        val isDefault: Boolean get() = value == default

        private val _isMutable: AtomicBoolean = atomic(true)
        @get:JvmName("isMutable")
        val isMutable: Boolean get() = _isMutable.value

        @JvmField
        @InternalTorApi
        val isStartArgument: Boolean = isStartArgument

        @JvmSynthetic
        internal fun setImmutable(): Setting<T> {
            _isMutable.update { false }
            return this
        }

        open fun set(value: T): Setting<T> {
            if (isMutable) {
                _value.update { value }
            }
            return this
        }

        open fun setDefault(): Setting<T> {
            if (isMutable) {
                _value.update { default }
            }
            return this
        }

        abstract fun clone(): Setting<T>

        override fun equals(other: Any?): Boolean {
            return  other != null               &&
                    other is Setting<*>         &&
                    other.keyword == keyword
        }

        override fun hashCode(): Int {
            return 17 * 31 + keyword.hashCode()
        }

        override fun toString(): String {
            return "${this::class.simpleName}(keyword=$keyword, value=$value, default=$default)"
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#AutomapHostsOnResolve
         * */
        class AutomapHostsOnResolve                 : Setting<Option.TorF>(
            keyword = KeyWord.AutomapHostsOnResolve,
            default = Option.TorF.True,
            isStartArgument = false,
        ) {

            override fun clone(): AutomapHostsOnResolve {
                return AutomapHostsOnResolve().set(value) as AutomapHostsOnResolve
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#CacheDirectory
         * */
        class CacheDirectory                        : Setting<Option.FileSystemDir?>(
            keyword = KeyWord.CacheDirectory,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FileSystemDir?): Setting<Option.FileSystemDir?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): CacheDirectory {
                return CacheDirectory().set(value) as CacheDirectory
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ClientOnionAuthDir
         * */
        class ClientOnionAuthDir                    : Setting<Option.FileSystemDir?>(
            keyword = KeyWord.ClientOnionAuthDir,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FileSystemDir?): Setting<Option.FileSystemDir?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): ClientOnionAuthDir {
                return ClientOnionAuthDir().set(value) as ClientOnionAuthDir
            }

            companion object {
                const val DEFAULT_NAME = "auth_private_files"
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ConnectionPadding
         * */
        class ConnectionPadding                     : Setting<Option.AorTorF>(
            keyword = KeyWord.ConnectionPadding,
            default = Option.AorTorF.Auto,
            isStartArgument = false,
        ) {

            override fun clone(): ConnectionPadding {
                return ConnectionPadding().set(value) as ConnectionPadding
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ConnectionPaddingReduced
         * */
        class ConnectionPaddingReduced              : Setting<Option.TorF>(
            keyword = KeyWord.ReducedConnectionPadding,
            default = Option.TorF.False,
            isStartArgument = false,
        ) {

            override fun clone(): ConnectionPaddingReduced {
                return ConnectionPaddingReduced().set(value) as ConnectionPaddingReduced
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPortWriteToFile
         * */
        class ControlPortWriteToFile                : Setting<Option.FileSystemFile?>(
            keyword = KeyWord.ControlPortWriteToFile,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FileSystemFile?): Setting<Option.FileSystemFile?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): ControlPortWriteToFile {
                return ControlPortWriteToFile().set(value) as ControlPortWriteToFile
            }

            companion object {
                const val DEFAULT_NAME = "control.txt"
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#CookieAuthentication
         * */
        class CookieAuthentication                  : Setting<Option.TorF>(
            keyword = KeyWord.CookieAuthentication,
            default = Option.TorF.True,
            isStartArgument = true,
        ) {

            override fun clone(): CookieAuthentication {
                return CookieAuthentication().set(value) as CookieAuthentication
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#CookieAuthFile
         * */
        class CookieAuthFile                        : Setting<Option.FileSystemFile?>(
            keyword = KeyWord.CookieAuthFile,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FileSystemFile?): Setting<Option.FileSystemFile?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): CookieAuthFile {
                return CookieAuthFile().set(value) as CookieAuthFile
            }

            companion object {
                const val DEFAULT_NAME = "control_auth_cookie"
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DataDirectory
         * */
        class DataDirectory                         : Setting<Option.FileSystemDir?>(
            keyword = KeyWord.DataDirectory,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FileSystemDir?): Setting<Option.FileSystemDir?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): DataDirectory {
                return DataDirectory().set(value) as DataDirectory
            }

            companion object {
                const val DEFAULT_NAME = "data"
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DisableNetwork
         * */
        class DisableNetwork                        : Setting<Option.TorF>(
            keyword = KeyWord.DisableNetwork,
            default = Option.TorF.False,
            isStartArgument = true,
        ) {

            override fun clone(): DisableNetwork {
                return DisableNetwork().set(value) as DisableNetwork
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantCanceledByStartup
         * */
        class DormantCanceledByStartup              : Setting<Option.TorF>(
            keyword = KeyWord.DormantCanceledByStartup,
            default = Option.TorF.False,
            isStartArgument = true,
        ) {

            override fun clone(): DormantCanceledByStartup {
                return DormantCanceledByStartup().set(value) as DormantCanceledByStartup
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantClientTimeout
         * */
        class DormantClientTimeout                  : Setting<Option.Time>(
            keyword = KeyWord.DormantClientTimeout,
            default = Option.Time.Hours(24),
            isStartArgument = false,
        ) {

            override fun set(value: Option.Time): Setting<Option.Time> {
                return if (value is Option.Time.Minutes && value.time < 10) {
                    super.set(Option.Time.Minutes(10))
                } else {
                    super.set(value)
                }
            }

            override fun clone(): DormantClientTimeout {
                return DormantClientTimeout().set(value) as DormantClientTimeout
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantOnFirstStartup
         * */
        class DormantOnFirstStartup                 : Setting<Option.TorF>(
            keyword = KeyWord.DormantOnFirstStartup,
            default = Option.TorF.False,
            isStartArgument = true,
        ) {

            override fun clone(): DormantOnFirstStartup {
                return DormantOnFirstStartup().set(value) as DormantOnFirstStartup
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantTimeoutDisabledByIdleStreams
         * */
        class DormantTimeoutDisabledByIdleStreams   : Setting<Option.TorF>(
            keyword = KeyWord.DormantTimeoutDisabledByIdleStreams,
            default = Option.TorF.True,
            isStartArgument = false,
        ) {

            override fun clone(): DormantTimeoutDisabledByIdleStreams {
                return DormantTimeoutDisabledByIdleStreams().set(value) as DormantTimeoutDisabledByIdleStreams
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPExcludeUnknown
         * */
        class GeoIPExcludeUnknown                   : Setting<Option.AorTorF>(
            keyword = KeyWord.GeoIPExcludeUnknown,
            default = Option.AorTorF.Auto,
            isStartArgument = false,
        ) {

            override fun clone(): GeoIPExcludeUnknown {
                return GeoIPExcludeUnknown().set(value) as GeoIPExcludeUnknown
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPFile
         * */
        class GeoIpV4File                           : Setting<Option.FileSystemFile?>(
            keyword = KeyWord.GeoIpFile,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FileSystemFile?): Setting<Option.FileSystemFile?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): GeoIpV4File {
                return GeoIpV4File().set(value) as GeoIpV4File
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPv6File
         * */
        class GeoIpV6File                           : Setting<Option.FileSystemFile?>(
            keyword = KeyWord.GeoIPv6File,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FileSystemFile?): Setting<Option.FileSystemFile?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): GeoIpV6File {
                return GeoIpV6File().set(value) as GeoIpV6File
            }
        }

        /**
         * val hsDirPath = workDir.builder {
         *     addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
         *     addSegment("my_hidden_service")
         * }
         *
         * val myHiddenService = Setting.HiddenService()
         *     .setPorts(ports = setOf(
         *         Setting.HiddenService.UnixSocket(virtualPort = Port(80), targetUnixSocket = hsDirPath.builder {
         *             addSegment(Setting.HiddenService.UnixSocket.DEFAULT_UNIX_SOCKET_NAME)
         *         })
         *         Setting.HiddenService.Ports(virtualPort = Port(22))
         *         Setting.HiddenService.Ports(virtualPort = Port(8022), targetPort = Port(22))
         *     ))
         *     .setMaxStreams(maxStreams = Setting.HiddenService.MaxStreams(2))
         *     .setMaxStreamsCloseCircuit(value = TorF.False)
         *     .set(FileSystemDir(hsDirPath))
         *
         * Note that both `set` and `setPorts` _must_ be called for it to be added
         * to your config.
         *
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceDir
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServicePort
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreams
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreamsCloseCircuit
         * */
        class HiddenService                         : Setting<Option.FileSystemDir?>(
            keyword = KeyWord.HiddenServiceDir,
            default = null,
            isStartArgument = false,
        ) {

            /**
             * See [HiddenService.VirtualPort]
             * */
            private val _ports: AtomicRef<Set<HiddenService.VirtualPort>?> = atomic(null)
            @get:JvmName("ports")
            val ports: Set<HiddenService.VirtualPort>? get() = _ports.value

            /**
             * A value of `null` means it will not be written to the config and falls
             * back to Tor's default value of 0 (unlimited).
             *
             * @see [MaxStreams]
             * */
            private val _maxStreams: AtomicRef<MaxStreams?> = atomic(null)
            @get:JvmName("maxStreams")
            val maxStreams: MaxStreams? get() = _maxStreams.value

            /**
             * A value of `null` means it will not be written to the config and falls
             * back to Tor's default setting of [Option.TorF.False]
             * */
            private val _maxStreamsCloseCircuit: AtomicRef<Option.TorF?> = atomic(null)
            @get:JvmName("maxStreamsCloseCircuit")
            val maxStreamsCloseCircuit: Option.TorF? get() = _maxStreamsCloseCircuit.value

            override fun set(value: Option.FileSystemDir?): Setting<Option.FileSystemDir?> {
                return super.set(value?.nullIfEmpty)
            }

            fun setPorts(ports: Set<VirtualPort>?): HiddenService {
                if (isMutable) {
                    if (ports.isNullOrEmpty()) {
                        _ports.update { null }
                    } else {
                        val filtered = ports.filter { instance ->
                            when (instance) {
                                is UnixSocket -> {
                                    if (!PlatformUtil.isLinux) {
                                        false
                                    } else {
                                        instance.targetUnixSocket.isUnixPath
                                    }
                                }
                                is Ports -> true
                            }
                        }

                        _ports.update {
                            if (filtered.isNotEmpty()) {
                                filtered.toSet()
                            } else {
                                null
                            }
                        }
                    }
                }

                return this
            }

            fun setMaxStreams(maxStreams: MaxStreams?): HiddenService {
                if (isMutable) {
                    _maxStreams.update { maxStreams }
                }
                return this
            }

            fun setMaxStreamsCloseCircuit(value: Option.TorF?): HiddenService {
                if (isMutable) {
                    _maxStreamsCloseCircuit.update { value }
                }
                return this
            }

            override fun setDefault(): HiddenService {
                if (isMutable) {
                    super.setDefault()
                    _ports.update {  null }
                    _maxStreams.update {  null }
                    _maxStreamsCloseCircuit.update {  null }
                }
                return this
            }

            override fun clone(): HiddenService {
                return HiddenService()
                    .setPorts(ports)
                    .setMaxStreams(maxStreams)
                    .setMaxStreamsCloseCircuit(maxStreamsCloseCircuit)
                    .set(value) as HiddenService
            }

            override fun equals(other: Any?): Boolean {
                return other is HiddenService && other.value == value
            }

            override fun hashCode(): Int {
                return 14 * 31 + value.hashCode()
            }

            /**
             * See [HiddenService.Ports] && [HiddenService.UnixSocket]
             * */
            sealed class VirtualPort

            /**
             * By default, [virtualPort] is always mapped to <localhostIp>:[targetPort]. This
             * can be overridden by expressing a different value for [targetPort].
             *
             * EX:
             *  - Server running on Port(31276) with endpoint `/api/v1/endpoint`
             *  - Listen for all http traffic:
             *      Ports(virtualPort = Port(80), targetPort = Port(31276))
             *
             *      http://<onion-address>.onion/api/v1/endpoint
             *
             *  - Server configured for SSL connections, listen for all https traffic:
             *      Ports(virtualPort = Port(443), targetPort = Port(31276))
             *
             *      https://<onion-address>.onion/api/v1/endpoint
             *
             *  - Server configured for SSL connections:
             *      Ports(virtualPort = Port(31276))
             *
             *      https://<onion-address>.onion:31276/api/v1/endpoint
             *
             * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServicePort
             * */
            data class Ports(
                @JvmField
                val virtualPort: Port,
                @JvmField
                val targetPort: Port = virtualPort,
            ): VirtualPort()

            /**
             * Instead of directing traffic to a server on the local machine via
             * TCP connection (and potentially leaking info), use a Unix Domain
             * Socket and communicate via IPC.
             *
             * Support for this is only available for linux. If not on linux (or
             * android). All [UnixSocket]s will be removed when [setPorts] is
             * called, so it is safe to call this from common code.
             *
             * You can check [PlatformUtil.isLinux] when setting up your hidden
             * service if you need to implement a fallback to use TCP.
             *
             * EX:
             *  - Server running on unix domain socket with endpoint `/api/v1/endpoint`
             *  - Listen for all http traffic:
             *      UnixSocket(
             *          virtualPort = Port(80),
             *          targetUnixSocket = Path("/home/user/.myApp/torservice/hidden_services/my_service/hs.sock")
             *      )
             *
             *      http://<onion-address>.onion/api/v1/endpoint
             *
             * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServicePort
             * */
            data class UnixSocket(
                @JvmField
                val virtualPort: Port,
                @JvmField
                val targetUnixSocket: Path,
            ): VirtualPort() {

                companion object {
                    const val DEFAULT_UNIX_SOCKET_NAME = "hs.sock"
                }
            }

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreams
             *
             * @throws [IllegalArgumentException] if [value] is not within the inclusive range
             *   of 0 and 65535
             * */
            @SealedValueClass
            sealed interface MaxStreams {
                val value: Int

                companion object {
                    @JvmStatic
                    @Throws(IllegalArgumentException::class)
                    operator fun invoke(value: Int): MaxStreams {
                        return RealMaxStreams(value)
                    }
                }
            }

            @JvmInline
            private value class RealMaxStreams(override val value: Int): MaxStreams {
                init {
                    require(value in Port.MIN..Port.MAX) {
                        "MaxStreams.value must be between ${Port.MIN} and ${Port.MAX}"
                    }
                }

                override fun toString(): String {
                    return "MaxStreams(value=$value)"
                }
            }

            companion object {
                const val DEFAULT_PARENT_DIR_NAME = "hidden_services"
            }
        }

        /**
         * https://torproject.gitlab.io/torspec/control-spec/#takeownership
         * */
        class OwningControllerProcess               : Setting<Option.ProcessId?>(
            keyword = KeyWord.__OwningControllerProcess,
            default = null,
            isStartArgument = true,
        ) {

            override fun clone(): OwningControllerProcess {
                return OwningControllerProcess().set(value) as OwningControllerProcess
            }
        }

        /**
         * Configure TCP Ports for Tor's
         *  - [KeyWord.ControlPort]
         *  - [KeyWord.DnsPort]
         *  - [KeyWord.HttpTunnelPort]
         *  - [KeyWord.SocksPort]
         *  - [KeyWord.TransPort]
         *
         * @see [UnixSockets]
         * */
        sealed class Ports(
            keyword: TorConfig.KeyWord,
            default: Option.AorDorPort,
            isStartArgument: Boolean,
        ) : Setting<Option.AorDorPort>(
            keyword,
            default,
            isStartArgument
        ) {

            override fun equals(other: Any?): Boolean {
                if (other == null) {
                    return false
                }

                if (other !is Ports) {
                    return false
                }

                val otherValue = other.value
                val thisValue = value
                return if (
                    otherValue is Option.AorDorPort.Value &&
                    thisValue is Option.AorDorPort.Value
                ) {
                    // compare ports as to disallow setting same port for different Ports
                    otherValue == thisValue
                } else {
                    other.keyword == keyword && otherValue == thisValue
                }
            }

            override fun hashCode(): Int {
                var result = 17 - 2
                if (value is Option.AorDorPort.Value) {
                    // take value of the port only
                    result = result * 31 + value.hashCode()
                } else {
                    result = result * 31 + keyword.hashCode()
                    result = result * 31 + value.hashCode()
                }
                return result
            }

            /**
             *
             * Note that Tor's default value as per the spec is disabled (0), so
             * excluding it from your config will not set it to a Port. As this
             * library depends on the [Control] port, the default value here differs
             * and cannot be set to [Option.AorDorPort.Disable].
             *
             * It is not necessary to add this to your [TorConfig] when using TorManager,
             * as if it is absent `TorManager.start` will automatically configure the
             * [KeyWord.ControlPort] and open a Tor control connection for you.
             *
             * https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPort
             *
             * @see [UnixSockets.Control]
             * */
            class Control                               : Ports(
                keyword = KeyWord.ControlPort,
                default = Option.AorDorPort.Auto,
                isStartArgument = true,
            ) {

                override fun set(value: Option.AorDorPort): Setting<Option.AorDorPort> {
                    return if (value !is Option.AorDorPort.Disable) {
                        super.set(value)
                    } else {
                        this
                    }
                }

                override fun clone(): Control {
                    return Control().set(value) as Control
                }
            }

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#DNSPort
             * */
            class Dns                                   : Ports(
                keyword = KeyWord.DnsPort,
                default = Option.AorDorPort.Disable,
                isStartArgument = false,
            ) {

                private val _isolationFlags: AtomicRef<Set<IsolationFlag>?> = atomic(null)
                @get:JvmName("isolationFlags")
                val isolationFlags: Set<IsolationFlag>? get() = _isolationFlags.value

                override fun setDefault(): Dns {
                    if (isMutable) {
                        super.setDefault()
                        _isolationFlags.update { null }
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Dns {
                    if (isMutable) {
                        _isolationFlags.update { flags?.toSet() }
                    }
                    return this
                }

                override fun clone(): Dns {
                    return Dns().setIsolationFlags(isolationFlags).set(value) as Dns
                }
            }

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#HTTPTunnelPort
             * */
            class HttpTunnel                            : Ports(
                keyword = KeyWord.HttpTunnelPort,
                default = Option.AorDorPort.Disable,
                isStartArgument = false,
            ) {

                private val _isolationFlags: AtomicRef<Set<IsolationFlag>?> = atomic(null)
                @get:JvmName("isolationFlags")
                val isolationFlags: Set<IsolationFlag>? get() = _isolationFlags.value

                override fun setDefault(): HttpTunnel {
                    if (isMutable) {
                        super.setDefault()
                        _isolationFlags.update { null }
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): HttpTunnel {
                    if (isMutable) {
                        _isolationFlags.update { flags?.toSet() }
                    }
                    return this
                }

                override fun clone(): HttpTunnel {
                    return HttpTunnel().setIsolationFlags(isolationFlags).set(value) as HttpTunnel
                }
            }

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#SocksPort
             * */
            class Socks                                 : Ports(
                keyword = KeyWord.SocksPort,
                default = Option.AorDorPort.Value(PortProxy(9050)),
                isStartArgument = false,
            ) {

                private val _flags: AtomicRef<Set<Flag>?> = atomic(null)
                @get:JvmName("flags")
                val flags: Set<Flag>? get() = _flags.value

                private val _isolationFlags: AtomicRef<Set<IsolationFlag>?> = atomic(null)
                @get:JvmName("isolationFlags")
                val isolationFlags: Set<IsolationFlag>? get() = _isolationFlags.value

                override fun setDefault(): Socks {
                    if (isMutable) {
                        super.setDefault()
                        _flags.update { null }
                        _isolationFlags.update { null }
                    }
                    return this
                }

                fun setFlags(flags: Set<Flag>?): Socks {
                    if (isMutable) {
                        _flags.update { flags?.toSet() }
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Socks {
                    if (isMutable) {
                        _isolationFlags.update { flags?.toSet() }
                    }
                    return this
                }

                override fun clone(): Socks {
                    return Socks()
                        .setFlags(flags)
                        .setIsolationFlags(isolationFlags)
                        .set(value) as Socks
                }

                sealed class Flag(@JvmField val value: String) {

                    override fun toString(): String = value

                    object NoIPv4Traffic                    : Flag("NoIPv4Traffic")
                    object IPv6Traffic                      : Flag("IPv6Traffic")
                    object PreferIPv6                       : Flag("PreferIPv6")
                    object NoDNSRequest                     : Flag("NoDNSRequest")
                    object NoOnionTraffic                   : Flag("NoOnionTraffic")
                    object OnionTrafficOnly                 : Flag("OnionTrafficOnly")
                    object CacheIPv4DNS                     : Flag("CacheIPv4DNS")
                    object CacheIPv6DNS                     : Flag("CacheIPv6DNS")
                    object CacheDNS                         : Flag("CacheDNS")
                    object UseIPv4Cache                     : Flag("UseIPv4Cache")
                    object UseIPv6Cache                     : Flag("UseIPv6Cache")
                    object UseDNSCache                      : Flag("UseDNSCache")
                    object PreferIPv6Automap                : Flag("PreferIPv6Automap")
                    object PreferSOCKSNoAuth                : Flag("PreferSOCKSNoAuth")
                }
            }

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#TransPort
             * */
            class Trans                                 : Ports(
                keyword = KeyWord.TransPort,
                default = Option.AorDorPort.Disable,
                isStartArgument = false,
            ) {

                private val _isolationFlags: AtomicRef<Set<IsolationFlag>?> = atomic(null)
                @get:JvmName("isolationFlags")
                val isolationFlags: Set<IsolationFlag>? get() = _isolationFlags.value

                override fun set(value: Option.AorDorPort): Setting<Option.AorDorPort> {
                    return if (PlatformUtil.isLinux) {
                        super.set(value)
                    } else {
                        this
                    }
                }

                override fun setDefault(): Trans {
                    if (isMutable) {
                        super.setDefault()
                        _isolationFlags.update { null }
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Trans {
                    if (isMutable) {
                        _isolationFlags.update { flags?.toSet() }
                    }
                    return this
                }

                override fun clone(): Trans {
                    return Trans().setIsolationFlags(isolationFlags).set(value) as Trans
                }
            }

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#SocksPort
             * */
            sealed class IsolationFlag(@JvmField val value: String) {

                override fun toString(): String = value

                object IsolateClientAddr                : IsolationFlag("IsolateClientAddr")
                object IsolateSOCKSAuth                 : IsolationFlag("IsolateSOCKSAuth")
                object IsolateClientProtocol            : IsolationFlag("IsolateClientProtocol")
                object IsolateDestPort                  : IsolationFlag("IsolateDestPort")
                object IsolateDestAddr                  : IsolationFlag("IsolateDestAddr")
                object KeepAliveIsolateSOCKSAuth        : IsolationFlag("KeepAliveIsolateSOCKSAuth")
                class SessionGroup(id: Int)             : IsolationFlag("SessionGroup=$id") {

                    init {
                        require(id >= 0) {
                            "SessionGroup.id must be greater than or equal to 0"
                        }
                    }

                    override fun equals(other: Any?): Boolean {
                        return other != null && other is SessionGroup
                    }

                    override fun hashCode(): Int {
                        return 17 * 31 + "SessionGroup".hashCode()
                    }
                }
            }
        }

        /**
         * Specific [Setting] for using unix domain sockets for the [KeyWord.ControlPort] and
         * [KeyWord.SocksPort]. Unix Domain Socket support is limited by Tor to Linux (and Android).
         *
         * @see [UnixSockets.Control]
         * @see [UnixSockets.Socks]
         * */
        sealed class UnixSockets(
            keyword: TorConfig.KeyWord,
            isStartArgument: Boolean,
        ) : Setting<Option.FileSystemFile?>(
            keyword,
            default = null,
            isStartArgument
        ) {

            final override fun set(value: Option.FileSystemFile?): Setting<Option.FileSystemFile?> {
                // Do not set if platform is something other than linux
                if (!PlatformUtil.isLinux) return this
                // Do not set if unix domain sockets are not supported for control port
                if (this is Control && !PlatformUtil.hasControlUnixDomainSocketSupport) return this
                // First character of the path must be / (Unix FileSystem) root dir char
                if (value?.path?.isUnixPath != true) return this

                return super.set(value)
            }

            final override fun equals(other: Any?): Boolean {
                return other is UnixSockets && other.value == value
            }

            final override fun hashCode(): Int {
                var result = 17 - 3
                result = result * 31 + value.hashCode()
                return result
            }

            /**
             * [UnixSockets.Control] must have support to be added to the [TorConfig]. For Android,
             * nothing is needed as native support is had via `android.net.LocalSocket`. For the JVM,
             * the extension `kmp-tor-ext-unix-socket` must be added to you Linux distrobution.
             *
             * If [PlatformUtil.hasControlUnixDomainSocketSupport] is false, calling [Control.set] will
             * result in the [Option.FileSystemFile] not being set, and thus not being added to
             * [TorConfig].
             *
             * It is not necessary to add this to your [TorConfig] when using TorManager, as if
             * [PlatformUtil.hasControlUnixDomainSocketSupport] is true, it will automatically be
             * added for you as the preferred way to establish a Tor control connection. To override
             * this behavior, you can specify [Ports.Control] with your provided config.
             *
             * https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPort
             * */
            class Control                               : UnixSockets(
                keyword = KeyWord.ControlPort,
                isStartArgument = true,
            ) {

                private val _unixFlags: AtomicRef<Set<UnixSockets.Control.Flag>?> = atomic(null)
                @get:JvmName("unixFlags")
                val unixFlags: Set<UnixSockets.Control.Flag>? get() = _unixFlags.value

                override fun setDefault(): Control {
                    if (isMutable) {
                        super.setDefault()
                        _unixFlags.update { null }
                    }
                    return this
                }

                fun setUnixFlags(flags: Set<UnixSockets.Control.Flag>?): Control {
                    if (isMutable) {
                        _unixFlags.update { flags?.toSet() }
                    }
                    return this
                }

                override fun clone(): Control {
                    return Control().setUnixFlags(unixFlags).set(value) as Control
                }

                sealed class Flag(@JvmField val value: String) {

                    override fun toString(): String = value

                    object RelaxDirModeCheck         : Flag("RelaxDirModeCheck")

                    // conveniences...
                    companion object {
                        @get:JvmStatic
                        val GroupWritable get() = UnixSockets.Flag.GroupWritable
                        @get:JvmStatic
                        val WorldWritable get() = UnixSockets.Flag.WorldWritable
                    }
                }

                companion object {
                    const val DEFAULT_NAME = "control.sock"
                }
            }

            /**
             * [UnixSockets.Socks] must have support to be added to the [TorConfig].
             *
             * If [PlatformUtil.isLinux] is false, calling [Socks.set] will result in
             * the [Option.FileSystemFile] not being set, and thus not being added to
             * [TorConfig].
             *
             * If using TorManager, when you provide your [TorConfig] and there is
             * neither a [Ports.Socks] or [UnixSockets.Socks] present, [Ports.Socks]
             * will automatically be added for you. So, you can always add this [Setting]
             * when providing your [TorConfig] to prefer using [UnixSockets] over [Ports].
             *
             * The resulting [Path] for [UnixSockets.Socks] will be dispatched via
             * `AddressInfo.unixSocks` property when Tor bootstrapping is complete.
             *
             * https://2019.www.torproject.org/docs/tor-manual.html.en#SocksPort
             * */
            class Socks                             : UnixSockets(
                keyword = KeyWord.SocksPort,
                isStartArgument = false,
            ) {

                private val _flags: AtomicRef<Set<Ports.Socks.Flag>?> = atomic(null)
                @get:JvmName("flags")
                val flags: Set<Ports.Socks.Flag>? get() = _flags.value

                private val _unixFlags: AtomicRef<Set<UnixSockets.Flag>?> = atomic(null)
                @get:JvmName("unixFlags")
                val unixFlags: Set<UnixSockets.Flag>? get() = _unixFlags.value

                private val _isolationFlags: AtomicRef<Set<Ports.IsolationFlag>?> = atomic(null)
                @get:JvmName("isolationFlags")
                val isolationFlags: Set<Ports.IsolationFlag>? get() = _isolationFlags.value

                override fun setDefault(): Socks {
                    if (isMutable) {
                        super.setDefault()
                        _flags.update { null }
                        _unixFlags.update { null }
                        _isolationFlags.update { null }
                    }
                    return this
                }

                fun setFlags(flags: Set<Ports.Socks.Flag>?): Socks {
                    if (isMutable) {
                        _flags.update { flags?.toSet() }
                    }
                    return this
                }

                fun setUnixFlags(flags: Set<UnixSockets.Flag>?): Socks {
                    if (isMutable) {
                        _unixFlags.update { flags?.toSet() }
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<Ports.IsolationFlag>?): Socks {
                    if (isMutable) {
                        _isolationFlags.update { flags?.toSet() }
                    }
                    return this
                }

                override fun clone(): Socks {
                    return Socks()
                        .setFlags(flags)
                        .setUnixFlags(unixFlags)
                        .setIsolationFlags(isolationFlags)
                        .set(value) as Socks
                }

                companion object {
                    const val DEFAULT_NAME = "socks.sock"
                }
            }

            sealed class Flag(value: String): UnixSockets.Control.Flag(value) {
                object GroupWritable                    : Flag("GroupWritable")
                object WorldWritable                    : Flag("WorldWritable")
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#RunAsDaemon
         * */
        class RunAsDaemon                           : Setting<Option.TorF>(
            keyword = KeyWord.RunAsDaemon,
            default = Option.TorF.False,
            isStartArgument = true,
        ) {

            override fun clone(): RunAsDaemon {
                return RunAsDaemon().set(value) as RunAsDaemon
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#SyslogIdentityTag
         * */
        class SyslogIdentityTag                     : Setting<Option.FieldId?>(
            keyword = KeyWord.SyslogIdentityTag,
            default = null,
            isStartArgument = true,
        ) {

            override fun set(value: Option.FieldId?): Setting<Option.FieldId?> {
                return super.set(value?.nullIfEmpty)
            }

            override fun clone(): SyslogIdentityTag {
                return SyslogIdentityTag().set(value) as SyslogIdentityTag
            }
        }
    }

    sealed interface Option {
        val value: String

        /**
         * Either [True] or [False]
         * */
        sealed interface TorF                                           : AorTorF {
            object True                                                     : TorF {
                override val value: String = "1"
                override fun toString(): String = value
            }
            object False                                                    : TorF {
                override val value: String = "0"
                override fun toString(): String = value
            }
        }

        /**
         * Either [Auto], [True], or [False]
         * */
        sealed interface AorTorF                                        : Option {
            object Auto                                                     : AorTorF {
                override val value: String = "auto"
                override fun toString(): String = value
            }

            // conveniences...
            companion object {
                @get:JvmStatic
                val True: TorF.True get() = TorF.True
                @get:JvmStatic
                val False: TorF.False get() = TorF.False
            }
        }

        /**
         * Either [Auto], [Disable], or [Value] containing a [Port]
         * */
        sealed interface AorDorPort                                     : Option {
            object Auto                                                     : AorDorPort {
                override val value: String get() = AorTorF.Auto.value
                override fun toString(): String = value
            }

            object Disable                                                  : AorDorPort {
                override val value: String get() = TorF.False.value
                override fun toString(): String = value
            }

            @SealedValueClass
            sealed interface Value                                          : AorDorPort {
                val port: PortProxy

                companion object {
                    @JvmStatic
                    operator fun invoke(port: PortProxy): Value {
                        return RealValue(port)
                    }
                }
            }

            @JvmInline
            private value class RealValue(override val port: PortProxy)     : Value {
                override val value: String get() = port.value.toString()
                override fun toString(): String = value
            }
        }

        @SealedValueClass
        sealed interface FileSystemFile                                 : Option {
            val path: Path
            val nullIfEmpty: FileSystemFile?

            companion object {
                @JvmStatic
                operator fun invoke(path: Path): FileSystemFile {
                    return RealFileSystemFile(path)
                }
            }
        }

        @JvmInline
        private value class RealFileSystemFile(override val path: Path): FileSystemFile {
            override val value: String get() = path.value
            override val nullIfEmpty: FileSystemFile? get() = if (value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @SealedValueClass
        sealed interface FileSystemDir                                  : Option {
            val path: Path
            val nullIfEmpty: FileSystemDir?

            companion object {
                @JvmStatic
                operator fun invoke(path: Path): FileSystemDir {
                    return RealFileSystemDir(path)
                }
            }
        }

        @JvmInline
        private value class RealFileSystemDir(override val path: Path)  : FileSystemDir {
            override val value: String get() = path.value
            override val nullIfEmpty: FileSystemDir? get() = if (value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @SealedValueClass
        sealed interface FieldId                                        : Option {
            val nullIfEmpty: FieldId?

            companion object {
                @JvmStatic
                operator fun invoke(value: String): FieldId {
                    return RealFieldId(value)
                }
            }
        }

        @JvmInline
        private value class RealFieldId(override val value: String)     : FieldId {
            override val nullIfEmpty: FieldId? get() = if (value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @SealedValueClass
        sealed interface ProcessId                                      : Option {
            val pid: Int

            companion object {
                @JvmStatic
                operator fun invoke(pid: Int): ProcessId {
                    return RealProcessId(pid)
                }
            }
        }

        @JvmInline
        private value class RealProcessId(override val pid: Int)        : ProcessId {
            override val value: String get() = "$pid"
            override fun toString(): String = value
        }

        sealed interface Time                                           : Option {
            val time: Int

            @SealedValueClass
            sealed interface Minutes                                        : Time {
                companion object {
                    @JvmStatic
                    operator fun invoke(time: Int): Minutes {
                        return RealMinutes(time)
                    }
                }
            }

            @JvmInline
            private value class RealMinutes(override val time: Int)         : Minutes {
                override val value: String get() = if (time < 1) {
                    "1 minutes"
                } else {
                    "$time minutes"
                }

                override fun toString(): String = value
            }

            @SealedValueClass
            sealed interface Hours                                          : Time {
                companion object {
                    @JvmStatic
                    operator fun invoke(time: Int): Hours {
                        return RealHours(time)
                    }
                }
            }

            @JvmInline
            private value class RealHours(override val time: Int)           : Hours {
                override val value: String get() = if (time < 1) {
                    "1 hours"
                } else {
                    "$time hours"
                }

                override fun toString(): String = value
            }

            @SealedValueClass
            sealed interface Days                                           : Time {
                companion object {
                    @JvmStatic
                    operator fun invoke(time: Int): Days {
                        return RealDays(time)
                    }
                }
            }

            @JvmInline
            private value class RealDays(override val time: Int)            : Days {
                override val value: String get() = if (time < 1) {
                    "1 days"
                } else {
                    "$time days"
                }

                override fun toString(): String = value
            }

            @SealedValueClass
            sealed interface Weeks                                          : Time {
                companion object {
                    @JvmStatic
                    operator fun invoke(time: Int): Weeks {
                        return RealWeeks(time)
                    }
                }
            }

            @JvmInline
            private value class RealWeeks(override val time: Int)           : Weeks {
                override val value: String get() = if (time < 1) {
                    "1 weeks"
                } else {
                    "$time weeks"
                }

                override fun toString(): String = value
            }
        }
    }

    /**
     * KeyWords utilized in TorConfig, as defined in:
     *  - https://2019.www.torproject.org/docs/tor-manual.html.en
     *  - https://torproject.gitlab.io/torspec/control-spec/#tor-config-options-for-use-by-controllers
     *
     * Ordering of KeyWords match that of what is in the above manuals. To keep track of what
     * has and has not been implemented as a [Setting], the following "headers" are used:
     *
     *     /* IMPLEMENTED */
     *     /* NOT IMPLEMENTED */
     * */
    @Suppress("ClassName")
    sealed class KeyWord: Comparable<String>, CharSequence {

/* IMPLEMENTED */
/* === GENERAL OPTIONS ======================== IMPLEMENTED as a TorConfig.Setting ================================== */
        object DisableNetwork: KeyWord() { override fun toString(): String = "DisableNetwork" }
        object ControlPort: KeyWord() { override fun toString(): String = "ControlPort" }
        object CookieAuthentication: KeyWord() { override fun toString(): String = "CookieAuthentication" }
        object CookieAuthFile: KeyWord() { override fun toString(): String = "CookieAuthFile" }
        object ControlPortWriteToFile: KeyWord() { override fun toString(): String = "ControlPortWriteToFile" }
        object DataDirectory: KeyWord() { override fun toString(): String = "DataDirectory" }
        object CacheDirectory: KeyWord() { override fun toString(): String = "CacheDirectory" }
        object RunAsDaemon: KeyWord() { override fun toString(): String = "RunAsDaemon" }
        object SyslogIdentityTag: KeyWord() { override fun toString(): String = "SyslogIdentityTag" }


/* === CLIENT OPTIONS ========================= IMPLEMENTED as a TorConfig.Setting ================================== */
        object ConnectionPadding: KeyWord() { override fun toString(): String = "ConnectionPadding" }
        object ReducedConnectionPadding: KeyWord() { override fun toString(): String = "ReducedConnectionPadding" }
        object GeoIPExcludeUnknown: KeyWord() { override fun toString(): String = "GeoIPExcludeUnknown" }
        object ClientOnionAuthDir: KeyWord() { override fun toString(): String = "ClientOnionAuthDir" }
        object SocksPort: KeyWord() { override fun toString(): String = "SocksPort" }
        object HttpTunnelPort: KeyWord() { override fun toString(): String = "HTTPTunnelPort" }
        object TransPort: KeyWord() { override fun toString(): String = "TransPort" }
        object AutomapHostsOnResolve: KeyWord() { override fun toString(): String = "AutomapHostsOnResolve" }
        object DnsPort: KeyWord() { override fun toString(): String = "DNSPort" }
        object DormantClientTimeout: KeyWord() { override fun toString(): String = "DormantClientTimeout" }
        object DormantTimeoutDisabledByIdleStreams: KeyWord() { override fun toString(): String = "DormantTimeoutDisabledByIdleStreams" }
        object DormantOnFirstStartup: KeyWord() { override fun toString(): String = "DormantOnFirstStartup" }
        object DormantCanceledByStartup: KeyWord() { override fun toString(): String = "DormantCanceledByStartup" }


/* === SERVER OPTIONS ========================= IMPLEMENTED as a TorConfig.Setting ================================== */
        object GeoIpFile: KeyWord() { override fun toString(): String = "GeoIPFile" }
        object GeoIPv6File: KeyWord() { override fun toString(): String = "GeoIPv6File" }


/* === DIRECTORY SERVER OPTIONS =============== IMPLEMENTED as a TorConfig.Setting ================================== */


/* === DENAIL OF SERVICE MITIGATION OPTIONS === IMPLEMENTED as a TorConfig.Setting ================================== */


/* === DIRECTORY AUTHORITY SERVER OPTIONS ===== IMPLEMENTED as a TorConfig.Setting ================================== */


/* === HIDDEN SERVICE OPTIONS ================= IMPLEMENTED as a TorConfig.Setting ================================== */
        object HiddenServiceDir: KeyWord() { override fun toString(): String = "HiddenServiceDir" }
        object HiddenServicePort: KeyWord() { override fun toString(): String = "HiddenServicePort" }
        object HiddenServiceMaxStreams: KeyWord() { override fun toString(): String = "HiddenServiceMaxStreams" }
        object HiddenServiceMaxStreamsCloseCircuit: KeyWord() { override fun toString(): String = "HiddenServiceMaxStreamsCloseCircuit" }


/* === TESTING NETWORK OPTIONS ================ IMPLEMENTED as a TorConfig.Setting ================================== */


/* === NON-PERSISTENT OPTIONS ================= IMPLEMENTED as a TorConfig.Setting ================================== */


/* === NON-PERSISTENT CONTROLLER OPTIONS ====== IMPLEMENTED as a TorConfig.Setting ================================== */
        object __OwningControllerProcess: KeyWord() { override fun toString(): String = "__OwningControllerProcess" }


/* NOT IMPLEMENTED */
/* === GENERAL OPTIONS ======================== NOT yet implemented as a TorConfig.Setting ========================== */
        object BandwidthRate: KeyWord() { override fun toString(): String = "BandwidthRate" }
        object BandwidthBurst: KeyWord() { override fun toString(): String = "BandwidthBurst" }
        object MaxAdvertisedBandwidth: KeyWord() { override fun toString(): String = "MaxAdvertisedBandwidth" }
        object RelayBandwidthRate: KeyWord() { override fun toString(): String = "RelayBandwidthRate" }
        object RelayBandwidthBurst: KeyWord() { override fun toString(): String = "RelayBandwidthBurst" }
        object PerConnBWRate: KeyWord() { override fun toString(): String = "PerConnBWRate" }
        object PerConnBWBurst: KeyWord() { override fun toString(): String = "PerConnBWBurst" }
        object ClientTransportPlugin: KeyWord() { override fun toString(): String = "ClientTransportPlugin" }
        object ServerTransportPlugin: KeyWord() { override fun toString(): String = "ServerTransportPlugin" }
        object ServerTransportListenAddr: KeyWord() { override fun toString(): String = "ServerTransportListenAddr" }
        object ServerTransportOptions: KeyWord() { override fun toString(): String = "ServerTransportOptions" }
        object ExtORPort: KeyWord() { override fun toString(): String = "ExtORPort" }
        object ExtORPortCookieAuthFile: KeyWord() { override fun toString(): String = "ExtORPortCookieAuthFile" }
        object ExtORPortCookieAuthFileGroupReadable: KeyWord() { override fun toString(): String = "ExtORPortCookieAuthFileGroupReadable" }
        object ConnLimit: KeyWord() { override fun toString(): String = "ConnLimit" }
        // DisableNetwork (IMPLEMENTED)
        object ConstrainedSockets: KeyWord() { override fun toString(): String = "ConstrainedSockets" }
        object ConstrainedSockSize: KeyWord() { override fun toString(): String = "ConstrainedSockSize" }
        // ControlPort (IMPLEMENTED)
        object ControlSocket: KeyWord() { override fun toString(): String = "ControlSocket" }
        object ControlSocketsGroupWritable: KeyWord() { override fun toString(): String = "ControlSocketsGroupWritable" }
        object HashedControlPassword: KeyWord() { override fun toString(): String = "HashedControlPassword" }
        // CookieAuthentication (IMPLEMENTED)
        // CookieAuthFile (IMPLEMENTED)
        object CookieAuthFileGroupReadable: KeyWord() { override fun toString(): String = "CookieAuthFileGroupReadable" }
        // ControlPortWriteToFile (IMPLEMENTED)
        object ControlPortFileGroupReadable: KeyWord() { override fun toString(): String = "ControlPortFileGroupReadable" }
        // DataDirectory (IMPLEMENTED)
        object DataDirectoryGroupReadable: KeyWord() { override fun toString(): String = "DataDirectoryGroupReadable" }
        // CacheDirectory (IMPLEMENTED)
        object CacheDirectoryGroupReadable: KeyWord() { override fun toString(): String = "CacheDirectoryGroupReadable" }
        object FallbackDir: KeyWord() { override fun toString(): String = "FallbackDir" }
        object UseDefaultFallbackDirs: KeyWord() { override fun toString(): String = "UseDefaultFallbackDirs" }
        object DirAuthority: KeyWord() { override fun toString(): String = "DirAuthority" }
        object DirAuthorityFallbackRate: KeyWord() { override fun toString(): String = "DirAuthorityFallbackRate" }
        object AlternateBridgeAuthority: KeyWord() { override fun toString(): String = "AlternateBridgeAuthority" }
        object DisableAllSwap: KeyWord() { override fun toString(): String = "DisableAllSwap" }
        object DisableDebuggerAttachment: KeyWord() { override fun toString(): String = "DisableDebuggerAttachment" }
        object FetchDirInfoEarly: KeyWord() { override fun toString(): String = "FetchDirInfoEarly" }
        object FetchDirInfoExtraEarly: KeyWord() { override fun toString(): String = "FetchDirInfoExtraEarly" }
        object FetchHidServDescriptors: KeyWord() { override fun toString(): String = "FetchHidServDescriptors" }
        object FetchServerDescriptors: KeyWord() { override fun toString(): String = "FetchServerDescriptors" }
        object FetchUselessDescriptors: KeyWord() { override fun toString(): String = "FetchUselessDescriptors" }
        // HTTPProxy (DEPRECATED)
        // HTTPProxyAuthenticator (DEPRECATED)
        object HTTPSProxy: KeyWord() { override fun toString(): String = "HTTPSProxy" }
        object HTTPSProxyAuthenticator: KeyWord() { override fun toString(): String = "HTTPSProxyAuthenticator" }
        object Sandbox: KeyWord() { override fun toString(): String = "Sandbox" }
        object Socks4Proxy: KeyWord() { override fun toString(): String = "Socks4Proxy" }
        object Socks5Proxy: KeyWord() { override fun toString(): String = "Socks5Proxy" }
        object Socks5ProxyPassword: KeyWord() { override fun toString(): String = "Socks5ProxyPassword" }
        object UnixSocksGroupWritable: KeyWord() { override fun toString(): String = "UnixSocksGroupWritable" }
        object KeepalivePeriod: KeyWord() { override fun toString(): String = "KeepalivePeriod" }
        object Log: KeyWord() { override fun toString(): String = "Log" }
        object LogMessageDomains: KeyWord() { override fun toString(): String = "LogMessageDomains" }
        object MaxUnparseableDescSizeToLog: KeyWord() { override fun toString(): String = "MaxUnparseableDescSizeToLog" }
        object OutboundBindAddress: KeyWord() { override fun toString(): String = "OutboundBindAddress" }
        object OutboundBindAddressOR: KeyWord() { override fun toString(): String = "OutboundBindAddressOR" }
        object OutboundBindAddressExit: KeyWord() { override fun toString(): String = "OutboundBindAddressExit" }
        object PidFile: KeyWord() { override fun toString(): String = "PidFile" }
        object ProtocolWarnings: KeyWord() { override fun toString(): String = "ProtocolWarnings" }
        // RunAsDaemon (IMPLEMENTED)
        object LogTimeGranularity: KeyWord() { override fun toString(): String = "LogTimeGranularity" }
        object TruncateLogFile: KeyWord() { override fun toString(): String = "TruncateLogFile" }
        // SyslogIdentityTag (IMPLEMENTED)
        object AndroidIdentityTag: KeyWord() { override fun toString(): String = "AndroidIdentityTag" }
        object SafeLogging: KeyWord() { override fun toString(): String = "SafeLogging" }
        object User: KeyWord() { override fun toString(): String = "User" }
        object KeepBindCapabilities: KeyWord() { override fun toString(): String = "KeepBindCapabilities" }
        object HardwareAccel: KeyWord() { override fun toString(): String = "HardwareAccel" }
        object AccelName: KeyWord() { override fun toString(): String = "AccelName" }
        object AccelDir: KeyWord() { override fun toString(): String = "AccelDir" }
        object AvoidDiskWrites: KeyWord() { override fun toString(): String = "AvoidDiskWrites" }
        object CircuitPriorityHalflife: KeyWord() { override fun toString(): String = "CircuitPriorityHalflife" }
        object CountPrivateBandwidth: KeyWord() { override fun toString(): String = "CountPrivateBandwidth" }
        object ExtendByEd25519ID: KeyWord() { override fun toString(): String = "ExtendByEd25519ID" }
        object NoExec: KeyWord() { override fun toString(): String = "NoExec" }
        object Schedulers: KeyWord() { override fun toString(): String = "Schedulers" }
        object KISTSchedRunInterval: KeyWord() { override fun toString(): String = "KISTSchedRunInterval" }
        object KISTSockBufSizeFactor: KeyWord() { override fun toString(): String = "KISTSockBufSizeFactor" }


/* === CLIENT OPTIONS ========================= NOT yet implemented as a TorConfig.Setting ========================== */
        object Bridge: KeyWord() { override fun toString(): String = "Bridge" }
        object LearnCircuitBuildTimeout: KeyWord() { override fun toString(): String = "LearnCircuitBuildTimeout" }
        object CircuitBuildTimeout: KeyWord() { override fun toString(): String = "CircuitBuildTimeout" }
        object CircuitsAvailableTimeout: KeyWord() { override fun toString(): String = "CircuitsAvailableTimeout" }
        object CircuitStreamTimeout: KeyWord() { override fun toString(): String = "CircuitStreamTimeout" }
        object ClientOnly: KeyWord() { override fun toString(): String = "ClientOnly" }
        // ConnectionPadding (IMPLEMENTED)
        // ReducedConnectionPadding (IMPLEMENTED)
        object ExcludeNodes: KeyWord() { override fun toString(): String = "ExcludeNodes" }
        object ExcludeExitNodes: KeyWord() { override fun toString(): String = "ExcludeExitNodes" }
        // GeoIPExcludeUnknown (IMPLEMENTED)
        object ExitNodes: KeyWord() { override fun toString(): String = "ExitNodes" }
        object MiddleNodes: KeyWord() { override fun toString(): String = "MiddleNodes" }
        object EntryNodes: KeyWord() { override fun toString(): String = "EntryNodes" }
        object StrictNodes: KeyWord() { override fun toString(): String = "StrictNodes" }
        object FascistFirewall: KeyWord() { override fun toString(): String = "FascistFirewall" }
        // FirewallPorts (DEPRECATED)
        object ReachableAddresses: KeyWord() { override fun toString(): String = "ReachableAddresses" }
        // ReachableDirAddresses (DEPRECATED)
        object ReachableORAddresses: KeyWord() { override fun toString(): String = "ReachableORAddresses" }
        object HidServAuth: KeyWord() { override fun toString(): String = "HidServAuth" }
        // ClientOnionAuthDir (IMPLEMENTED)
        object LongLivedPorts: KeyWord() { override fun toString(): String = "LongLivedPorts" }
        object MapAddress: KeyWord() { override fun toString(): String = "MapAddress" }
        object NewCircuitPeriod: KeyWord() { override fun toString(): String = "NewCircuitPeriod" }
        object MaxCircuitDirtiness: KeyWord() { override fun toString(): String = "MaxCircuitDirtiness" }
        object MaxClientCircuitsPending: KeyWord() { override fun toString(): String = "MaxClientCircuitsPending" }
        object NodeFamily: KeyWord() { override fun toString(): String = "NodeFamily" }
        object EnforceDistinctSubnets: KeyWord() { override fun toString(): String = "EnforceDistinctSubnets" }
        // SocksPort (IMPLEMENTED)
        object SocksPolicy: KeyWord() { override fun toString(): String = "SocksPolicy" }
        object SocksTimeout: KeyWord() { override fun toString(): String = "SocksTimeout" }
        object TokenBucketRefillInterval: KeyWord() { override fun toString(): String = "TokenBucketRefillInterval" }
        object TrackHostExits: KeyWord() { override fun toString(): String = "TrackHostExits" }
        object TrackHostExitsExpire: KeyWord() { override fun toString(): String = "TrackHostExitsExpire" }
        object UpdateBridgesFromAuthority: KeyWord() { override fun toString(): String = "UpdateBridgesFromAuthority" }
        object UseBridges: KeyWord() { override fun toString(): String = "UseBridges" }
        object UseEntryGuards: KeyWord() { override fun toString(): String = "UseEntryGuards" }
        object GuardfractionFile: KeyWord() { override fun toString(): String = "GuardfractionFile" }
        object UseGuardFraction: KeyWord() { override fun toString(): String = "UseGuardFraction" }
        object NumEntryGuards: KeyWord() { override fun toString(): String = "NumEntryGuards" }
        object NumPrimaryGuards: KeyWord() { override fun toString(): String = "NumPrimaryGuards" }
        object NumDirectoryGuards: KeyWord() { override fun toString(): String = "NumDirectoryGuards" }
        object GuardLifetime: KeyWord() { override fun toString(): String = "GuardLifetime" }
        object SafeSocks: KeyWord() { override fun toString(): String = "SafeSocks" }
        object TestSocks: KeyWord() { override fun toString(): String = "TestSocks" }
        object VirtualAddrNetworkIPv6: KeyWord() { override fun toString(): String = "VirtualAddrNetworkIPv6" }
        object AllowNonRFC953Hostnames: KeyWord() { override fun toString(): String = "AllowNonRFC953Hostnames" }
        // HTTPTunnelPort (IMPLEMENTED)
        // TransPort (IMPLEMENTED)
        object TransProxyType: KeyWord() { override fun toString(): String = "TransProxyType" }
        object NATDPort: KeyWord() { override fun toString(): String = "NATDPort" }
        // AutomapHostsOnResolve (IMPLEMENTED)
        object AutomapHostsSuffixes: KeyWord() { override fun toString(): String = "AutomapHostsSuffixes" }
        // DNSPort (IMPLEMENTED)
        object ClientDNSRejectInternalAddresses: KeyWord() { override fun toString(): String = "ClientDNSRejectInternalAddresses" }
        object ClientRejectInternalAddresses: KeyWord() { override fun toString(): String = "ClientRejectInternalAddresses" }
        object DownloadExtraInfo: KeyWord() { override fun toString(): String = "DownloadExtraInfo" }
        object WarnPlaintextPorts: KeyWord() { override fun toString(): String = "WarnPlaintextPorts" }
        object RejectPlaintextPorts: KeyWord() { override fun toString(): String = "RejectPlaintextPorts" }
        object OptimisticData: KeyWord() { override fun toString(): String = "OptimisticData" }
        object HSLayer2Nodes: KeyWord() { override fun toString(): String = "HSLayer2Nodes" }
        object HSLayer3Nodes: KeyWord() { override fun toString(): String = "HSLayer3Nodes" }
        object UseMicrodescriptors: KeyWord() { override fun toString(): String = "UseMicrodescriptors" }
        object PathBiasScaleThreshold: KeyWord() { override fun toString(): String = "PathBiasScaleThreshold" }
        object PathBiasScaleUseThreshold: KeyWord() { override fun toString(): String = "PathBiasScaleUseThreshold" }
        object ClientUseIPv4: KeyWord() { override fun toString(): String = "ClientUseIPv4" }
        object ClientUseIPv6: KeyWord() { override fun toString(): String = "ClientUseIPv6" }
        object ClientPreferIPv6ORPort: KeyWord() { override fun toString(): String = "ClientPreferIPv6ORPort" }
        object ClientAutoIPv6ORPort: KeyWord() { override fun toString(): String = "ClientAutoIPv6ORPort" }
        object PathsNeededToBuildCircuits: KeyWord() { override fun toString(): String = "PathsNeededToBuildCircuits" }
        object ClientBootstrapConsensusAuthorityDownloadInitialDelay: KeyWord() { override fun toString(): String = "ClientBootstrapConsensusAuthorityDownloadInitialDelay" }
        object ClientBootstrapConsensusFallbackDownloadInitialDelay: KeyWord() { override fun toString(): String = "ClientBootstrapConsensusFallbackDownloadInitialDelay" }
        object ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay: KeyWord() { override fun toString(): String = "ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay" }
        object ClientBootstrapConsensusMaxInProgressTries: KeyWord() { override fun toString(): String = "ClientBootstrapConsensusMaxInProgressTries" }
        // DormantClientTimeout (IMPLEMENTED)
        // DormantTimeoutDisabledByIdleStreams (IMPLEMENTED)
        // DormantOnFirstStartup (IMPLEMENTED)
        // DormantCanceledByStartup (IMPLEMENTED)


/* === SERVER OPTIONS ========================= NOT yet implemented as a TorConfig.Setting ========================== */
        object Address: KeyWord() { override fun toString(): String = "Address" }
        object AssumeReachable: KeyWord() { override fun toString(): String = "AssumeReachable" }
        object BridgeRelay: KeyWord() { override fun toString(): String = "BridgeRelay" }
        object BridgeDistribution: KeyWord() { override fun toString(): String = "BridgeDistribution" }
        object ContactInfo: KeyWord() { override fun toString(): String = "ContactInfo" }
        object ExitRelay: KeyWord() { override fun toString(): String = "ExitRelay" }
        object ExitPolicy: KeyWord() { override fun toString(): String = "ExitPolicy" }
        object ExitPolicyRejectPrivate: KeyWord() { override fun toString(): String = "ExitPolicyRejectPrivate" }
        object ExitPolicyRejectLocalInterfaces: KeyWord() { override fun toString(): String = "ExitPolicyRejectLocalInterfaces" }
        object ReducedExitPolicy: KeyWord() { override fun toString(): String = "ReducedExitPolicy" }
        object IPv6Exit: KeyWord() { override fun toString(): String = "IPv6Exit" }
        object MaxOnionQueueDelay: KeyWord() { override fun toString(): String = "MaxOnionQueueDelay" }
        object MyFamily: KeyWord() { override fun toString(): String = "MyFamily" }
        object Nickname: KeyWord() { override fun toString(): String = "Nickname" }
        object NumCPUs: KeyWord() { override fun toString(): String = "NumCPUs" }
        object ORPort: KeyWord() { override fun toString(): String = "ORPort" }
        object PublishServerDescriptor: KeyWord() { override fun toString(): String = "PublishServerDescriptor" }
        object ShutdownWaitLength: KeyWord() { override fun toString(): String = "ShutdownWaitLength" }
        object SSLKeyLifetime: KeyWord() { override fun toString(): String = "SSLKeyLifetime" }
        object HeartbeatPeriod: KeyWord() { override fun toString(): String = "HeartbeatPeriod" }
        object MainloopStats: KeyWord() { override fun toString(): String = "MainloopStats" }
        object AccountingMax: KeyWord() { override fun toString(): String = "AccountingMax" }
        object AccountingRule: KeyWord() { override fun toString(): String = "AccountingRule" }
        object AccountingStart: KeyWord() { override fun toString(): String = "AccountingStart" }
        object RefuseUnknownExits: KeyWord() { override fun toString(): String = "RefuseUnknownExits" }
        object ServerDNSResolvConfFile: KeyWord() { override fun toString(): String = "ServerDNSResolvConfFile" }
        object ServerDNSAllowBrokenConfig: KeyWord() { override fun toString(): String = "ServerDNSAllowBrokenConfig" }
        object ServerDNSSearchDomains: KeyWord() { override fun toString(): String = "ServerDNSSearchDomains" }
        object ServerDNSDetectHijacking: KeyWord() { override fun toString(): String = "ServerDNSDetectHijacking" }
        object ServerDNSTestAddresses: KeyWord() { override fun toString(): String = "ServerDNSTestAddresses" }
        object ServerDNSAllowNonRFC953Hostnames: KeyWord() { override fun toString(): String = "ServerDNSAllowNonRFC953Hostnames" }
        object BridgeRecordUsageByCountry: KeyWord() { override fun toString(): String = "BridgeRecordUsageByCountry" }
        object ServerDNSRandomizeCase: KeyWord() { override fun toString(): String = "ServerDNSRandomizeCase" }
        // GeoIPFile (IMPLEMENTED)
        // GeoIPv6File (IMPLEMENTED)
        object CellStatistics: KeyWord() { override fun toString(): String = "CellStatistics" }
        object PaddingStatistics: KeyWord() { override fun toString(): String = "PaddingStatistics" }
        object DirReqStatistics: KeyWord() { override fun toString(): String = "DirReqStatistics" }
        object EntryStatistics: KeyWord() { override fun toString(): String = "EntryStatistics" }
        object ExitPortStatistics: KeyWord() { override fun toString(): String = "ExitPortStatistics" }
        object ConnDirectionStatistics: KeyWord() { override fun toString(): String = "ConnDirectionStatistics" }
        object HiddenServiceStatistics: KeyWord() { override fun toString(): String = "HiddenServiceStatistics" }
        object ExtraInfoStatistics: KeyWord() { override fun toString(): String = "ExtraInfoStatistics" }
        object ExtendAllowPrivateAddresses: KeyWord() { override fun toString(): String = "ExtendAllowPrivateAddresses" }
        object MaxMemInQueues: KeyWord() { override fun toString(): String = "MaxMemInQueues" }
        object DisableOOSCheck: KeyWord() { override fun toString(): String = "DisableOOSCheck" }
        object SigningKeyLifetime: KeyWord() { override fun toString(): String = "SigningKeyLifetime" }
        object OfflineMasterKey: KeyWord() { override fun toString(): String = "OfflineMasterKey" }
        object KeyDirectory: KeyWord() { override fun toString(): String = "KeyDirectory" }
        object KeyDirectoryGroupReadable: KeyWord() { override fun toString(): String = "KeyDirectoryGroupReadable" }
        object RephistTrackTime: KeyWord() { override fun toString(): String = "RephistTrackTime" }


/* === DIRECTORY SERVER OPTIONS =============== NOT yet implemented as a TorConfig.Setting ========================== */
        object DirPortFrontPage: KeyWord() { override fun toString(): String = "DirPortFrontPage" }
        object DirPort: KeyWord() { override fun toString(): String = "DirPort" }
        object DirPolicy: KeyWord() { override fun toString(): String = "DirPolicy" }
        object DirCache: KeyWord() { override fun toString(): String = "DirCache" }
        object MaxConsensusAgeForDiffs: KeyWord() { override fun toString(): String = "MaxConsensusAgeForDiffs" }


/* === DENAIL OF SERVICE MITIGATION OPTIONS === NOT yet implemented as a TorConfig.Setting ========================== */
        object DoSCircuitCreationEnabled: KeyWord() { override fun toString(): String = "DoSCircuitCreationEnabled" }
        object DoSCircuitCreationMinConnections: KeyWord() { override fun toString(): String = "DoSCircuitCreationMinConnections" }
        object DoSCircuitCreationRate: KeyWord() { override fun toString(): String = "DoSCircuitCreationRate" }
        object DoSCircuitCreationBurst: KeyWord() { override fun toString(): String = "DoSCircuitCreationBurst" }
        object DoSCircuitCreationDefenseType: KeyWord() { override fun toString(): String = "DoSCircuitCreationDefenseType" }
        object DoSCircuitCreationDefenseTimePeriod: KeyWord() { override fun toString(): String = "DoSCircuitCreationDefenseTimePeriod" }
        object DoSConnectionEnabled: KeyWord() { override fun toString(): String = "DoSConnectionEnabled" }
        object DoSConnectionMaxConcurrentCount: KeyWord() { override fun toString(): String = "DoSConnectionMaxConcurrentCount" }
        object DoSConnectionDefenseType: KeyWord() { override fun toString(): String = "DoSConnectionDefenseType" }
        object DoSRefuseSingleHopClientRendezvous: KeyWord() { override fun toString(): String = "DoSRefuseSingleHopClientRendezvous" }


/* === DIRECTORY AUTHORITY SERVER OPTIONS ===== NOT yet implemented as a TorConfig.Setting ========================== */
        object AuthoritativeDirectory: KeyWord() { override fun toString(): String = "AuthoritativeDirectory" }
        object V3AuthoritativeDirectory: KeyWord() { override fun toString(): String = "V3AuthoritativeDirectory" }
        object VersioningAuthoritativeDirectory: KeyWord() { override fun toString(): String = "VersioningAuthoritativeDirectory" }
        object RecommendedVersions: KeyWord() { override fun toString(): String = "RecommendedVersions" }
        object RecommendedPackages: KeyWord() { override fun toString(): String = "RecommendedPackages" }
        object RecommendedClientVersions: KeyWord() { override fun toString(): String = "RecommendedClientVersions" }
        object BridgeAuthoritativeDir: KeyWord() { override fun toString(): String = "BridgeAuthoritativeDir" }
        object MinUptimeHidServDirectoryV2: KeyWord() { override fun toString(): String = "MinUptimeHidServDirectoryV2" }
        object RecommendedServerVersions: KeyWord() { override fun toString(): String = "RecommendedServerVersions" }
        object ConsensusParams: KeyWord() { override fun toString(): String = "ConsensusParams" }
        object DirAllowPrivateAddresses: KeyWord() { override fun toString(): String = "DirAllowPrivateAddresses" }
        object AuthDirBadExit: KeyWord() { override fun toString(): String = "AuthDirBadExit" }
        object AuthDirInvalid: KeyWord() { override fun toString(): String = "AuthDirInvalid" }
        object AuthDirReject: KeyWord() { override fun toString(): String = "AuthDirReject" }
        object AuthDirRejectCCs: KeyWord() { override fun toString(): String = "AuthDirRejectCCs" }
        object AuthDirListBadExits: KeyWord() { override fun toString(): String = "AuthDirListBadExits" }
        object AuthDirMaxServersPerAddr: KeyWord() { override fun toString(): String = "AuthDirMaxServersPerAddr" }
        object AuthDirFastGuarantee: KeyWord() { override fun toString(): String = "AuthDirFastGuarantee" }
        object AuthDirGuardBWGuarantee: KeyWord() { override fun toString(): String = "AuthDirGuardBWGuarantee" }
        object AuthDirPinKeys: KeyWord() { override fun toString(): String = "AuthDirPinKeys" }
        object AuthDirSharedRandomness: KeyWord() { override fun toString(): String = "AuthDirSharedRandomness" }
        object AuthDirTestEd25519LinkKeys: KeyWord() { override fun toString(): String = "AuthDirTestEd25519LinkKeys" }
        object BridgePassword: KeyWord() { override fun toString(): String = "BridgePassword" }
        object V3AuthVotingInterval: KeyWord() { override fun toString(): String = "V3AuthVotingInterval" }
        object V3AuthVoteDelay: KeyWord() { override fun toString(): String = "V3AuthVoteDelay" }
        object V3AuthDistDelay: KeyWord() { override fun toString(): String = "V3AuthDistDelay" }
        object V3AuthNIntervalsValid: KeyWord() { override fun toString(): String = "V3AuthNIntervalsValid" }
        object V3BandwidthsFile: KeyWord() { override fun toString(): String = "V3BandwidthsFile" }
        object V3AuthUseLegacyKey: KeyWord() { override fun toString(): String = "V3AuthUseLegacyKey" }
        object AuthDirHasIPv6Connectivity: KeyWord() { override fun toString(): String = "AuthDirHasIPv6Connectivity" }
        object MinMeasuredBWsForAuthToIgnoreAdvertised: KeyWord() { override fun toString(): String = "MinMeasuredBWsForAuthToIgnoreAdvertised" }


/* === HIDDEN SERVICE OPTIONS ================= NOT yet implemented as a TorConfig.Setting ========================== */
        // HiddenServiceDir (IMPLEMENTED)
        // HiddenServicePort (IMPLEMENTED)
        object PublishHidServDescriptors: KeyWord() { override fun toString(): String = "PublishHidServDescriptors" }
        object HiddenServiceVersion: KeyWord() { override fun toString(): String = "HiddenServiceVersion" }
        object HiddenServiceAuthorizeClient: KeyWord() { override fun toString(): String = "HiddenServiceAuthorizeClient" }
        object HiddenServiceAllowUnknownPorts: KeyWord() { override fun toString(): String = "HiddenServiceAllowUnknownPorts" }
        object HiddenServiceExportCircuitID: KeyWord() { override fun toString(): String = "HiddenServiceExportCircuitID" }
        // HiddenServiceMaxStreams (IMPLEMENTED)
        // HiddenServiceMaxStreamsCloseCircuit (IMPLEMENTED)
        object RendPostPeriod: KeyWord() { override fun toString(): String = "RendPostPeriod" }
        object HiddenServiceDirGroupReadable: KeyWord() { override fun toString(): String = "HiddenServiceDirGroupReadable" }
        object HiddenServiceNumIntroductionPoints: KeyWord() { override fun toString(): String = "HiddenServiceNumIntroductionPoints" }
        object HiddenServiceSingleHopMode: KeyWord() { override fun toString(): String = "HiddenServiceSingleHopMode" }
        object HiddenServiceNonAnonymousMode: KeyWord() { override fun toString(): String = "HiddenServiceNonAnonymousMode" }


/* === TESTING NETWORK OPTIONS ================ NOT yet implemented as a TorConfig.Setting ========================== */
        object TestingTorNetwork: KeyWord() { override fun toString(): String = "TestingTorNetwork" }
        object TestingV3AuthInitialVotingInterval: KeyWord() { override fun toString(): String = "TestingV3AuthInitialVotingInterval" }
        object TestingV3AuthInitialVoteDelay: KeyWord() { override fun toString(): String = "TestingV3AuthInitialVoteDelay" }
        object TestingV3AuthInitialDistDelay: KeyWord() { override fun toString(): String = "TestingV3AuthInitialDistDelay" }
        object TestingV3AuthVotingStartOffset: KeyWord() { override fun toString(): String = "TestingV3AuthVotingStartOffset" }
        object TestingAuthDirTimeToLearnReachability: KeyWord() { override fun toString(): String = "TestingAuthDirTimeToLearnReachability" }
        object TestingMinFastFlagThreshold: KeyWord() { override fun toString(): String = "TestingMinFastFlagThreshold" }
        object TestingServerDownloadInitialDelay: KeyWord() { override fun toString(): String = "TestingServerDownloadInitialDelay" }
        object TestingClientDownloadInitialDelay: KeyWord() { override fun toString(): String = "TestingClientDownloadInitialDelay" }
        object TestingServerConsensusDownloadInitialDelay: KeyWord() { override fun toString(): String = "TestingServerConsensusDownloadInitialDelay" }
        object TestingClientConsensusDownloadInitialDelay: KeyWord() { override fun toString(): String = "TestingClientConsensusDownloadInitialDelay" }
        object TestingBridgeDownloadInitialDelay: KeyWord() { override fun toString(): String = "TestingBridgeDownloadInitialDelay" }
        object TestingBridgeBootstrapDownloadInitialDelay: KeyWord() { override fun toString(): String = "TestingBridgeBootstrapDownloadInitialDelay" }
        object TestingClientMaxIntervalWithoutRequest: KeyWord() { override fun toString(): String = "TestingClientMaxIntervalWithoutRequest" }
        object TestingDirConnectionMaxStall: KeyWord() { override fun toString(): String = "TestingDirConnectionMaxStall" }
        object TestingDirAuthVoteExit: KeyWord() { override fun toString(): String = "TestingDirAuthVoteExit" }
        object TestingDirAuthVoteExitIsStrict: KeyWord() { override fun toString(): String = "TestingDirAuthVoteExitIsStrict" }
        object TestingDirAuthVoteGuard: KeyWord() { override fun toString(): String = "TestingDirAuthVoteGuard" }
        object TestingDirAuthVoteGuardIsStrict: KeyWord() { override fun toString(): String = "TestingDirAuthVoteGuardIsStrict" }
        object TestingDirAuthVoteHSDir: KeyWord() { override fun toString(): String = "TestingDirAuthVoteHSDir" }
        object TestingDirAuthVoteHSDirIsStrict: KeyWord() { override fun toString(): String = "TestingDirAuthVoteHSDirIsStrict" }
        object TestingEnableConnBwEvent: KeyWord() { override fun toString(): String = "TestingEnableConnBwEvent" }
        object TestingEnableCellStatsEvent: KeyWord() { override fun toString(): String = "TestingEnableCellStatsEvent" }
        object TestingMinExitFlagThreshold: KeyWord() { override fun toString(): String = "TestingMinExitFlagThreshold" }
        object TestingLinkCertLifetime: KeyWord() { override fun toString(): String = "TestingLinkCertLifetime" }
        object TestingAuthKeyLifetime: KeyWord() { override fun toString(): String = "TestingAuthKeyLifetime" }
        object TestingSigningKeySlop: KeyWord() { override fun toString(): String = "TestingSigningKeySlop" }


/* === NON-PERSISTENT OPTIONS ================= NOT yet implemented as a TorConfig.Setting ========================== */
        object __ControlPort: KeyWord() { override fun toString(): String = "__ControlPort" }
        object __DirPort: KeyWord() { override fun toString(): String = "__DirPort" }
        object __DNSPort: KeyWord() { override fun toString(): String = "__DNSPort" }
        object __ExtORPort: KeyWord() { override fun toString(): String = "__ExtORPort" }
        object __NATDPort: KeyWord() { override fun toString(): String = "__NATDPort" }
        object __ORPort: KeyWord() { override fun toString(): String = "__ORPort" }
        object __SocksPort: KeyWord() { override fun toString(): String = "__SocksPort" }
        object __TransPort: KeyWord() { override fun toString(): String = "__TransPort" }


/* === NON-PERSISTENT CONTROLLER OPTIONS ====== NOT yet implemented as a TorConfig.Setting ========================== */
        // See: https://torproject.gitlab.io/torspec/control-spec/#tor-config-options-for-use-by-controllers
        object __AllDirActionsPrivate: KeyWord() { override fun toString(): String = "__AllDirActionsPrivate" }
        object __DisablePredictedCircuits: KeyWord() { override fun toString(): String = "__DisablePredictedCircuits" }
        object __LeaveStreamsUnattached: KeyWord() { override fun toString(): String = "__LeaveStreamsUnattached" }
        object __HashedControlSessionPassword: KeyWord() { override fun toString(): String = "__HashedControlSessionPassword" }
        object __ReloadTorrcOnSIGHUP: KeyWord() { override fun toString(): String = "__ReloadTorrcOnSIGHUP" }
        object __OwningControllerFD: KeyWord() { override fun toString(): String = "__OwningControllerFD" }
        object __DisableSignalHandlers: KeyWord() { override fun toString(): String = "__DisableSignalHandlers" }


        // DEPRECATED
        @Deprecated(message = "DEPRECATED: As of 0.3.1.0-alpha you should use HTTPSProxy.")
        object HTTPProxy: KeyWord() { override fun toString(): String = "HTTPProxy" }
        @Deprecated(message = "DEPRECATED: As of 0.3.1.0-alpha you should use HTTPSProxyAuthenticator.")
        object HTTPProxyAuthenticator: KeyWord() { override fun toString(): String = "HTTPProxyAuthenticator" }
        @Deprecated(message = "This option is deprecated; use ReachableAddresses instead.")
        object FirewallPorts: KeyWord() { override fun toString(): String = "FirewallPorts" }
        @Deprecated(message = "DEPRECATED: This option has had no effect for some time.")
        object ReachableDirAddresses: KeyWord() { override fun toString(): String = "ReachableDirAddresses" }
        @Deprecated(message = "DEPRECATED: This option has had no effect for some time.")
        object ClientPreferIPv6DirPort: KeyWord() { override fun toString(): String = "ClientPreferIPv6DirPort" }

        @Deprecated(message = "Use KeyWord.GeoIPFile", replaceWith = ReplaceWith(expression = "TorConfig.KeyWord.GeoIPFile"))
        object GeoIpV4File: KeyWord() { override fun toString(): String = "GeoIPFile" }
        @Deprecated(message = "Use KeyWord.__OwningControllerProcess", replaceWith = ReplaceWith(expression = "TorConfig.KeyWord.__OwningControllerProcess"))
        object OwningControllerProcess: KeyWord() { override fun toString(): String = "__OwningControllerProcess" }

        final override val length: Int get() = toString().length
        final override fun get(index: Int): Char = toString()[index]
        final override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return toString().subSequence(startIndex, endIndex)
        }

        final override fun compareTo(other: String): Int = toString().compareTo(other)
        operator fun plus(other: Any?): String = toString() + other

        final override fun equals(other: Any?): Boolean = other is KeyWord && other.toString() == toString()
        final override fun hashCode(): Int = 21 * 31 + toString().hashCode()
    }
}
