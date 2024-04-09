/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("ClassName", "FunctionName")

package io.matthewnelson.kmp.tor.runtime.core

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableMap
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.process.Process
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V4.Companion.toIPAddressV4
import io.matthewnelson.kmp.tor.runtime.core.address.IPAddress.V6.Companion.toIPAddressV6
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Keyword.Attribute
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Setting.Companion.filterByAttribute
import io.matthewnelson.kmp.tor.runtime.core.TorConfig.Setting.Companion.filterByKeyword
import io.matthewnelson.kmp.tor.runtime.core.builder.*
import io.matthewnelson.kmp.tor.runtime.core.internal.IsAndroidHost
import io.matthewnelson.kmp.tor.runtime.core.internal.IsUnixLikeHost
import io.matthewnelson.kmp.tor.runtime.core.internal.toByte
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Holder for a configuration
 *
 * @see [Builder]
 * @see [Companion.Builder]
 * */
@OptIn(InternalKmpTorApi::class)
public class TorConfig private constructor(
    @JvmField
    public val settings: Set<Setting>,
) {

    public inline fun <reified T: Keyword> filterByKeyword(): List<Setting> {
        return settings.filterByKeyword<T>()
    }

    public inline fun <reified T: Attribute> filterByAttribute(): List<Setting> {
        return settings.filterByAttribute<T>()
    }

    public companion object {

        /**
         * Opener for creating a new [TorConfig]
         *
         * @see [TorConfig.Builder]
         * */
        @JvmStatic
        public fun Builder(
            block: ThisBlock<Builder>,
        ): TorConfig = Builder.build(null, block)

        /**
         * Opener for creating a new [TorConfig] that inherits
         * settings from another [TorConfig].
         *
         * @see [TorConfig.Builder]
         * */
        @JvmStatic
        public fun Builder(
            other: TorConfig,
            block: ThisBlock<Builder>,
        ): TorConfig = Builder.build(other, block)

        internal const val AUTO = "auto"
    }

    /**
     * Configures a new [TorConfig]
     *
     * e.g. (Kotlin)
     *
     *     val dns = TorConfig.__DNSPort.Builder { auto() }
     *
     *     val config = TorConfig.Builder {
     *         // An already configured setting
     *         put(dns)
     *
     *         // Via the Setting.Factory
     *         put(TorConfig.__SocksPort) {
     *             asPort {
     *                 port(9050.toPortProxy())
     *             }
     *         }
     *     }
     *
     * e.g. (Java)
     *
     *     TorConfig.Setting dns = TorConfig.__DNSPort.Companion.Builder(b -> {
     *         b.auto();
     *     });
     *
     *     TorConfig config = TorConfig.Builder(b -> {
     *         // An already configured setting
     *         b.put(dns)
     *
     *         // Via the Setting.Factory
     *         b.put(TorConfig.__SocksPort.Companion, f -> {
     *             f.asPort(p -> {
     *                 p.port(Port.Proxy.get(9050));
     *             });
     *         });
     *     });
     *
     * @see [TorConfig.Companion.Builder]
     * */
    @KmpTorDsl
    public open class Builder private constructor(other: TorConfig?) {

        @JvmField
        protected val settings: MutableSet<Setting> = mutableSetOf()
        // For dealing with inherited disabled port
        @JvmField
        protected val inheritedDisabledPorts: MutableSet<Setting> = mutableSetOf()

        /**
         * Add an already configured [Setting].
         * */
        @KmpTorDsl
        public fun put(setting: Setting?): Builder = put(setting, ifAbsent = false)

        /**
         * Add an already configured [Setting] if not currently
         * present.
         * */
        @KmpTorDsl
        public fun putIfAbsent(setting: Setting?): Builder = put(setting, ifAbsent = true)

        /**
         * Configure a [Setting] via its [Setting.Factory] and add.
         * */
        @KmpTorDsl
        public fun <B: Setting.Builder, S: Setting?> put(
            factory: Setting.Factory<B, S>,
            block: ThisBlock<B>,
        ): Builder = put(factory.Builder(block), ifAbsent = false)

        /**
         * Configure a [Setting] via its [Setting.Factory] and add
         * if not currently present.
         * */
        @KmpTorDsl
        public fun <B: Setting.Builder, S: Setting?> putIfAbsent(
            factory: Setting.Factory<B, S>,
            block: ThisBlock<B>,
        ): Builder = put(factory.Builder(block), ifAbsent = true)

        private fun put(setting: Setting?, ifAbsent: Boolean): Builder {
            if (setting == null) return this

            var added = settings.add(setting)

            if (!added && !ifAbsent) {
                // Remove and replace the other item
                // e.g. SocksPort and DNSPort are configured
                //      with the same port value
                settings.remove(setting)
                added = settings.add(setting)
            }

            if (added) {
                inheritedDisabledPorts.find {
                    it.keyword == setting.keyword
                }?.let { disabled ->
                    // It is being overridden by a newly
                    // configured port. Remove the inherited
                    // setting.
                    inheritedDisabledPorts.remove(disabled)
                }
            }

            return this
        }

        init {
            if (other != null) {
                val disabledPorts = mutableSetOf<Setting>()

                other.settings.forEach { setting ->
                    if (
                        setting.keyword.attributes.contains(Attribute.Port)
                        && setting.argument == "0"
                    ) {
                        disabledPorts.add(setting)
                        return@forEach
                    }

                    put(setting)
                }

                inheritedDisabledPorts.addAll(disabledPorts)
            }
        }

        internal companion object {

            @JvmSynthetic
            internal fun build(
                other: TorConfig?,
                block: ThisBlock<Builder>,
            ): TorConfig {
                val b = Extended(other).apply(block)
                // Copy our settings in before we modify them
                val settings = b.settings.toMutableSet()
                settings.addAll(b.inheritedDisabledPorts)

                settings.filterByAttribute<Attribute.Port>().forEach { setting ->
                    if (setting.keyword is HiddenServiceDir.Companion) return@forEach
                    if (setting.argument != "0") return@forEach

                    // A port is configured as disabled.
                    // remove all other ports of that type
                    val toRemove = settings.filter { otherSetting ->
                        otherSetting.keyword == setting.keyword
                        && otherSetting.argument != setting.argument
                    }
                    if (toRemove.isEmpty()) return@forEach
                    settings.removeAll(toRemove.toSet())
                }

                return TorConfig(settings.toImmutableSet())
            }
        }

        // Used by TorRuntime for startup configuration
        private class Extended(other: TorConfig?): Builder(other), ExtendedTorConfigBuilder {
            override fun contains(keyword: Keyword): Boolean {
                for (setting in settings) {
                    for (item in setting.items) {
                        if (item.keyword == keyword) return true
                    }
                }

                if (keyword.attributes.contains(Attribute.Port)) {
                    for (port in inheritedDisabledPorts) {
                        if (port.keyword == keyword) return true
                    }
                }

                return false
            }

            override fun remove(setting: Setting) {
                settings.remove(setting)
            }
        }
    }

    /**
     * Note that Tor's default value as per the spec is disabled (0). As this library
     * depends on the control port the default [argument] used is "auto" and it cannot
     * be set to disabled (0).
     *
     * [ControlPort](https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPort)
     *
     * [Non-Persistent Options](https://2019.www.torproject.org/docs/tor-manual.html.en#_non_persistent_options)
     * */
    @KmpTorDsl
    public class __ControlPort private constructor(): Setting.Builder(
        keyword = Companion,
    ),  TCPPortBuilder.DSL<TCPPortBuilder.Control, __ControlPort>,
        UnixFlagBuilder.DSL<__ControlPort>,
        UnixSocketBuilder.DSL<__ControlPort>
    {

        private var argument: String = AUTO
        private var allowReassign = true
        private val unixFlags = mutableSetOf<String>()

        @KmpTorDsl
        public override fun asPort(
            block: ThisBlock<TCPPortBuilder.Control>,
        ): __ControlPort {
            val (reassign, arg) = TCPPortBuilder.Control.build(block)
            argument = arg
            allowReassign = reassign
            return this
        }

        @KmpTorDsl
        @Throws(UnsupportedOperationException::class)
        public override fun asUnixSocket(
            block: ThisBlock<UnixSocketBuilder>
        ): __ControlPort {
            val path = UnixSocketBuilder.build(block) ?: return this
            argument = path
            return this
        }

        @KmpTorDsl
        public override fun unixFlags(
            block: ThisBlock<UnixFlagBuilder>
        ): __ControlPort {
            UnixFlagBuilder.configure(isControl = true, unixFlags, block)
            return this
        }

        public companion object: Setting.Factory<__ControlPort, Setting>(
            name = "__ControlPort",
            attributes = immutableSetOf(Attribute.Port, Attribute.UnixSocket),
            isCmdLineArg = true,
            isUnique = false,
            factory = { __ControlPort() },
            build = {
                val argument = argument
                val flags = if (argument.startsWith("unix:")) unixFlags else emptySet()
                val extras = Extra.AllowReassign.create(argument, allowReassign)
                build(argument, optionals = flags, extras = extras)!!
            },
        )
    }

    /**
     * [DNSPort](https://2019.www.torproject.org/docs/tor-manual.html.en#DNSPort)
     *
     * [Non-Persistent Options](https://2019.www.torproject.org/docs/tor-manual.html.en#_non_persistent_options)
     * */
    @KmpTorDsl
    public class __DNSPort private constructor(): Setting.Builder(
        keyword = Companion,
    ),  TCPPortBuilder.DSLAuto<__DNSPort>,
        TCPPortBuilder.DSLDisable<__DNSPort>,
        TCPPortBuilder.DSLPort<__DNSPort>,
        IsolationFlagBuilder.DSL<__DNSPort>
    {

        private var argument: String = "0"
        private var allowReassign: Boolean = true
        private val isolationFlags = mutableSetOf<String>()

        @KmpTorDsl
        public override fun auto(): __DNSPort {
            argument = AUTO
            return this
        }

        @KmpTorDsl
        public override fun disable(): __DNSPort {
            argument = "0"
            return this
        }

        @KmpTorDsl
        public override fun port(port: Port.Proxy): __DNSPort {
            argument = port.toString()
            return this
        }

        @KmpTorDsl
        public override fun isolationFlags(
            block: ThisBlock<IsolationFlagBuilder>,
        ): __DNSPort {
            IsolationFlagBuilder.configure(isolationFlags, block)
            return this
        }

        @KmpTorDsl
        public override fun reassignable(allow: Boolean): __DNSPort {
            allowReassign = allow
            return this
        }

        public companion object: Setting.Factory<__DNSPort, Setting>(
            name = "__DNSPort",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
            factory = { __DNSPort() },
            build = {
                val argument = argument
                val flags = if (argument == "0") emptySet() else isolationFlags
                val extras = Extra.AllowReassign.create(argument, allowReassign)
                build(argument, optionals = flags, extras = extras)!!
            },
        )
    }

    /**
     * [HTTPTunnelPort](https://2019.www.torproject.org/docs/tor-manual.html.en#HTTPTunnelPort)
     *
     * [Non-Persistent Options](https://2019.www.torproject.org/docs/tor-manual.html.en#_non_persistent_options)
     * */
    @KmpTorDsl
    public class __HTTPTunnelPort private constructor(): Setting.Builder(
        keyword = Companion,
    ),  TCPPortBuilder.DSLAuto<__HTTPTunnelPort>,
        TCPPortBuilder.DSLDisable<__HTTPTunnelPort>,
        TCPPortBuilder.DSLPort<__HTTPTunnelPort>,
        IsolationFlagBuilder.DSL<__HTTPTunnelPort>
    {

        private var argument: String = "0"
        private var allowReassign: Boolean = true
        private val isolationFlags = mutableSetOf<String>()

        @KmpTorDsl
        public override fun auto(): __HTTPTunnelPort {
            argument = AUTO
            return this
        }

        @KmpTorDsl
        public override fun disable(): __HTTPTunnelPort {
            argument = "0"
            return this
        }

        @KmpTorDsl
        public override fun port(port: Port.Proxy): __HTTPTunnelPort {
            argument = port.toString()
            return this
        }

        @KmpTorDsl
        public override fun isolationFlags(
            block: ThisBlock<IsolationFlagBuilder>,
        ): __HTTPTunnelPort {
            IsolationFlagBuilder.configure(isolationFlags, block)
            return this
        }

        @KmpTorDsl
        public override fun reassignable(allow: Boolean): __HTTPTunnelPort {
            allowReassign = allow
            return this
        }

        public companion object: Setting.Factory<__HTTPTunnelPort, Setting>(
            name = "__HTTPTunnelPort",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
            factory = { __HTTPTunnelPort() },
            build = {
                val argument = argument
                val flags = if (argument == "0") emptySet() else isolationFlags
                val extras = Extra.AllowReassign.create(argument, allowReassign)
                build(argument, optionals = flags, extras = extras)!!
            },
        )
    }

    /**
     * [TAKEOWNERSHIP](https://torproject.gitlab.io/torspec/control-spec/#takeownership)
     * */
    @KmpTorDsl
    public class __OwningControllerProcess private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var processId: Int = Process.Current.pid()

        public companion object: Setting.Factory<__OwningControllerProcess, Setting>(
            name = "__OwningControllerProcess",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { __OwningControllerProcess() },
            build = { build(processId.toString())!! },
        )
    }

    /**
     * [SocksPort](https://2019.www.torproject.org/docs/tor-manual.html.en#SocksPort)
     *
     * [Non-Persistent Options](https://2019.www.torproject.org/docs/tor-manual.html.en#_non_persistent_options)
     * */
    @KmpTorDsl
    public class __SocksPort private constructor(): Setting.Builder(
        keyword = Companion,
    ),  IsolationFlagBuilder.DSL<__SocksPort>,
        TCPPortBuilder.DSL<TCPPortBuilder.Socks, __SocksPort>,
        UnixFlagBuilder.DSL<__SocksPort>,
        UnixSocketBuilder.DSL<__SocksPort>
    {

        private var argument: String = "9050"
        private var allowReassign = true
        private val socksFlags = mutableSetOf<String>()
        private val unixFlags = mutableSetOf<String>()
        private val isolationFlags = mutableSetOf<String>()

        @KmpTorDsl
        public override fun asPort(
            block: ThisBlock<TCPPortBuilder.Socks>
        ): __SocksPort {
            val (reassign, arg) = TCPPortBuilder.Socks.build(block)
            argument = arg
            allowReassign = reassign
            return this
        }

        @KmpTorDsl
        @Throws(UnsupportedOperationException::class)
        public override fun asUnixSocket(
            block: ThisBlock<UnixSocketBuilder>
        ): __SocksPort {
            val path = UnixSocketBuilder.build(block) ?: return this
            argument = path
            return this
        }

        @KmpTorDsl
        public fun socksFlags(
            block: ThisBlock<SocksFlagBuilder>,
        ): __SocksPort {
            SocksFlagBuilder.configure(socksFlags, block)
            return this
        }

        @KmpTorDsl
        public override fun unixFlags(
            block: ThisBlock<UnixFlagBuilder>
        ): __SocksPort {
            UnixFlagBuilder.configure(isControl = false, unixFlags, block)
            return this
        }

        @KmpTorDsl
        public override fun isolationFlags(
            block: ThisBlock<IsolationFlagBuilder>,
        ): __SocksPort {
            IsolationFlagBuilder.configure(isolationFlags, block)
            return this
        }

        public companion object: Setting.Factory<__SocksPort, Setting>(
            name = "__SocksPort",
            attributes = immutableSetOf(Attribute.Port, Attribute.UnixSocket),
            isCmdLineArg = false,
            isUnique = false,
            factory = { __SocksPort() },
            build = {
                val argument = argument
                val flags = if (argument == "0") {
                    emptySet()
                } else {
                    val set = socksFlags.toMutableSet()
                    if (argument.startsWith("unix:")) set.addAll(unixFlags)
                    set.addAll(isolationFlags)
                    set
                }
                val extras = Extra.AllowReassign.create(argument, allowReassign)
                build(argument, optionals = flags, extras = extras)!!
            },
        )
    }

    /**
     * [TransPort](https://2019.www.torproject.org/docs/tor-manual.html.en#TransPort)
     *
     * [Non-Persistent Options](https://2019.www.torproject.org/docs/tor-manual.html.en#_non_persistent_options)
     * */
    @KmpTorDsl
    public class __TransPort private constructor(): Setting.Builder(
        keyword = Companion,
    ),  TCPPortBuilder.DSLAuto<__TransPort>,
        TCPPortBuilder.DSLDisable<__TransPort>,
        TCPPortBuilder.DSLPort<__TransPort>,
        IsolationFlagBuilder.DSL<__TransPort>
    {

        private var port: String = "0"
        private var allowReassign: Boolean = true
        private val isolationFlags = mutableSetOf<String>()

        @KmpTorDsl
        public override fun auto(): __TransPort {
            if (!IsUnixLikeHost) return this
            port = AUTO
            return this
        }

        @KmpTorDsl
        public override fun disable(): __TransPort {
            if (!IsUnixLikeHost) return this
            port = "0"
            return this
        }

        @KmpTorDsl
        public override fun port(port: Port.Proxy): __TransPort {
            if (!IsUnixLikeHost) return this
            this.port = port.toString()
            return this
        }

        @KmpTorDsl
        public override fun isolationFlags(
            block: ThisBlock<IsolationFlagBuilder>,
        ): __TransPort {
            if (!IsUnixLikeHost) return this
            IsolationFlagBuilder.configure(isolationFlags, block)
            return this
        }

        @KmpTorDsl
        public override fun reassignable(allow: Boolean): __TransPort {
            this.allowReassign = allow
            return this
        }

        public companion object: Setting.Factory<__TransPort, Setting>(
            name = "__TransPort",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
            factory = { __TransPort() },
            build = {
                val port = port
                val flags = if (port == "0") emptySet() else isolationFlags
                val extras = Extra.AllowReassign.create(port, allowReassign)
                build(port, optionals = flags, extras = extras)!!
            },
        ) {

        }
    }

    /**
     * [AutomapHostsOnResolve](https://2019.www.torproject.org/docs/tor-manual.html.en#AutomapHostsOnResolve)
     * */
    @KmpTorDsl
    public class AutomapHostsOnResolve private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<AutomapHostsOnResolve, Setting>(
            name = "AutomapHostsOnResolve",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { AutomapHostsOnResolve() },
            build = { build(enable.toByte().toString())!! },
        )
    }

    /**
     * [AutomapHostsSuffixes](https://2019.www.torproject.org/docs/tor-manual.html.en#AutomapHostsSuffixes)
     * */
    @KmpTorDsl
    public class AutomapHostsSuffixes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        private val suffixes = mutableSetOf<String>()

        /**
         * Add a single suffix (e.g. ".exit")
         *
         * If the prefixing dot character ('.') is missing, it will
         * automatically be added to [suffix].
         *
         * If no suffix are added, the default value of ".exit,.onion"
         * is utilized.
         *
         * No TLD checks for character validity are made outside
         * what is listed below. The only checks made are so that
         * the configuration text generated for all settings is
         * not affected.
         *
         * @throws [IllegalArgumentException] if [suffix]
         *  - Contains a backslash ('\')
         *  - Contains a forward slash ('/')
         *  - Contains a comma (',')
         *  - Contains a space (' ')
         *  - Contains more than 1 dot ('.')
         *  - Contains multiple lines
         *  - Is greater than 63 characters (prefixing . excluded)
         * */
        @KmpTorDsl
        @Throws(IllegalArgumentException::class)
        public fun add(suffix: String): AutomapHostsSuffixes {
            var value = suffix
            if (!value.startsWith('.')) {
                value = ".$value"
            }

            if (suffixes.contains(".")) return this
            if (value == ".") suffixes.clear()

            require(!value.contains('\\')) { "suffix cannot contain a backslash" }
            require(!value.contains('/')) { "suffix cannot contain a forward slash" }
            require(!value.contains(',')) { "suffix cannot contain a comma" }
            require(!value.contains(' ')) { "suffix cannot contain spaces" }
            require(value.count { it == '.' } == 1) { "suffix cannot contain multiple dots" }
            require(value.lines().size == 1) { "suffix cannot be multiple lines" }

            // . + 63 characters max
            require(value.length <= 64) { "suffix cannot be more than 63 characters (excluding the . prefix)" }

            suffixes.add(value)
            return this
        }

        @KmpTorDsl
        public fun all(): AutomapHostsSuffixes = add(".")

        public companion object: Setting.Factory<AutomapHostsSuffixes, Setting>(
            name = "AutomapHostsSuffixes",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { AutomapHostsSuffixes() },
            build = {
                var result = suffixes.joinToString(separator = ",")
                if (result.isBlank()) result = ".exit,.onion"
                build(result)!!
            },
        )
    }

    /**
     * [CacheDirectory](https://2019.www.torproject.org/docs/tor-manual.html.en#CacheDirectory)
     * */
    @KmpTorDsl
    public class CacheDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var directory: File? = null

        public companion object: Setting.Factory<CacheDirectory, Setting?>(
            name = "CacheDirectory",
            attributes = immutableSetOf(Attribute.Directory),
            isCmdLineArg = true,
            isUnique = true,
            factory = { CacheDirectory() },
            build = { build(directory?.absoluteFile?.normalize()?.path) },
        )
    }

    /**
     * [ClientOnionAuthDir](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientOnionAuthDir)
     * */
    @KmpTorDsl
    public class ClientOnionAuthDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var directory: File? = null

        public companion object: Setting.Factory<ClientOnionAuthDir, Setting?>(
            name = "ClientOnionAuthDir",
            attributes = immutableSetOf(Attribute.Directory),
            isCmdLineArg = true,
            isUnique = true,
            factory = { ClientOnionAuthDir() },
            build = { build(directory?.absoluteFile?.normalize()?.path) },
        ) {
            /**
             * A default directory name to use (if desired).
             * */
            public const val DEFAULT_NAME: String = "auth_private_files"
        }
    }

    /**
     * [ConnectionPadding](https://2019.www.torproject.org/docs/tor-manual.html.en#ConnectionPadding)
     * */
    @KmpTorDsl
    public class ConnectionPadding private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        private var argument: String = AUTO

        @KmpTorDsl
        public fun auto(): ConnectionPadding {
            argument = AUTO
            return this
        }

        @KmpTorDsl
        public fun enable(enable: Boolean): ConnectionPadding {
            argument = enable.toByte().toString()
            return this
        }

        public companion object: Setting.Factory<ConnectionPadding, Setting>(
            name = "ConnectionPadding",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { ConnectionPadding() },
            build = { build(argument)!! },
        )
    }

    /**
     * [ConnectionPaddingReduced](https://2019.www.torproject.org/docs/tor-manual.html.en#ConnectionPaddingReduced)
     * */
    @KmpTorDsl
    public class ConnectionPaddingReduced private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<ConnectionPaddingReduced, Setting>(
            name = "ConnectionPaddingReduced",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { ConnectionPaddingReduced() },
            build = { build(enable.toByte().toString())!! },
        )
    }

    /**
     * [ControlPortWriteToFile](https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPortWriteToFile)
     * */
    @KmpTorDsl
    public class ControlPortWriteToFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var file: File? = null

        public companion object: Setting.Factory<ControlPortWriteToFile, Setting?>(
            name = "ControlPortWriteToFile",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = true,
            isUnique = true,
            factory = { ControlPortWriteToFile() },
            build = { build(file?.absoluteFile?.normalize()?.path) },
        ) {
            /**
             * A default file name to use (if desired).
             * */
            public const val DEFAULT_NAME: String = "control.txt"
        }
    }

    /**
     * [CookieAuthentication](https://2019.www.torproject.org/docs/tor-manual.html.en#CookieAuthentication)
     * */
    @KmpTorDsl
    public class CookieAuthentication private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<CookieAuthentication, Setting>(
            name = "CookieAuthentication",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { CookieAuthentication() },
            build = { build(enable.toByte().toString())!! },
        )
    }

    /**
     * [CookieAuthFile](https://2019.www.torproject.org/docs/tor-manual.html.en#CookieAuthFile)
     * */
    @KmpTorDsl
    public class CookieAuthFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var file: File? = null

        public companion object: Setting.Factory<CookieAuthFile, Setting?>(
            name = "CookieAuthFile",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = true,
            isUnique = true,
            factory = { CookieAuthFile() },
            build = { build(file?.absoluteFile?.normalize()?.path) },
        ) {
            /**
             * A default file name to use (if desired).
             * */
            public const val DEFAULT_NAME: String = "control_auth_cookie"
        }
    }

    /**
     * [DataDirectory](https://2019.www.torproject.org/docs/tor-manual.html.en#DataDirectory)
     * */
    @KmpTorDsl
    public class DataDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var directory: File? = null

        public companion object: Setting.Factory<DataDirectory, Setting?>(
            name = "DataDirectory",
            attributes = immutableSetOf(Attribute.Directory),
            isCmdLineArg = true,
            isUnique = true,
            factory = { DataDirectory() },
            build = { build(directory?.absoluteFile?.normalize()?.path) },
        ) {
            /**
             * A default directory name to use (if desired).
             * */
            public const val DEFAULT_NAME: String = "data"
        }
    }

    /**
     * [DisableNetwork](https://2019.www.torproject.org/docs/tor-manual.html.en#DisableNetwork)
     * */
    @KmpTorDsl
    public class DisableNetwork private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var disable: Boolean = false

        public companion object: Setting.Factory<DisableNetwork, Setting>(
            name = "DisableNetwork",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { DisableNetwork() },
            build = { build(disable.toByte().toString())!! },
        )
    }

    /**
     * [DormantCanceledByStartup](https://2019.www.torproject.org/docs/tor-manual.html.en#DormantCanceledByStartup)
     * */
    @KmpTorDsl
    public class DormantCanceledByStartup private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var cancel: Boolean = false

        public companion object: Setting.Factory<DormantCanceledByStartup, Setting>(
            name = "DormantCanceledByStartup",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { DormantCanceledByStartup() },
            build = { build(cancel.toByte().toString())!! },
        )
    }

    /**
     * [DormantClientTimeout](https://2019.www.torproject.org/docs/tor-manual.html.en#DormantClientTimeout)
     * */
    @KmpTorDsl
    public class DormantClientTimeout private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        private var timeout: String = "24 hours"

        @KmpTorDsl
        public fun minutes(n: Int): DormantClientTimeout {
            // Must be at LEAST 10 minutes
            timeout = "${if (n < 10) 10 else n} minutes"
            return this
        }

        @KmpTorDsl
        public fun hours(n: Int): DormantClientTimeout {
            timeout = "${if (n < 1) 1 else n} hours"
            return this
        }

        @KmpTorDsl
        public fun days(n: Int): DormantClientTimeout {
            timeout = "${if (n < 1) 1 else n} days"
            return this
        }

        @KmpTorDsl
        public fun weeks(n: Int): DormantClientTimeout {
            timeout = "${if (n < 1) 1 else n} weeks"
            return this
        }

        public companion object: Setting.Factory<DormantClientTimeout, Setting>(
            name = "DormantClientTimeout",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { DormantClientTimeout() },
            build = { build(timeout)!! },
        )
    }

    /**
     * [DormantOnFirstStartup](https://2019.www.torproject.org/docs/tor-manual.html.en#DormantOnFirstStartup)
     * */
    @KmpTorDsl
    public class DormantOnFirstStartup private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<DormantOnFirstStartup, Setting>(
            name = "DormantOnFirstStartup",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { DormantOnFirstStartup() },
            build = { build(enable.toByte().toString())!! },
        )
    }

    /**
     * [DormantTimeoutDisabledByIdleStreams](https://2019.www.torproject.org/docs/tor-manual.html.en#DormantTimeoutDisabledByIdleStreams)
     * */
    @KmpTorDsl
    public class DormantTimeoutDisabledByIdleStreams private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = true

        public companion object: Setting.Factory<DormantTimeoutDisabledByIdleStreams, Setting>(
            name = "DormantTimeoutDisabledByIdleStreams",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { DormantTimeoutDisabledByIdleStreams() },
            build = { build(enable.toByte().toString())!! },
        )
    }

    /**
     * [GeoIPExcludeUnknown](https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPExcludeUnknown)
     * */
    @KmpTorDsl
    public class GeoIPExcludeUnknown private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        private var argument: String = AUTO

        @KmpTorDsl
        public fun auto(): GeoIPExcludeUnknown {
            argument = AUTO
            return this
        }

        @KmpTorDsl
        public fun exclude(exclude: Boolean): GeoIPExcludeUnknown {
            argument = exclude.toByte().toString()
            return this
        }

        public companion object: Setting.Factory<GeoIPExcludeUnknown, Setting>(
            name = "GeoIPExcludeUnknown",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { GeoIPExcludeUnknown() },
            build = { build(argument)!! },
        )
    }

    /**
     * [GeoIPFile](https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPFile)
     * */
    @KmpTorDsl
    public class GeoIPFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var file: File? = null

        public companion object: Setting.Factory<GeoIPFile, Setting?>(
            name = "GeoIPFile",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = true,
            isUnique = true,
            factory = { GeoIPFile() },
            build = { build(file?.absoluteFile?.normalize()?.path) },
        )
    }

    /**
     * [GeoIPv6File](https://2019.www.torproject.org/docs/tor-manual.html.en#GeoIPv6File)
     * */
    @KmpTorDsl
    public class GeoIPv6File private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var file: File? = null

        public companion object: Setting.Factory<GeoIPv6File, Setting?>(
            name = "GeoIPv6File",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = true,
            isUnique = true,
            factory = { GeoIPv6File() },
            build = { build(file?.absoluteFile?.normalize()?.path) },
        )
    }

    /**
     * All other HiddenService options are only configurable from within
     * the [HiddenServiceDir] DSL (the "root" [Setting.items] [LineItem]).
     *
     * The [directory], [version], and a minimum of 1 [port] **MUST** be configured
     * in order to create the hidden service [Setting].
     *
     * e.g. (Minimum)
     *
     *     val setting = HiddenServiceDir.Builder {
     *         directory = "/some/path".toFile()
     *         port { virtual = 80.toPort() }
     *         version { HSv(3) }
     *     }
     *
     *     assertNotNull(setting)
     *
     * [HiddenServiceDir](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceDir)
     * */
    @KmpTorDsl
    public class HiddenServiceDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        private val ports = mutableSetOf<LineItem>()
        private var version: LineItem? = null
        private var allowUnknownPorts: LineItem = DEFAULT_ALLOW_UNKNOWN_PORTS
