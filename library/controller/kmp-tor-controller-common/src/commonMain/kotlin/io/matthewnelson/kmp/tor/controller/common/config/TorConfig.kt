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
import io.matthewnelson.kmp.tor.common.annotation.InternalTorApi
import kotlin.jvm.JvmStatic
import io.matthewnelson.kmp.tor.common.util.TorStrings.REDACTED
import io.matthewnelson.kmp.tor.common.util.TorStrings.SP
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.TorF.False
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig.Option.TorF.True
import io.matthewnelson.kmp.tor.controller.common.file.Path
import kotlin.jvm.JvmInline
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
    val settings: Map<TorConfig.Setting<*>, TorConfig.Option>,
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
               updateIfPresent(DisableNetwork::class) {
                   setDefault()
               }
               put(ConnectionPadding().set(AorTorF.False))
               put(Ports.Control().set(AorDorPort.Value(Port(9051))))
               remove(DisableNetwork())
           }.build()
       }
     * */
    class Builder {
        private val settings: MutableMap<TorConfig.Setting<*>, TorConfig.Option> = mutableMapOf()

        fun remove(setting: TorConfig.Setting<*>): Builder = apply {
            settings.remove(setting)
        }

        @Suppress("unchecked_cast")
        fun <T: Setting<*>> removeInstanceOf(clazz: KClass<T>): Builder = apply {
            val toRemove = mutableListOf<T>()
            for (setting in settings.keys) {
                if (setting::class == clazz) {
                    toRemove.add(setting as T)
                }
            }

            for (setting in toRemove) {
                settings.remove(setting)
            }
        }

        fun put(config: TorConfig): Builder = apply {
            settings.putAll(config.settings)
        }

        fun put(setting: TorConfig.Setting<*>): Builder = apply {
            setting.value?.let { settings[setting] = it } ?: remove(setting)
        }

        fun put(settings: Collection<TorConfig.Setting<*>>): Builder = apply {
            for (setting in settings) {
                setting.value?.let { this.settings[setting] = it } ?: remove(setting)
            }
        }

        fun putIfAbsent(setting: TorConfig.Setting<*>): Builder = apply {
            if (settings[setting] == null) {
                setting.value?.let { settings[setting] = it }
            }
        }

        @Suppress("unchecked_cast")
        fun <T: Option?, V: Setting<T>> updateIfPresent(
            clazz: KClass<out V>,
            action: V.() -> Unit
        ): Builder = apply {
            var soughtKey: V? = null

            for (key in settings.keys) {
                if (key::class == clazz) {
                    action.invoke(key as V)
                    soughtKey = key
                    break
                }
            }

            if (soughtKey != null) {
                soughtKey.value?.let { settings[soughtKey] = it } ?: settings.remove(soughtKey)
            }
        }

        @OptIn(InternalTorApi::class)
        fun build(): TorConfig {
            val sb = StringBuilder()

            val map = settings.toMap()
            for (entry in map.entries) {
                val key = entry.key
                sb.append(key.keyword)
                sb.append(SP)
                sb.append(entry.value.value)

                if (key is Setting.Ports) {
                    when (key) {
                        is Setting.Ports.Control -> {
                            key.flags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.Dns -> {
                            key.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.HttpTunnel -> {
                            key.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.Socks -> {
                            key.flags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                            key.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                        is Setting.Ports.Trans -> {
                            key.isolationFlags?.let { flags ->
                                for (flag in flags) {
                                    sb.append(SP)
                                    sb.append(flag.value)
                                }
                            }
                        }
                    }
                }

                sb.append('\n')
            }

            return TorConfig(map, sb.toString())
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
     * val socksPort: Setting.Ports.Socks = Setting.Ports.Socks()
     *     .set(Option.AorDorPort.Value(port = Port(9250)))
     *     .setFlags(flags = setOf(
     *         Setting.Ports.Socks.Flag.OnionTrafficOnly
     *     ))
     *     .setIsolationFlags(flags = setOf(
     *         Setting.Ports.IsolationFlag.IsolateClientProtocol,
     *         Setting.Ports.IsolationFlag.IsolateDestAddr,
     *     ))
     *
     * https://2019.www.torproject.org/docs/tor-manual.html.en
     * */
    sealed class Setting<T: Option?>(val keyword: String) {

        abstract val default: T
        abstract var value: T
            protected set
        val isDefault: Boolean get() = value == default

        fun set(value: T): Setting<T> {
            this.value = value
            return this
        }
        open fun setDefault(): Setting<T> {
            this.value = default
            return this
        }

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
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#CacheDirectory
         * */
        class CacheDirectory                : Setting<Option.FileSystemDir?>("CacheDirectory") {
            override val default: Option.FileSystemDir? = null
            override var value: Option.FileSystemDir? = default
                set(value) { field = value?.nullIfEmpty }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ClientOnionAuthDir
         * */
        class ClientOnionAuthDir            : Setting<Option.FileSystemDir?>("ClientOnionAuthDir") {
            override val default: Option.FileSystemDir? = null
            override var value: Option.FileSystemDir? = default
                set(value) { field = value?.nullIfEmpty }

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
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ConnectionPaddingReduced
         * */
        class ConnectionPaddingReduced      : Setting<Option.TorF>("ReducedConnectionPadding") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPortWriteToFile
         * */
        class ControlPortWriteToFile        : Setting<Option.FileSystemFile?>("ControlPortWriteToFile") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }

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
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#CookieAuthFile
         * */
        class CookieAuthFile                : Setting<Option.FileSystemFile?>("CookieAuthFile") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }

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
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantCanceledByStartup
         * */
        class DormantCanceledByStartup      : Setting<Option.TorF>("DormantCanceledByStartup") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default
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
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantOnFirstStartup
         * */
        class DormantOnFirstStartup         : Setting<Option.TorF>("DormantOnFirstStartup") {
            override val default: Option.TorF get() = Option.TorF.False
            override var value: Option.TorF = default
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#DormantTimeoutDisabledByIdleStreams
         * */
        class DormantTimeoutDisabledByIdleStreams   : Setting<Option.TorF>("DormantTimeoutDisabledByIdleStreams") {
            override val default: Option.TorF get() = Option.TorF.True
            override var value: Option.TorF = default
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPExcludeUnknown
         * */
        class GeoIPExcludeUnknown           : Setting<Option.AorTorF>("GeoIPExcludeUnknown") {
            override val default: Option.AorTorF get() = Option.AorTorF.Auto
            override var value: Option.AorTorF = default
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPFile
         * */
        class GeoIpV4File                   : Setting<Option.FileSystemFile?>("GeoIPFile") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPv6File
         * */
        class GeoIpV6File                   : Setting<Option.FileSystemFile?>("GeoIPv6File") {
            override val default: Option.FileSystemFile? = null
            override var value: Option.FileSystemFile? = default
                set(value) { field = value?.nullIfEmpty }
        }

        // TODO: Logs.Debug && Logs.Info
        // TODO: Nodes.Entry, Nodes.Exclude, Nodes.Exit, Nodes.Strict

        sealed class Ports(keyword: String) : Setting<Option.AorDorPort>(keyword) {

            abstract fun clone(): Ports

            /**
             * https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPort
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
                    value = default
                    flags = null
                    return this
                }

                fun setFlags(flags: Set<Flag>?): Control {
                    this.flags = flags
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
                    value = default
                    isolationFlags = null
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Dns {
                    isolationFlags = flags
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
                    value = default
                    isolationFlags = null
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): HttpTunnel {
                    isolationFlags = flags
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
                override val default: Option.AorDorPort get() = Option.AorDorPort.Value(Port(9050))
                override var value: Option.AorDorPort = default
                var flags: Set<Flag>? = null
                    private set
                var isolationFlags: Set<IsolationFlag>? = null
                    private set

                override fun setDefault(): Socks {
                    value = default
                    flags = null
                    isolationFlags = null
                    return this
                }

                fun setFlags(flags: Set<Flag>?): Socks {
                    this.flags = flags
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Socks {
                    isolationFlags = flags
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
                    value = default
                    isolationFlags = null
                    return this
                }

                fun setIsolationFlags(flags: Set<IsolationFlag>?): Trans {
                    isolationFlags = flags
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
        }

        /**
         * https://2019.www.torproject.org/docs/tor-manual.html.en#SyslogIdentityTag
         * */
        class SyslogIdentityTag             : Setting<Option.FieldId?>("SyslogIdentityTag") {
            override val default: Option.FieldId? get() = null
            override var value: Option.FieldId? = default
                set(value) { field = value?.nullIfEmpty }
        }
    }

    sealed interface Option {
        val value: String

        /**
         * Either [True] or [False]
         * */
        sealed interface TorF                               : AorTorF {
            object True                                         : TorF {
                override val value: String = "1"
                override fun toString(): String = value
            }
            object False                                        : TorF {
                override val value: String = "0"
                override fun toString(): String = value
            }
        }

        /**
         * Either [Auto], [True], or [False]
         * */
        sealed interface AorTorF                            : Option {
            object Auto                                         : AorTorF {
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
        sealed interface AorDorPort                         : Option {
            object Auto                                         : AorDorPort {
                override val value: String get() = AorTorF.Auto.value
                override fun toString(): String = value
            }

            object Disable                                      : AorDorPort {
                override val value: String get() = TorF.False.value
                override fun toString(): String = value
            }

            @JvmInline
            value class Value(val port: Port)                   : AorDorPort {
                override val value: String get() = port.value.toString()
                override fun toString(): String = value
            }
        }

        @JvmInline
        value class FileSystemFile(val path: Path)          : Option {
            override val value: String get() = path.value
            val nullIfEmpty: FileSystemFile? get() = if (value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @JvmInline
        value class FileSystemDir(val path: Path)          : Option {
            override val value: String get() = path.value
            val nullIfEmpty: FileSystemDir? get() = if (value.isEmpty()) null else this
            override fun toString(): String = value
        }

        @JvmInline
        value class FieldId(override val value: String)     : Option {
            val nullIfEmpty: FieldId? get() = if(value.isEmpty()) null else this
            override fun toString(): String = value
        }

        sealed interface Time                               : Option {
            val time: Int

            @JvmInline
            value class Minutes(override val time: Int)         : Time {
                override val value: String get() = if (time < 1) {
                    "1 minutes"
                } else {
                    "$time minutes"
                }

                override fun toString(): String = value
            }

            @JvmInline
            value class Hours(override val time: Int)           : Time {
                override val value: String get() = if (time < 1) {
                    "1 hours"
                } else {
                    "$time hours"
                }

                override fun toString(): String = value
            }

            @JvmInline
            value class Days(override val time: Int)            : Time {
                override val value: String get() = if (time < 1) {
                    "1 days"
                } else {
                    "$time days"
                }

                override fun toString(): String = value
            }

            @JvmInline
            value class Weeks(override val time: Int)           : Time {
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
