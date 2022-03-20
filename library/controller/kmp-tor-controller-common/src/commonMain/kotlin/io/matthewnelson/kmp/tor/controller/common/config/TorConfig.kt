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
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import kotlin.jvm.JvmStatic
import io.matthewnelson.kmp.tor.common.util.TorStrings.REDACTED
import io.matthewnelson.kmp.tor.common.util.TorStrings.SP
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.TorF.False
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.TorF.True
import io.matthewnelson.kmp.tor.controller.common.file.Path
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmSynthetic
import kotlin.reflect.KClass

/**
 * Holder for Tor configuration information.
 *
 * @see [Builder]
 * @see [Setting]
 * @see [Option]
 * */
@Suppress("RemoveRedundantQualifierName", "SpellCheckingInspection")
class TorConfig private constructor(
    val settings: Set<TorConfig.Setting<*>>,
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

    @OptIn(InternalTorApi::class)
    override fun toString(): String {
        return "TorConfig(settings=$REDACTED,text=$REDACTED)"
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

        @OptIn(InternalTorApi::class)
        fun build(): TorConfig {
            val sb = StringBuilder()

            val disabledPorts = mutableSetOf<String>()
            val sorted = settings.sortedBy { setting ->
                if (setting is Setting.Ports) {
                    if (setting.value is Option.AorDorPort.Disable) {
                        disabledPorts.add(setting.keyword)
                    }
                    "AAA${setting.keyword}"
                } else {
                    setting.keyword
                }
            }

            val writtenDisabledPorts: MutableSet<String> = LinkedHashSet(disabledPorts.size)

            val newSettings = mutableSetOf<Setting<*>>()
            for ((i, setting) in sorted.withIndex()) {
                val value = setting.value ?: continue

                if (setting is Setting.Ports) {
                    if (disabledPorts.contains(setting.keyword)) {
                        if (!writtenDisabledPorts.contains(setting.keyword)) {
                            sb.append(setting.keyword)
                            sb.append(SP)
                            sb.append(Option.AorDorPort.Disable.value)
                            sb.append('\n')
                            writtenDisabledPorts.add(setting.keyword)
                        }

                        if (setting.value is Option.AorDorPort.Disable) {
                            newSettings.add(setting.setImmutable())
                        }

                        continue
                    } else {
                        sb.append(setting.keyword)
                        sb.append(SP)
                        sb.append(value)
                    }

                    when (setting) {
                        is Setting.Ports.Control -> {
                            setting.flags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.Dns -> {
                            setting.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.HttpTunnel -> {
                            setting.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.Socks -> {
                            setting.flags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                            setting.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.Trans -> {
                            setting.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                    }
                } else if (setting is Setting.HiddenService) {
                    val hsDir = setting.value ?: continue
                    val hsPorts = setting.ports

                    if (hsPorts == null || hsPorts.isEmpty()) {
                        continue
                    }

                    if (sorted.elementAtOrNull(i - 1) !is Setting.HiddenService) {
                        sb.appendLine()
                    }

                    sb.append(setting.keyword)
                    sb.append(SP)
                    sb.append(hsDir.value)

                    for (hsPort in hsPorts) {
                        sb.appendLine()
                        sb.append("HiddenServicePort")
                        sb.append(SP)
                        sb.append(hsPort.virtualPort.value)
                        sb.append(SP)
                        sb.append("127.0.0.1")
                        sb.append(':')
                        sb.append(hsPort.targetPort.value)
                    }

                    setting.maxStreams?.let { maxStreams ->
                        sb.appendLine()
                        sb.append("HiddenServiceMaxStreams")
                        sb.append(SP)
                        sb.append(maxStreams.value)
                    }

                    setting.maxStreamsCloseCircuit?.let { closeCircuit ->
                        sb.appendLine()
                        sb.append("HiddenServiceMaxStreamsCloseCircuit")
                        sb.append(SP)
                        sb.append(closeCircuit.value)
                    }

                    sb.appendLine()
                } else {
                    sb.append(setting.keyword)
                    sb.append(SP)
                    sb.append(value)
                }

                newSettings.add(setting.setImmutable())
                sb.appendLine()
            }

            return TorConfig(newSettings.toSet(), sb.toString())
        }

        companion object {
            @JvmStatic
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
    sealed class Setting<T: Option?>(val keyword: String) {

        abstract val default: T
        abstract var value: T
            protected set
        val isDefault: Boolean get() = value == default
        var isMutable: Boolean = true
            protected set

        @JvmSynthetic
        internal fun setImmutable(): Setting<T> {
            isMutable = false
            return this
        }

        fun set(value: T): Setting<T> {
            if (isMutable) {
                this.value = value
            }
            return this
        }
        open fun setDefault(): Setting<T> {
            if (isMutable) {
                this.value = default
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
            val simpleName = this::class.simpleName?.let { ".$it" } ?: ""
            return "TorConfig.Setting$simpleName(keyword=$keyword,value=$value,default=$default)"
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#AutomapHostsOnResolve
         * */
        class AutomapHostsOnResolve         : Setting<Option.TorF>("AutomapHostsOnResolve") {
            override val default: Option.TorF get() = Option.TorF.True
            override var value: Option.TorF = default

            override fun clone(): AutomapHostsOnResolve {
                return AutomapHostsOnResolve().set(value) as AutomapHostsOnResolve
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#CacheDirectory
         * */
        class CacheDirectory                : Setting<Option.FileSystemDir?>("CacheDirectory") {
            override val default: Option.FileSystemDir? = null
            override var value: Option.FileSystemDir? = default
                set(value) { field = value?.nullIfEmpty }

            override fun clone(): CacheDirectory {
                return CacheDirectory().set(value) as CacheDirectory
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ClientOnionAuthDir
         * */
        class ClientOnionAuthDir            : Setting<Option.FileSystemDir?>("ClientOnionAuthDir") {
            override val default: Option.FileSystemDir? = null
            override var value: Option.FileSystemDir? = default
                set(value) { field = value?.nullIfEmpty }

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
        class ConnectionPadding             : Setting<Option.AorTorF>("ConnectionPadding") {
            override val default: Option.AorTorF get() = Option.AorTorF.Auto
            override var value: Option.AorTorF = default

            override fun clone(): ConnectionPadding {
                return ConnectionPadding().set(value) as ConnectionPadding
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ConnectionPaddingReduced
         * */
        class ConnectionPaddingReduced      : Setting<Option.TorF>("ReducedConnectionPadding") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default

            override fun clone(): ConnectionPaddingReduced {
                return ConnectionPaddingReduced().set(value) as ConnectionPaddingReduced
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPortWriteToFile
         * */
        class ControlPortWriteToFile        : Setting<Option.FileSystemFile?>("ControlPortWriteToFile") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }

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
        class CookieAuthentication          : Setting<Option.TorF>("CookieAuthentication") {
            override val default: Option.TorF get() = Option.TorF.True
            override var value: Option.TorF = default

            override fun clone(): CookieAuthentication {
                return CookieAuthentication().set(value) as CookieAuthentication
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#CookieAuthFile
         * */
        class CookieAuthFile                : Setting<Option.FileSystemFile?>("CookieAuthFile") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }

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
        class DataDirectory                 : Setting<Option.FileSystemDir?>("DataDirectory") {
            override val default: Option.FileSystemDir? = null
            override var value: Option.FileSystemDir? = default
                set(value) { field = value?.nullIfEmpty }

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
        class DisableNetwork                : Setting<Option.TorF>("DisableNetwork") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default

            override fun clone(): DisableNetwork {
                return DisableNetwork().set(value) as DisableNetwork
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantCanceledByStartup
         * */
        class DormantCanceledByStartup      : Setting<Option.TorF>("DormantCanceledByStartup") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default

            override fun clone(): DormantCanceledByStartup {
                return DormantCanceledByStartup().set(value) as DormantCanceledByStartup
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantClientTimeout
         * */
        class DormantClientTimeout          : Setting<Option.Time>("DormantClientTimeout") {
            override val default: Option.Time = Option.Time.Hours(24)
            override var value: Option.Time = default
                set(value) {
                    // value must be greater than or equal to 10 minutes
                    field = if (value is Option.Time.Minutes && value.time < 10) {
                        Option.Time.Minutes(10)
                    } else {
                        value
                    }
                }

            override fun clone(): DormantClientTimeout {
                return DormantClientTimeout().set(value) as DormantClientTimeout
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantOnFirstStartup
         * */
        class DormantOnFirstStartup         : Setting<Option.TorF>("DormantOnFirstStartup") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default

            override fun clone(): DormantOnFirstStartup {
                return DormantOnFirstStartup().set(value) as DormantOnFirstStartup
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantTimeoutDisabledByIdleStreams
         * */
        class DormantTimeoutDisabledByIdleStreams   : Setting<Option.TorF>("DormantTimeoutDisabledByIdleStreams") {
            override val default: Option.TorF get() = Option.TorF.True
            override var value: Option.TorF = default

            override fun clone(): DormantTimeoutDisabledByIdleStreams {
                return DormantTimeoutDisabledByIdleStreams().set(value) as DormantTimeoutDisabledByIdleStreams
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPExcludeUnknown
         * */
        class GeoIPExcludeUnknown           : Setting<Option.AorTorF>("GeoIPExcludeUnknown") {
            override val default: Option.AorTorF get() = Option.AorTorF.Auto
            override var value: Option.AorTorF = default

            override fun clone(): GeoIPExcludeUnknown {
                return GeoIPExcludeUnknown().set(value) as GeoIPExcludeUnknown
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPFile
         * */
        class GeoIpV4File                   : Setting<Option.FileSystemFile?>("GeoIPFile") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }

            override fun clone(): GeoIpV4File {
                return GeoIpV4File().set(value) as GeoIpV4File
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPv6File
         * */
        class GeoIpV6File                   : Setting<Option.FileSystemFile?>("GeoIPv6File") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }

            override fun clone(): GeoIpV6File {
                return GeoIpV6File().set(value) as GeoIpV6File
            }
        }

        /**
         * val myHiddenService = Setting.HiddenService()
         *     .setPorts(ports = setOf(
         *         Setting.HiddenService.Ports(virtualPort = Port(22))
         *         Setting.HiddenService.Ports(virtualPort = Port(8022), targetPort = Port(22))
         *     ))
         *     .setMaxStreams(maxStreams = Setting.HiddenService.MaxStreams(2))
         *     .setMaxStreamsCloseCircuit(value = TorF.False)
         *     .set(FileSystemDir(
         *         workDir.builder {
         *             addSegment(HiddenService.DEFAULT_PARENT_DIR_NAME)
         *             addSegment("my_hidden_service")
         *         }
         *     ))
         *
         * Note that both `set` and `setPorts` _must_ be set for it to be added
         * to your config.
         *
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceDir
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServicePort
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreams
         * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreamsCloseCircuit
         * */
        class HiddenService                 : Setting<Option.FileSystemDir?>("HiddenServiceDir") {
            override val default: Option.FileSystemDir? = null
            override var value: Option.FileSystemDir? = default
                set(value) { field = value?.nullIfEmpty }

            /**
             * See [HiddenService.Ports]
             * */
            var ports: Set<HiddenService.Ports>? = null
                private set

            /**
             * A value of `null` means it will not be written to the config and falls
             * back to Tor's default value of 0 (unlimited).
             *
             * @see [MaxStreams]
             * */
            var maxStreams: MaxStreams? = null
                private set

            /**
             * A value of `null` means it will not be written to the config and falls
             * back to Tor's default setting of [Option.TorF.False]
             * */
            var maxStreamsCloseCircuit: Option.TorF? = null
                private set

            fun setPorts(ports: Set<Ports>?): HiddenService {
                if (isMutable) {
                    this.ports = ports
                }
                return this
            }

            fun setMaxStreams(maxStreams: MaxStreams?): HiddenService {
                if (isMutable) {
                    this.maxStreams = maxStreams
                }
                return this
            }

            fun setMaxStreamsCloseCircuit(value: Option.TorF?): HiddenService {
                if (isMutable) {
                    maxStreamsCloseCircuit = value
                }
                return this
            }

            override fun setDefault(): HiddenService {
                if (isMutable) {
                    value = default
                    ports = null
                    maxStreams = null
                    maxStreamsCloseCircuit = null
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
                return  other is HiddenService && other.value == value
            }

            override fun hashCode(): Int {
                return 14 * 31 + value.hashCode()
            }

            /**
             * By default, [virtualPort] is always mapped to 127.0.0.1:[targetPort]. This
             * can be overridden by expressing a different value for [targetPort].
             *
             * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServicePort
             * */
            data class Ports(val virtualPort: Port, val targetPort: Port = virtualPort)

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreams
             *
             * @throws [IllegalArgumentException] if [value] is not within the inclusive range
             *   of 0 and 65535
             * */
            @JvmInline
            value class MaxStreams(val value: Int) {
                init {
                    require(value in Port.MIN..Port.MAX) {
                        "MaxStreams.value must be between ${Port.MIN} and ${Port.MAX}"
                    }
                }
            }

            companion object {
                const val DEFAULT_PARENT_DIR_NAME = "hidden_services"
            }
        }

        /**
         * https://torproject.gitlab.io/torspec/control-spec/#takeownership
         * */
        class OwningControllerProcess       : Setting<Option.ProcessId?>("__OwningControllerProcess") {
            override val default: Option.ProcessId? = null
            override var value: Option.ProcessId? = default

            override fun clone(): OwningControllerProcess {
                return OwningControllerProcess().set(value) as OwningControllerProcess
            }
        }

        sealed class Ports(keyword: String) : Setting<Option.AorDorPort>(keyword) {

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
             * https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPort
             *
             * Note that Tor's default value as per the spec is disabled (0), so
             * excluding it from your config will not set it to a Port. As this
             * library depends on the [Control] port, the default value here differs
             * and cannot be set to [Option.AorDorPort.Disable].
             * */
            class Control                       : Ports("ControlPort") {
                override val default: Option.AorDorPort get() = Option.AorDorPort.Auto
                override var value: Option.AorDorPort = default
                    set(value) {
                        if (value !is Option.AorDorPort.Disable) {
                            field = value
                        }
                    }

                var flags: Set<Flag>? = null
                    private set

                override fun setDefault(): Control {
                    if (isMutable) {
                        value = default
                        flags = null
                    }
                    return this
                }

                fun setFlags(flags: Set<Flag>?): Control {
                    if (isMutable) {
                        this.flags = flags?.toSet()
                    }
                    return this
                }

                override fun clone(): Control {
                    return Control().setFlags(flags).set(value) as Control
                }

                sealed class Flag(val value: String) {
                    // TODO: Implement Unix domains in addition to Port capabilities
//                    object GroupWritable                    : Flag("GroupWritable")
//                    object WorldWritable                    : Flag("WorldWritable")
//                    object RelaxDirModeCheck                : Flag("RelaxDirModeCheck")
                }
            }

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#DNSPort
             * */
            class Dns                           : Ports("DNSPort") {
                override val default: Option.AorDorPort get() = Option.AorDorPort.Disable
                override var value: Option.AorDorPort = default
                var isolationFlags: Set<IsolationFlag>? = null
                    private set

                override fun setDefault(): Dns {
                    if (isMutable) {
                        value = default
                        isolationFlags = null
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Dns {
                    if (isMutable) {
                        isolationFlags = flags?.toSet()
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
            class HttpTunnel                    : Ports("HTTPTunnelPort") {
                override val default: Option.AorDorPort get() = Option.AorDorPort.Disable
                override var value: Option.AorDorPort = default
                var isolationFlags: Set<IsolationFlag>? = null
                    private set

                override fun setDefault(): HttpTunnel {
                    if (isMutable) {
                        value = default
                        isolationFlags = null
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): HttpTunnel {
                    if (isMutable) {
                        isolationFlags = flags?.toSet()
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
            class Socks                         : Ports("SocksPort") {
                override val default: Option.AorDorPort
                    get() = Option.AorDorPort.Value(PortProxy(9050))
                override var value: Option.AorDorPort = default
                var flags: Set<Flag>? = null
                    private set
                var isolationFlags: Set<IsolationFlag>? = null
                    private set

                override fun setDefault(): Socks {
                    if (isMutable) {
                        value = default
                        flags = null
                        isolationFlags = null
                    }
                    return this
                }

                fun setFlags(flags: Set<Flag>?): Socks {
                    if (isMutable) {
                        this.flags = flags?.toSet()
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Socks {
                    if (isMutable) {
                        isolationFlags = flags?.toSet()
                    }
                    return this
                }

                override fun clone(): Socks {
                    return Socks().setFlags(flags).setIsolationFlags(isolationFlags).set(value) as Socks
                }

                sealed class Flag(val value: String) {
                    object NoIPv4Traffic                    : Flag("NoIPv4Traffic")
                    object IPv6Traffic                      : Flag("IPv6Traffic")
                    object PreferIPv6                       : Flag("PreferIPv6")
                    object NoDNSRequest                     : Flag("NoDNSRequest")
                    object NoOnionTraffic                   : Flag("NoOnionTraffic")
                    object OnionTrafficOnly                 : Flag("OnionTrafficOnly")
                    object CacheIPv4DNS                     : Flag("CacheIPv4DNS")
                    object CacheIPv6DNS                     : Flag("CacheIPv6DNS")
//                    object GroupWritable                    : Flag("GroupWritable")
//                    object WorldWritable                    : Flag("WorldWritable")
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
            class Trans                         : Ports("TransPort") {
                override val default: Option.AorDorPort get() = Option.AorDorPort.Disable
                override var value: Option.AorDorPort = default
                var isolationFlags: Set<IsolationFlag>? = null
                    private set

                override fun setDefault(): Trans {
                    if (isMutable) {
                        value = default
                        isolationFlags = null
                    }
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Trans {
                    if (isMutable) {
                        isolationFlags = flags?.toSet()
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
            sealed class IsolationFlag(val value: String) {

                override fun toString(): String {
                    return value
                }

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
                        return  other != null           &&
                                other is SessionGroup
                    }

                    override fun hashCode(): Int {
                        return 17 * 31 + "SessionGroup".hashCode()
                    }
                }
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#RunAsDaemon
         * */
        class RunAsDaemon                   : Setting<Option.TorF>("RunAsDaemon") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default

            override fun clone(): RunAsDaemon {
                return RunAsDaemon().set(value) as RunAsDaemon
            }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#SyslogIdentityTag
         * */
        class SyslogIdentityTag             : Setting<Option.FieldId?>("SyslogIdentityTag") {
            override val default: Option.FieldId? get() = null
            override var value: Option.FieldId? = default
                set(value) { field = value?.nullIfEmpty }

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

            @JvmInline
            value class Value(val port: PortProxy)                          : AorDorPort {
                override val value: String get() = port.value.toString()
                override fun toString(): String = value
            }
        }

        @JvmInline
        value class FileSystemFile(val path: Path)                      : Option {
            override val value: String get() = path.value
            val nullIfEmpty: FileSystemFile? get() = if (value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @JvmInline
        value class FileSystemDir(val path: Path)                       : Option {
            override val value: String get() = path.value
            val nullIfEmpty: FileSystemDir? get() = if (value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @JvmInline
        value class FieldId(override val value: String)                 : Option {
            val nullIfEmpty: FieldId? get() = if(value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @JvmInline
        value class ProcessId(val pid: Int)                             : Option {
            override val value: String get() = "$pid"
            override fun toString(): String = value
        }

        sealed interface Time                                           : Option {
            val time: Int

            @JvmInline
            value class Minutes(override val time: Int)                     : Time {
                override val value: String get() = if (time < 1) {
                    "1 minutes"
                } else {
                    "$time minutes"
                }

                override fun toString(): String = value
            }

            @JvmInline
            value class Hours(override val time: Int)                       : Time {
                override val value: String get() = if (time < 1) {
                    "1 hours"
                } else {
                    "$time hours"
                }

                override fun toString(): String = value
            }

            @JvmInline
            value class Days(override val time: Int)                        : Time {
                override val value: String get() = if (time < 1) {
                    "1 days"
                } else {
                    "$time days"
                }

                override fun toString(): String = value
            }

            @JvmInline
            value class Weeks(override val time: Int)                       : Time {
                override val value: String get() = if (time < 1) {
                    "1 weeks"
                } else {
                    "$time weeks"
                }

                override fun toString(): String = value
            }
        }
    }
}