//        private var exportCircuitID: LineItem = DEFAULT_EXPORT_CIRCUIT_ID
        private var maxStreams: LineItem = DEFAULT_MAX_STREAMS
        private var maxStreamsCloseCircuit: LineItem = DEFAULT_MAX_STREAMS_CLOSE_CIRCUIT
        private var dirGroupReadable: LineItem = DEFAULT_DIR_GROUP_READABLE
        private val numIntroductionPoints = mutableMapOf("3" to 3)

        @JvmField
        public var directory: File? = null

        @KmpTorDsl
        public fun port(
            block: ThisBlock<HiddenServicePort>,
        ): HiddenServiceDir {
            val port = HiddenServicePort.build(block)
            if (port != null) ports.add(port)
            return this
        }

        @KmpTorDsl
        public fun version(
            block: ThisBlock<HiddenServiceVersion>,
        ): HiddenServiceDir {
            val v = HiddenServiceVersion.build(block) ?: return this
            version = v
            return this
        }

        @KmpTorDsl
        public fun allowUnknownPorts(
            block: ThisBlock<HiddenServiceAllowUnknownPorts>,
        ): HiddenServiceDir {
            allowUnknownPorts = HiddenServiceAllowUnknownPorts.build(block)
            return this
        }

        @KmpTorDsl
        public fun maxStreams(
            block: ThisBlock<HiddenServiceMaxStreams>,
        ): HiddenServiceDir {
            maxStreams = HiddenServiceMaxStreams.build(block)
            return this
        }

        @KmpTorDsl
        public fun maxStreamsCloseCircuit(
            block: ThisBlock<HiddenServiceMaxStreamsCloseCircuit>,
        ): HiddenServiceDir {
            maxStreamsCloseCircuit = HiddenServiceMaxStreamsCloseCircuit.build(block)
            return this
        }

        @KmpTorDsl
        public fun dirGroupReadable(
            block: ThisBlock<HiddenServiceDirGroupReadable>,
        ): HiddenServiceDir {
            dirGroupReadable = HiddenServiceDirGroupReadable.build(block)
            return this
        }

        @KmpTorDsl
        public fun numIntroductionPoints(
            block: ThisBlock<HiddenServiceNumIntroductionPoints>,
        ): HiddenServiceDir {
            HiddenServiceNumIntroductionPoints.configure(numIntroductionPoints, block)
            return this
        }

        private fun build(): Setting? {
            val directory = directory?.absoluteFile?.normalize()?.path ?: return null
            val version = version ?: return null
            val ports = ports.also { if (it.isEmpty()) return null }

            val numIntroductionPoints = (numIntroductionPoints[version.argument] ?: 3).let { points ->
                HiddenServiceNumIntroductionPoints.toLineItem(points.toString())!!
            }

            val others = LinkedHashSet<LineItem>(ports.size + 6, 1.0F)
            others.add(version)
            others.addAll(ports)
            others.add(allowUnknownPorts)
            others.add(maxStreams)
            others.add(maxStreamsCloseCircuit)
            others.add(dirGroupReadable)
            others.add(numIntroductionPoints)

            return build(directory, others = others)
        }

        public companion object: Setting.Factory<HiddenServiceDir, Setting?>(
            name = "HiddenServiceDir",
            attributes = immutableSetOf(Attribute.Directory, Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
            factory = { HiddenServiceDir() },
            build = { build() },
        ) {
            /**
             * A default directory name to use (if desired) for the parent directory
             * of all configured hidden services.
             * */
            public const val DEFAULT_PARENT_DIR_NAME: String = "hidden_services"

            private val DEFAULT_ALLOW_UNKNOWN_PORTS = HiddenServiceAllowUnknownPorts.build {}
            private val DEFAULT_MAX_STREAMS = HiddenServiceMaxStreams.build {}
            private val DEFAULT_MAX_STREAMS_CLOSE_CIRCUIT = HiddenServiceMaxStreamsCloseCircuit.build {}
            private val DEFAULT_DIR_GROUP_READABLE = HiddenServiceDirGroupReadable.build {}
        }
    }

    /**
     * [HiddenServicePort](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServicePort)
     * */
    @KmpTorDsl
    public class HiddenServicePort private constructor() {

        private var targetArgument: String? = null

        // TODO: Check if can be 0
        /**
         * Configures the virtual port that this HiddenServicePort
         * declaration will listen on.
         *
         * e.g.
         *
         *     HiddenServicePort 80
         *     // http://<onion-address>.onion
         *
         *     HiddenServicePort 443
         *     // https://<onion-address>.onion
         *
         *     HiddenServicePort 8080
         *     // http://<onion-address>.onion:8080
         * */
        @JvmField
        public var virtual: Port? = null

        /**
         * Configure the target to use a TCP Port.
         *
         * Only necessary if a value different than [virtual]
         * is required.
         * */
        @KmpTorDsl
        public fun targetAsPort(
            block: ThisBlock<TCPPortBuilder.HiddenService>
        ): HiddenServicePort {
            targetArgument = TCPPortBuilder.HiddenService.build(block)
            return this
        }

        /**
         * Configure the target to use a Unix Socket
         *
         * @throws [UnsupportedOperationException] see [UnixSocketBuilder.DSL]
         * */
        @KmpTorDsl
        @Throws(UnsupportedOperationException::class)
        public fun targetAsUnixSocket(
            block: ThisBlock<UnixSocketBuilder>
        ): HiddenServicePort {
            val path = UnixSocketBuilder.build(block) ?: return this
            targetArgument = path
            return this
        }

        private fun build(): LineItem? {
            val virtual = virtual ?: return null
            val target = targetArgument ?: virtual.toString()
            return toLineItem("$virtual $target")
        }

        public companion object: Keyword(
            name = "HiddenServicePort",
            attributes = immutableSetOf(Attribute.HiddenService, Attribute.Port, Attribute.UnixSocket),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServicePort>
            ): LineItem? = HiddenServicePort().apply(block).build()
        }
    }

    /**
     * Configure the HiddenService version. This **must** be set
     * when configuring a [HiddenServiceDir], even though it is not required
     * by Tor (which will fall back to whatever its default is).
     *
     * If a new version is introduced by Tor, it may cause a conflict for
     * already created hidden services. By explicitly declaring it within
     * your code, this potential issue is mitigated.
     *
     * [HiddenServiceVersion](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceVersion)
     * */
    @KmpTorDsl
    public class HiddenServiceVersion private constructor() {

        private var argument: Byte? = null

        /**
         * Currently, the only supported version is 3
         *
         * All this hoopla is to enforce declaration of a supported [version]
         * for all HiddenServices.
         *
         * @throws [IllegalArgumentException] if [version] is inappropriate
         * */
        @KmpTorDsl
        @Throws(IllegalArgumentException::class)
        public fun HSv(version: Byte): HiddenServiceVersion {
            // NOTE: If adding a version, also update
            // HiddenServiceDir._numIntroductionPoints Map with
            // its default value.
            require(version in 3..3) { "Unsupported HS version of $version" }
            argument = version
            return this
        }

        private fun build(): LineItem? = toLineItem(argument?.toString())

        public companion object: Keyword(
            name = "HiddenServiceVersion",
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceVersion>,
            ): LineItem? = HiddenServiceVersion().apply(block).build()
        }
    }

    /**
     * [HiddenServiceAllowUnknownPorts](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceAllowUnknownPorts)
     * */
    @KmpTorDsl
    public class HiddenServiceAllowUnknownPorts private constructor() {

        @JvmField
        public var allow: Boolean = false

        private fun build(): LineItem = toLineItem(allow.toByte().toString())!!

        public companion object: Keyword(
            name = "HiddenServiceAllowUnknownPorts",
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceAllowUnknownPorts>,
            ): LineItem = HiddenServiceAllowUnknownPorts().apply(block).build()
        }
    }

//    /**
//     * [HiddenServiceExportCircuitID](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceExportCircuitID)
//     * */
//    @KmpTorDsl
//    public class HiddenServiceExportCircuitID private constructor() {
//        // TODO
//    }

    /**
     * [HiddenServiceMaxStreams](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreams)
     * */
    @KmpTorDsl
    public class HiddenServiceMaxStreams private constructor() {

        /**
         * If not between a value of 0 and 65535 (inclusive), the
         * default value of 0 (Unlimited) will be utilized.
         * */
        @JvmField
        public var maximum: Int = 0

        private fun build(): LineItem {
            var maximum = maximum
            if (maximum !in Port.MIN..Port.MAX) maximum = 0
            return toLineItem(maximum.toString())!!
        }

        public companion object: Keyword(
            name = "HiddenServiceMaxStreams",
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceMaxStreams>,
            ): LineItem = HiddenServiceMaxStreams().apply(block).build()
        }
    }

    /**
     * [HiddenServiceMaxStreamsCloseCircuit](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceMaxStreamsCloseCircuit)
     * */
    @KmpTorDsl
    public class HiddenServiceMaxStreamsCloseCircuit private constructor() {

        @JvmField
        public var close: Boolean = false

        private fun build(): LineItem = toLineItem(close.toByte().toString())!!

        public companion object: Keyword(
            name = "HiddenServiceMaxStreamsCloseCircuit",
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceMaxStreamsCloseCircuit>,
            ): LineItem = HiddenServiceMaxStreamsCloseCircuit().apply(block).build()
        }
    }

    /**
     * [HiddenServiceDirGroupReadable](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceDirGroupReadable)
     * */
    @KmpTorDsl
    public class HiddenServiceDirGroupReadable private constructor() {

        @JvmField
        public var readable: Boolean = false

        private fun build(): LineItem = toLineItem(readable.toByte().toString())!!

        public companion object: Keyword(
            name = "HiddenServiceDirGroupReadable",
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceDirGroupReadable>,
            ): LineItem = HiddenServiceDirGroupReadable().apply(block).build()
        }
    }

    /**
     * [HiddenServiceNumIntroductionPoints](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceNumIntroductionPoints)
     * */
    @KmpTorDsl
    public class HiddenServiceNumIntroductionPoints private constructor(
        private val map: MutableMap<String, Int>
    ) {

        // To inhibit modification after closure
        private var isConfigured: Boolean = false

        /**
         * Setting for a Version 3 Hidden Service.
         *
         * If not between a value of 1 and 20 (inclusive), the
         * default value of 3 will be utilized.
         *
         * [HiddenServiceVersion] 3 is currently the only supported
         * version.
         * */
        @KmpTorDsl
        public fun HSv3(points: Int): HiddenServiceNumIntroductionPoints {
            if (isConfigured) return this
            // TODO: Check minimum value
            if (points !in 1..20) return this
            map["3"] = points
            return this
        }

        public companion object: Keyword(
            name = "HiddenServiceNumIntroductionPoints",
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun configure(
                map: MutableMap<String, Int>,
                block: ThisBlock<HiddenServiceNumIntroductionPoints>,
            ) { HiddenServiceNumIntroductionPoints(map).apply(block).isConfigured = true }
        }
    }

    /**
     * [RunAsDaemon](https://2019.www.torproject.org/docs/tor-manual.html.en#RunAsDaemon)
     * */
    @KmpTorDsl
    public class RunAsDaemon private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<RunAsDaemon, Setting>(
            name = "RunAsDaemon",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { RunAsDaemon() },
            build = { build(enable.toByte().toString())!! },
        )
    }

    /**
     * [SyslogIdentityTag](https://2019.www.torproject.org/docs/tor-manual.html.en#SyslogIdentityTag)
     * */
    @KmpTorDsl
    public class SyslogIdentityTag private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        /**
         * Acceptable Tag Constraints:
         *  - Characters: a-z
         *  - Characters: A-Z
         *  - Characters: 0-9
         *  - Character: _
         *  - Character Range: 1 to 19 (inclusive)
         *
         * `null` will be returned if the above constraints
         * are not met.
         * */
        @JvmField
        public var tag: String? = null

        public companion object: Setting.Factory<SyslogIdentityTag, Setting?>(
            name = "SyslogIdentityTag",
            attributes = immutableSetOf(Attribute.Logging),
            isCmdLineArg = true,
            isUnique = true,
            factory = { SyslogIdentityTag() },
            build = {
                tag?.let { tag ->
                    @Suppress("RedundantCompanionReference")
                    if (!tag.matches(Companion.REGEX)) return@let null

                    val others = if (IsAndroidHost) {
                        setOf(AndroidIdentityTag.toLineItem(tag)!!)
                    } else {
                        emptySet()
                    }

                    build(tag, others = others)
                }
            },
        ) {

            // Max length is 23 - "tor-".length
            // Android's max Log tag length is 23 characters
            private val REGEX = "[a-zA-Z0-9_]{1,19}".toRegex()
        }
    }

    /**
     * This is automatically added via [SyslogIdentityTag] as a second
     * [Setting.items] if Android Runtime is observed.
     *
     * [AndroidIdentityTag](https://2019.www.torproject.org/docs/tor-manual.html.en#AndroidIdentityTag)
     * */
    public class AndroidIdentityTag private constructor() {
        public companion object: Keyword(
            name = "AndroidIdentityTag",
            attributes = immutableSetOf(Attribute.Logging),
            isCmdLineArg = true,
            isUnique = true
        )
    }

    /**
     * [VirtualAddrNetworkIPv4](https://2019.www.torproject.org/docs/tor-manual.html.en#VirtualAddrNetworkIPv4)
     * */
    @KmpTorDsl
    public class VirtualAddrNetworkIPv4 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var address: IPAddress.V4 = DEFAULT

        /**
         * If not between a value of 0 and 16 (inclusive), the
         * default value of 10 will be utilized.
         * */
        @JvmField
        public var bits: Byte = 10

        public companion object: Setting.Factory<VirtualAddrNetworkIPv4, Setting>(
            name = "VirtualAddrNetworkIPv4",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { VirtualAddrNetworkIPv4() },
            build = {
                var bits = bits
                if (bits !in 0..16) bits = 10
                build(address.canonicalHostname() + '/' + bits)!!
            },
        ) {
            private val DEFAULT = "127.192.0.0".toIPAddressV4()
        }
    }

    /**
     * [VirtualAddrNetworkIPv6](https://2019.www.torproject.org/docs/tor-manual.html.en#VirtualAddrNetworkIPv6)
     * */
    @KmpTorDsl
    public class VirtualAddrNetworkIPv6 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var address: IPAddress.V6 = DEFAULT

        /**
         * If not between a value of 0 and 104 (inclusive), the
         * default value of 10 will be utilized.
         * */
        @JvmField
        public var bits: Byte = 10

        public companion object: Setting.Factory<VirtualAddrNetworkIPv6, Setting>(
            name = "VirtualAddrNetworkIPv6",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { VirtualAddrNetworkIPv6() },
            build = {
                var bits = bits
                if (bits !in 0..104) bits = 10
                build(address.canonicalHostname() + '/' + bits)!!
            },
        ) {
            private val DEFAULT = "FE80::".toIPAddressV6()
        }
    }

    /**
     * Model of a single configuration line item
     *
     * e.g.
     *
     *     |   keyword   | argument |            optionals             |
     *       __SocksPort     9050     OnionTrafficOnly IsolateDestPort
     * */
    public class LineItem private constructor(
        @JvmField
        public val keyword: Keyword,
        @JvmField
        public val argument: String,
        @JvmField
        public val optionals: Set<String>,
    ) {

        private val isPort = keyword.attributes.contains(Attribute.Port)
        private val isPath = with(keyword.attributes) {
            contains(Attribute.File) || contains(Attribute.Directory)
        }
        private val isPortAutoOrDisabled = if (isPort) {
            argument == "0" || argument == AUTO
        } else {
            false
        }

        public override fun equals(other: Any?): Boolean {
            if (other !is LineItem) return false

            if (keyword.isUnique || other.keyword.isUnique) {
                // If either are unique, compare only the keyword
                return other.keyword == keyword
            }

            // Have to compare 2 non-unique Items

            // If both are ports
            if (other.isPort && isPort) {
                return if (other.isPortAutoOrDisabled || isPortAutoOrDisabled) {
                    other.keyword == keyword && other.argument == argument
                } else {
                    // neither are disabled or set to auto, compare
                    // only their port arguments (or unix socket paths)
                    other.argument == argument
                }
            }

            // if both are file paths
            if (other.isPath && isPath) {
                // compare only by their arguments (file paths)
                return argument == other.argument
            }

            return  other.keyword == keyword
                    && other.argument == argument
        }

        public override fun hashCode(): Int {
            var result = 13
            if (keyword.isUnique) {
                return result * 42 + keyword.hashCode()
            }

            if (isPortAutoOrDisabled) {
                result = result * 42 + keyword.hashCode()
                return result * 42 + argument.hashCode()
            }

            if (isPort || isPath) {
                return result * 42 + argument.hashCode()
            }

            result = result * 42 + keyword.hashCode()
            return result * 42 + argument.hashCode()
        }

        public override fun toString(): String = buildString {
            append(keyword)
            append(' ')
            append(argument)
            if (optionals.isNotEmpty()) {
                append(' ')
                optionals.joinTo(this, separator = " ")
            }
        }

        internal companion object {

            @JvmSynthetic
            internal fun Keyword.toLineItem(
                argument: String?,
                optionals: Set<String> = emptySet(),
            ): LineItem? {
                if (argument.isNullOrBlank()) return null
                if (argument.lines().size != 1) return null

                optionals.forEach {
                    if (it.isBlank()) return null
                    if (it.lines().size != 1) return null
                }

                return LineItem(this, argument, optionals.toImmutableSet())
            }
        }
    }

    /**
     * Model for a group of [LineItem]s belonging to a single [Setting].
     *
     * Only the "root" (the first [LineItem]) is considered when computing
     * the [equals] and [hashCode] values.
     *
     * Most configuration items are only 1 [LineItem], but there are some
     * that must be expressed in a single "group" such as [HiddenServiceDir].
     * */
    public class Setting private constructor(

        /**
         * Will **always** contain at least 1 argument
         * */
        @JvmField
        public val items: Set<LineItem>,

        /**
         * Any information to be excluded from the configuration for
         * this setting, but may be required elsewhere when referencing
         * it via its [keyword].
         *
         * e.g. The un-hashed password for HashedControlPassword to
         *      establish a control connection.
         *
         * @see [get]
         * */
        private val extras: Map<Extra<*>, Any>,
    ) {

        public operator fun <T: Any> get(key: Extra<T>): T? {
            val value = extras[key] ?: return null
            @Suppress("UNCHECKED_CAST")
            return value as? T
        }

        @get:JvmName("keyword")
        public val keyword: Keyword get() = items.first().keyword
        @get:JvmName("argument")
        public val argument: String get() = items.first().argument
        @get:JvmName("optionals")
        public val optionals: Set<String> get() = items.first().optionals

        @KmpTorDsl
        public sealed class Builder(
            @JvmField
            public val keyword: Keyword,
        ) {

            // Returns null when:
            //  - argument is null, blank, or multi-line
            //  - optionals contains a blank or multi-line value
            protected fun build(
                argument: String?,
                optionals: Set<String> = emptySet(),
                others: Set<LineItem> = emptySet(),
                extras: Map<Extra<*>, Any> = emptyMap(),
            ): Setting? {
                val first = keyword.toLineItem(argument, optionals) ?: return null

                val set = LinkedHashSet<LineItem>(1 + others.size, 1.0F)
                set.add(first)
                set.addAll(others)

                return Setting(set.toImmutableSet(), extras.toImmutableMap())
            }
        }

        /**
         * Factory function provider for [TorConfig] subclasses to utilize
         * on their companion objects.
         *
         * e.g. (Kotlin)
         *
         *     val setting = TorConfig.RunAsDaemon.Builder { enable = true }
         *
         * e.g. (Java)
         *
         *     TorSetting setting = TorConfig.RunAsDaemon.Companion.Builder(b -> {
         *         b.enable = true;
         *     });
         * */
        public sealed class Factory<out B: Builder, out S: Setting?>(
            name: String,
            attributes: Set<Attribute>,
            isCmdLineArg: Boolean,
            isUnique: Boolean,
            private val factory: () -> B,
            private val build: B.() -> S,
        ): Keyword(name, attributes, isCmdLineArg, isUnique) {

            public fun Builder(block: ThisBlock<B>): S = build(factory().apply(block))
        }

        public companion object {

            /**
             * Iterates through all [Setting.items], returning all settings which contain
             * the specified [Keyword].
             * */
            @JvmStatic
            public inline fun <reified T: Keyword> Iterable<Setting>.filterByKeyword(): List<Setting> {
                return filter { setting ->
                    setting.items.forEach { item ->
                        if (item.keyword is T) return@filter true
                    }
                    false
                }
            }

            /**
             * Iterates through all [Setting.items], returning all settings which contain
             * the specified [Attribute].
             * */
            @JvmStatic
            public inline fun <reified T: Attribute> Iterable<Setting>.filterByAttribute(): List<Setting> {
                return filter { setting ->
                    setting.items.forEach forEachItem@ { item ->
                        item.keyword.attributes.forEach forEachAttr@ { attr ->
                            if (attr !is T) return@forEachAttr
                            // Found specified attribute T

                            if (attr is Attribute.Port) {
                                if (item.isArgumentAUnixSocket()) {
                                    return@forEachItem
                                }
                            }

                            if (attr is Attribute.UnixSocket) {
                                if (item.isArgumentAPort()) {
                                    return@forEachItem
                                }
                            }

                            // T is not Port or UnixSocket
                            return@filter true
                        }
                    }
                    false
                }
            }

            @PublishedApi
            @JvmSynthetic
            internal fun LineItem.isArgumentAPort(): Boolean {
                if (!keyword.attributes.contains(Attribute.Port)) return false
                // If not a unix socket configurable Port keyword, then it MUST be
                // configured as a port.
                //
                // If potentially a unix socket, see if the argument is set to one
                return !isArgumentAUnixSocket()
            }

            @PublishedApi
            @JvmSynthetic
            internal fun LineItem.isArgumentAUnixSocket(): Boolean {
                if (!keyword.attributes.contains(Attribute.UnixSocket)) return false

                return if (keyword is HiddenServicePort.Companion) {
                    // Check the target, not the virtual port
                    argument.substringAfter(' ')
                } else {
                    argument
                }.startsWith("unix:")
            }
        }

        public override fun equals(other: Any?): Boolean = other is Setting && other.items.first() == items.first()
        public override fun hashCode(): Int = 17 * 31 + items.first().hashCode()
        public override fun toString(): String = buildString {
            items.joinTo(this, separator = "\n")
        }

        // Returns a new Setting if it is a *Port that is
        // not configured as disabled or auto.
        @InternalKmpTorApi
        public fun reassignTCPPortAutoOrNull(): Setting? {
            // Setting does not have Attribute.Port
            if (!keyword.attributes.contains(Attribute.Port)) return null
            if (this[Extra.AllowReassign] != true) return null

            // Remove and replace argument with auto
            val list = items.toMutableList()
            val removed = list.removeAt(0)

            // TODO: Issue #313
            //  Will need to figure out how to handle a non-localhost IPAddress
            list.add(0, removed.keyword.toLineItem(AUTO, removed.optionals)!!)

            val extras = extras.toMutableMap()
            extras.remove(Extra.AllowReassign)

            return Setting(list.toImmutableSet(), extras.toImmutableMap())
        }
    }

    /**
     * Base abstraction for modeling of Keywords utilized by tor, either
     * by its configuration file or its control port.
     *
     * This is only utilized by builders on their companion objects such
     * that a singleton [Keyword] is available.
     *
     * e.g.
     *
     *     assertIs<TorConfig.KeyWord>(TorConfig.__DNSPort)
     *     assertIs<TorConfig.KeyWord>(TorConfig.__DNSPort.Companion)
     *
     * [Docs](https://2019.www.torproject.org/docs/tor-manual.html.en)
     *
     * @see [Setting.Factory]
     * @see [Setting.filterByKeyword]
     * @see [Setting.filterByAttribute]
     *
     * @param [name] The string value (e.g. `RunAsDaemon`)
     * @param [attributes] Attributes specific to the Keyword (used for filtering)
     * @param [isCmdLineArg] KmpTor specific flag used to determine
     *   if the argument should be utilized in the start arguments passed
     *   to tor's main function, or loaded via the control connection after
     *   initial startup of the tor daemon.
     * @param [isUnique] If the Keyword can be included multiple times
     *   within a configuration.
     * */
    public sealed class Keyword(
        @JvmField
        public val name: String,
        @JvmField
        public val attributes: Set<Attribute>,
        @JvmField
        public val isCmdLineArg: Boolean,
        @JvmField
        public val isUnique: Boolean,
    ): Comparable<Keyword>, CharSequence {

        public sealed class Attribute private constructor() {
            public data object Directory: Attribute()
            public data object File: Attribute()
            public data object HiddenService: Attribute()
            public data object Logging: Attribute()
            public data object Port: Attribute()
            public data object UnixSocket: Attribute()
        }

        public final override val length: Int get() = name.length
        public final override fun get(index: Int): Char = name[index]
        public final override fun subSequence(
            startIndex: Int,
            endIndex: Int
        ): CharSequence = name.subSequence(startIndex, endIndex)

        public final override fun compareTo(other: Keyword): Int = name.compareTo(other.name)
        public operator fun plus(other: Any?): String = name + other

        public final override fun equals(other: Any?): Boolean = other is Keyword && other.name == name
        public final override fun hashCode(): Int = 21 * 31 + name.hashCode()
        public final override fun toString(): String = name
    }

    /**
     * A key for extra information about a [Setting] that is
     * excluded from the tor configuration, but may be utilized
     * elsewhere in `kmp-tor`.
     * */
    public sealed class Extra<T: Any> {

        /**
         * An [Extra] which signals that if a TCP port is not available,
         * it can be reassigned to "auto", or one that **is** available.
         *
         * Will only be added as an extra to the [Setting] if the builder
         * argument is a port value and reassignable was true.
         * */
        public data object AllowReassign: Extra<Boolean>() {

            @JvmSynthetic
            internal fun create(argument: String, allow: Boolean): Map<Extra<*>, Any> {
                if (!allow) return emptyMap()
                if (argument == "0") return emptyMap()
                if (argument == AUTO) return emptyMap()
                if (argument.startsWith("unix:")) return emptyMap()
                return mapOf(Pair(AllowReassign, allow))
            }
        }
    }

    public override fun equals(other: Any?): Boolean = other is TorConfig && other.settings == settings
    public override fun hashCode(): Int = 5 * 42 + settings.hashCode()
    public override fun toString(): String = buildString {
        settings.joinTo(this, separator = "\n")
    }
}
