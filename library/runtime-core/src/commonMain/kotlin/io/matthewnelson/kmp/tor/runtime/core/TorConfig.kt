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
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
import io.matthewnelson.kmp.tor.runtime.core.internal.IsAndroidHost
import io.matthewnelson.kmp.tor.runtime.core.internal.IsUnixLikeHost
import io.matthewnelson.kmp.tor.runtime.core.internal.toByte
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.math.pow
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Holder for a configuration.
 *
 * **NOTE:** All [Keyword] as defined in the
 * [tor-manual](https://2019.www.torproject.org/docs/tor-manual.html.en)
 * are declared within [TorConfig] as subclasses. Many of them do not
 * have their builders implemented, but are available for use with
 * [TorCmd.Config.Get] queries.
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
     *                 port(9050.toPortEphemeral())
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
     *                 p.port(Port.Ephemeral.get(9050));
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

            override fun cookieAuthentication(): Setting? {
                return settings.filterByKeyword<CookieAuthentication.Companion>()
                    .firstOrNull()
            }

            override fun cookieAuthFile(): Setting? {
                return settings.filterByKeyword<CookieAuthFile.Companion>()
                    .firstOrNull()
            }

            override fun dataDirectory(): Setting? {
                return settings.filterByKeyword<DataDirectory.Companion>()
                    .firstOrNull()
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
            default = "0",
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

        private var argument: String = default
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
        public override fun port(port: Port.Ephemeral): __DNSPort {
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
            default = "0",
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

        private var argument: String = default
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
        public override fun port(port: Port.Ephemeral): __HTTPTunnelPort {
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
            default = "0",
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
            default = "",
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

        private var argument: String = default
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
            default = "9050",
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

        private var port: String = default
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
        public override fun port(port: Port.Ephemeral): __TransPort {
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
            default = "0",
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
            default = false.toByte().toString(),
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
            default = ".onion,.exit",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { AutomapHostsSuffixes() },
            build = {
                var result = suffixes.joinToString(separator = ",")
                if (result.isBlank()) result = Companion.default
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
            default = "",
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
            default = "",
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

        private var argument: String = default

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
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { ConnectionPadding() },
            build = { build(argument)!! },
        )
    }

    /**
     * [ReducedConnectionPadding](https://2019.www.torproject.org/docs/tor-manual.html.en#ReducedConnectionPadding)
     * */
    @KmpTorDsl
    public class ReducedConnectionPadding private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<ReducedConnectionPadding, Setting>(
            name = "ReducedConnectionPadding",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { ReducedConnectionPadding() },
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
            default = "",
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
            default = false.toByte().toString(),
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
            default = "",
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
            default = "",
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
            default = false.toByte().toString(),
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
            default = false.toByte().toString(),
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
            default = 24.hours.inWholeSeconds.toString(),
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
            default = false.toByte().toString(),
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
            default = true.toByte().toString(),
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

        private var argument: String = default

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
            default = AUTO,
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
            default = "",
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
            default = "",
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
            default = "",
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
            default = "",
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
            default = "3",
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
            default = false.toByte().toString(),
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
            default = "0",
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
            default = false.toByte().toString(),
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
            default = false.toByte().toString(),
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
            default = "3",
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
            default = false.toByte().toString(),
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
            default = "",
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
            default = "",
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
            default = "127.192.0.0/10",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { VirtualAddrNetworkIPv4() },
            build = {
                var bits = bits
                if (bits !in 0..16) bits = 10
                build(address.canonicalHostName() + '/' + bits)!!
            },
        ) {
            private val DEFAULT by lazy { default.toIPAddressV4() }
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
            default = "[FE80::]/10",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { VirtualAddrNetworkIPv6() },
            build = {
                var bits = bits
                if (bits !in 0..104) bits = 10
                build(address.canonicalHostName() + '/' + bits)!!
            },
        ) {
            private val DEFAULT by lazy { default.toIPAddressV6() }
        }
    }

    ////////////////////////////////////////
    ////////////////////////////////////////
    ////                                ////
    ////    Not implemented settings    ////
    ////                                ////
    ////////////////////////////////////////
    ////////////////////////////////////////

    /**
     * See ephemeral setting [__ControlPort].
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ControlPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __ControlPort.name.drop(2),
            default = __ControlPort.default,
            attributes = __ControlPort.attributes,
            isCmdLineArg = __ControlPort.isCmdLineArg,
            isUnique = __ControlPort.isUnique,
        )
    }

    /**
     * See ephemeral setting [__DNSPort].
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DNSPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __DNSPort.name.drop(2),
            default = __DNSPort.default,
            attributes = __DNSPort.attributes,
            isCmdLineArg = __DNSPort.isCmdLineArg,
            isUnique = __DNSPort.isUnique,
        )
    }

    /**
     * See ephemeral setting [__HTTPTunnelPort].
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HTTPTunnelPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __HTTPTunnelPort.name.drop(2),
            default = __HTTPTunnelPort.default,
            attributes = __HTTPTunnelPort.attributes,
            isCmdLineArg = __HTTPTunnelPort.isCmdLineArg,
            isUnique = __HTTPTunnelPort.isUnique,
        )
    }

    /**
     * See ephemeral setting [__SocksPort].
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class SocksPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __SocksPort.name.drop(2),
            default = __SocksPort.default,
            attributes = __SocksPort.attributes,
            isCmdLineArg = __SocksPort.isCmdLineArg,
            isUnique = __SocksPort.isUnique,
        )
    }

    /**
     * See ephemeral setting [__TransPort].
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TransPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __TransPort.name.drop(2),
            default = __TransPort.default,
            attributes = __TransPort.attributes,
            isCmdLineArg = __TransPort.isCmdLineArg,
            isUnique = __TransPort.isUnique,
        )
    }

    ///////////////////////
    //  GENERAL OPTIONS  //
    ///////////////////////

    /**
     * [BandwidthRate](https://2019.www.torproject.org/docs/tor-manual.html.en#BandwidthRate)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class BandwidthRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BandwidthRate",
            default = (2.0F.pow(30)).toInt().toString(), // 1 GByte
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [BandwidthBurst](https://2019.www.torproject.org/docs/tor-manual.html.en#BandwidthBurst)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class BandwidthBurst private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BandwidthBurst",
            default = (2.0F.pow(30)).toInt().toString(), // 1 GByte
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MaxAdvertisedBandwidth](https://2019.www.torproject.org/docs/tor-manual.html.en#MaxAdvertisedBandwidth)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MaxAdvertisedBandwidth private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MaxAdvertisedBandwidth",
            default = (2.0F.pow(30)).toInt().toString(), // 1 GByte
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RelayBandwidthRate](https://2019.www.torproject.org/docs/tor-manual.html.en#RelayBandwidthRate)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RelayBandwidthRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RelayBandwidthRate",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RelayBandwidthBurst](https://2019.www.torproject.org/docs/tor-manual.html.en#RelayBandwidthBurst)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RelayBandwidthBurst private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RelayBandwidthBurst",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PerConnBWRate](https://2019.www.torproject.org/docs/tor-manual.html.en#PerConnBWRate)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PerConnBWRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PerConnBWRate",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PerConnBWBurst](https://2019.www.torproject.org/docs/tor-manual.html.en#PerConnBWBurst)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PerConnBWBurst private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PerConnBWBurst",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientTransportPlugin](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientTransportPlugin)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientTransportPlugin private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientTransportPlugin",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerTransportPlugin](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerTransportPlugin)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerTransportPlugin private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerTransportPlugin",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerTransportListenAddr](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerTransportListenAddr)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerTransportListenAddr private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerTransportListenAddr",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerTransportOptions](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerTransportOptions)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerTransportOptions private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerTransportOptions",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExtORPort](https://2019.www.torproject.org/docs/tor-manual.html.en#ExtORPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExtORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtORPort",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExtORPortCookieAuthFile](https://2019.www.torproject.org/docs/tor-manual.html.en#ExtORPortCookieAuthFile)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExtORPortCookieAuthFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtORPortCookieAuthFile",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExtORPortCookieAuthFileGroupReadable](https://2019.www.torproject.org/docs/tor-manual.html.en#ExtORPortCookieAuthFileGroupReadable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExtORPortCookieAuthFileGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtORPortCookieAuthFileGroupReadable",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ConnLimit](https://2019.www.torproject.org/docs/tor-manual.html.en#ConnLimit)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ConnLimit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConnLimit",
            default = "1000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ConstrainedSockets](https://2019.www.torproject.org/docs/tor-manual.html.en#ConstrainedSockets)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ConstrainedSockets private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConstrainedSockets",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ConstrainedSockSize](https://2019.www.torproject.org/docs/tor-manual.html.en#ConstrainedSockSize)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ConstrainedSockSize private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConstrainedSockSize",
            default = (2.0F.pow(13)).toInt().toString(), // 8192 bytes
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ControlSocket](https://2019.www.torproject.org/docs/tor-manual.html.en#ControlSocket)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ControlSocket private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ControlSocket",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ControlSocketsGroupWritable](https://2019.www.torproject.org/docs/tor-manual.html.en#ControlSocketsGroupWritable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ControlSocketsGroupWritable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ControlSocketsGroupWritable",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HashedControlPassword](https://2019.www.torproject.org/docs/tor-manual.html.en#HashedControlPassword)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HashedControlPassword private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HashedControlPassword",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CookieAuthFileGroupReadable](https://2019.www.torproject.org/docs/tor-manual.html.en#CookieAuthFileGroupReadable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CookieAuthFileGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CookieAuthFileGroupReadable",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ControlPortFileGroupReadable](https://2019.www.torproject.org/docs/tor-manual.html.en#ControlPortFileGroupReadable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ControlPortFileGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ControlPortFileGroupReadable",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DataDirectoryGroupReadable](https://2019.www.torproject.org/docs/tor-manual.html.en#DataDirectoryGroupReadable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DataDirectoryGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DataDirectoryGroupReadable",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CacheDirectoryGroupReadable](https://2019.www.torproject.org/docs/tor-manual.html.en#CacheDirectoryGroupReadable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CacheDirectoryGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CacheDirectoryGroupReadable",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [FallbackDir](https://2019.www.torproject.org/docs/tor-manual.html.en#FallbackDir)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class FallbackDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FallbackDir",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [UseDefaultFallbackDirs](https://2019.www.torproject.org/docs/tor-manual.html.en#UseDefaultFallbackDirs)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class UseDefaultFallbackDirs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseDefaultFallbackDirs",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DirAuthority](https://2019.www.torproject.org/docs/tor-manual.html.en#DirAuthority)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirAuthority private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirAuthority",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DirAuthorityFallbackRate](https://2019.www.torproject.org/docs/tor-manual.html.en#DirAuthorityFallbackRate)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirAuthorityFallbackRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirAuthorityFallbackRate",
            default = "0.100000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AlternateBridgeAuthority](https://2019.www.torproject.org/docs/tor-manual.html.en#AlternateBridgeAuthority)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AlternateBridgeAuthority private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AlternateBridgeAuthority",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DisableAllSwap](https://2019.www.torproject.org/docs/tor-manual.html.en#DisableAllSwap)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DisableAllSwap private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DisableAllSwap",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DisableDebuggerAttachment](https://2019.www.torproject.org/docs/tor-manual.html.en#DisableDebuggerAttachment)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DisableDebuggerAttachment private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DisableDebuggerAttachment",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [FetchDirInfoEarly](https://2019.www.torproject.org/docs/tor-manual.html.en#FetchDirInfoEarly)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class FetchDirInfoEarly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchDirInfoEarly",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [FetchDirInfoExtraEarly](https://2019.www.torproject.org/docs/tor-manual.html.en#FetchDirInfoExtraEarly)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class FetchDirInfoExtraEarly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchDirInfoExtraEarly",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [FetchHidServDescriptors](https://2019.www.torproject.org/docs/tor-manual.html.en#FetchHidServDescriptors)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class FetchHidServDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchHidServDescriptors",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [FetchServerDescriptors](https://2019.www.torproject.org/docs/tor-manual.html.en#FetchServerDescriptors)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class FetchServerDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchServerDescriptors",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [FetchUselessDescriptors](https://2019.www.torproject.org/docs/tor-manual.html.en#FetchUselessDescriptors)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class FetchUselessDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchUselessDescriptors",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HTTPSProxy](https://2019.www.torproject.org/docs/tor-manual.html.en#HTTPSProxy)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HTTPSProxy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HTTPSProxy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HTTPSProxyAuthenticator](https://2019.www.torproject.org/docs/tor-manual.html.en#HTTPSProxyAuthenticator)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HTTPSProxyAuthenticator private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HTTPSProxyAuthenticator",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [Sandbox](https://2019.www.torproject.org/docs/tor-manual.html.en#Sandbox)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Sandbox private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Sandbox",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [Socks4Proxy](https://2019.www.torproject.org/docs/tor-manual.html.en#Socks4Proxy)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Socks4Proxy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Socks4Proxy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [Socks5Proxy](https://2019.www.torproject.org/docs/tor-manual.html.en#Socks5Proxy)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Socks5Proxy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Socks5Proxy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [Socks5ProxyPassword](https://2019.www.torproject.org/docs/tor-manual.html.en#Socks5ProxyPassword)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Socks5ProxyPassword private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Socks5ProxyPassword",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [UnixSocksGroupWritable](https://2019.www.torproject.org/docs/tor-manual.html.en#UnixSocksGroupWritable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class UnixSocksGroupWritable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UnixSocksGroupWritable",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [KeepalivePeriod](https://2019.www.torproject.org/docs/tor-manual.html.en#KeepalivePeriod)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class KeepalivePeriod private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KeepalivePeriod",
            default = 5.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [Log](https://2019.www.torproject.org/docs/tor-manual.html.en#Log)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Log private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Log",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [LogMessageDomains](https://2019.www.torproject.org/docs/tor-manual.html.en#LogMessageDomains)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class LogMessageDomains private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "LogMessageDomains",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MaxUnparseableDescSizeToLog](https://2019.www.torproject.org/docs/tor-manual.html.en#MaxUnparseableDescSizeToLog)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MaxUnparseableDescSizeToLog private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MaxUnparseableDescSizeToLog",
            default = (2.0F.pow(20) * 10).toInt().toString(), // 10 MB
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [OutboundBindAddress](https://2019.www.torproject.org/docs/tor-manual.html.en#OutboundBindAddress)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class OutboundBindAddress private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "OutboundBindAddress",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [OutboundBindAddressOR](https://2019.www.torproject.org/docs/tor-manual.html.en#OutboundBindAddressOR)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class OutboundBindAddressOR private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "OutboundBindAddressOR",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [OutboundBindAddressExit](https://2019.www.torproject.org/docs/tor-manual.html.en#OutboundBindAddressExit)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class OutboundBindAddressExit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "OutboundBindAddressExit",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PidFile](https://2019.www.torproject.org/docs/tor-manual.html.en#PidFile)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PidFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PidFile",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ProtocolWarnings](https://2019.www.torproject.org/docs/tor-manual.html.en#ProtocolWarnings)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ProtocolWarnings private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ProtocolWarnings",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [LogTimeGranularity](https://2019.www.torproject.org/docs/tor-manual.html.en#LogTimeGranularity)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class LogTimeGranularity private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "LogTimeGranularity",
            default = 1.seconds.inWholeMilliseconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TruncateLogFile](https://2019.www.torproject.org/docs/tor-manual.html.en#TruncateLogFile)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TruncateLogFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TruncateLogFile",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [SafeLogging](https://2019.www.torproject.org/docs/tor-manual.html.en#SafeLogging)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class SafeLogging private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SafeLogging",
            default = "1",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [User](https://2019.www.torproject.org/docs/tor-manual.html.en#User)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class User private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "User",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [KeepBindCapabilities](https://2019.www.torproject.org/docs/tor-manual.html.en#KeepBindCapabilities)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class KeepBindCapabilities private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KeepBindCapabilities",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HardwareAccel](https://2019.www.torproject.org/docs/tor-manual.html.en#HardwareAccel)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HardwareAccel private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HardwareAccel",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AccelName](https://2019.www.torproject.org/docs/tor-manual.html.en#AccelName)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AccelName private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AccelName",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AccelDir](https://2019.www.torproject.org/docs/tor-manual.html.en#AccelDir)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AccelDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AccelDir",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AvoidDiskWrites](https://2019.www.torproject.org/docs/tor-manual.html.en#AvoidDiskWrites)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AvoidDiskWrites private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AvoidDiskWrites",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CircuitPriorityHalflife](https://2019.www.torproject.org/docs/tor-manual.html.en#CircuitPriorityHalflife)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CircuitPriorityHalflife private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CircuitPriorityHalflife",
            default = "-1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CountPrivateBandwidth](https://2019.www.torproject.org/docs/tor-manual.html.en#CountPrivateBandwidth)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CountPrivateBandwidth private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CountPrivateBandwidth",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExtendByEd25519ID](https://2019.www.torproject.org/docs/tor-manual.html.en#ExtendByEd25519ID)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExtendByEd25519ID private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtendByEd25519ID",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NoExec](https://2019.www.torproject.org/docs/tor-manual.html.en#NoExec)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class NoExec private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NoExec",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [Schedulers](https://2019.www.torproject.org/docs/tor-manual.html.en#Schedulers)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Schedulers private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Schedulers",
            default = "KIST,KISTLite,Vanilla",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [KISTSchedRunInterval](https://2019.www.torproject.org/docs/tor-manual.html.en#KISTSchedRunInterval)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class KISTSchedRunInterval private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KISTSchedRunInterval",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [KISTSockBufSizeFactor](https://2019.www.torproject.org/docs/tor-manual.html.en#KISTSockBufSizeFactor)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class KISTSockBufSizeFactor private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KISTSockBufSizeFactor",
            default = "1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    //////////////////////
    //  CLIENT OPTIONS  //
    //////////////////////

    /**
     * [Bridge](https://2019.www.torproject.org/docs/tor-manual.html.en#Bridge)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Bridge private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Bridge",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [LearnCircuitBuildTimeout](https://2019.www.torproject.org/docs/tor-manual.html.en#LearnCircuitBuildTimeout)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class LearnCircuitBuildTimeout private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "LearnCircuitBuildTimeout",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CircuitBuildTimeout](https://2019.www.torproject.org/docs/tor-manual.html.en#CircuitBuildTimeout)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CircuitBuildTimeout private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CircuitBuildTimeout",
            default = 60.seconds.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CircuitsAvailableTimeout](https://2019.www.torproject.org/docs/tor-manual.html.en#CircuitsAvailableTimeout)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CircuitsAvailableTimeout private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CircuitsAvailableTimeout",
            default = 30.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CircuitStreamTimeout](https://2019.www.torproject.org/docs/tor-manual.html.en#CircuitStreamTimeout)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CircuitStreamTimeout private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CircuitStreamTimeout",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientOnly](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientOnly)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientOnly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientOnly",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExcludeNodes](https://2019.www.torproject.org/docs/tor-manual.html.en#ExcludeNodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExcludeNodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExcludeNodes",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExcludeExitNodes](https://2019.www.torproject.org/docs/tor-manual.html.en#ExcludeExitNodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExcludeExitNodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExcludeExitNodes",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExitNodes](https://2019.www.torproject.org/docs/tor-manual.html.en#ExitNodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExitNodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitNodes",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MiddleNodes](https://2019.www.torproject.org/docs/tor-manual.html.en#MiddleNodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MiddleNodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MiddleNodes",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [EntryNodes](https://2019.www.torproject.org/docs/tor-manual.html.en#EntryNodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class EntryNodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "EntryNodes",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [StrictNodes](https://2019.www.torproject.org/docs/tor-manual.html.en#StrictNodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class StrictNodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "StrictNodes",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [FascistFirewall](https://2019.www.torproject.org/docs/tor-manual.html.en#FascistFirewall)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class FascistFirewall private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FascistFirewall",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ReachableAddresses](https://2019.www.torproject.org/docs/tor-manual.html.en#ReachableAddresses)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ReachableAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ReachableAddresses",
            default = "accept *:*",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ReachableORAddresses](https://2019.www.torproject.org/docs/tor-manual.html.en#ReachableORAddresses)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ReachableORAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ReachableORAddresses",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HidServAuth](https://2019.www.torproject.org/docs/tor-manual.html.en#HidServAuth)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HidServAuth private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HidServAuth",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [LongLivedPorts](https://2019.www.torproject.org/docs/tor-manual.html.en#LongLivedPorts)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class LongLivedPorts private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "LongLivedPorts",
            default = "21,22,706,1863,5050,5190,5222,5223,6523,6667,6697,8300",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MapAddress](https://2019.www.torproject.org/docs/tor-manual.html.en#MapAddress)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MapAddress private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MapAddress",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NewCircuitPeriod](https://2019.www.torproject.org/docs/tor-manual.html.en#NewCircuitPeriod)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class NewCircuitPeriod private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NewCircuitPeriod",
            default = "30",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MaxCircuitDirtiness](https://2019.www.torproject.org/docs/tor-manual.html.en#MaxCircuitDirtiness)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MaxCircuitDirtiness private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MaxCircuitDirtiness",
            default = 10.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MaxClientCircuitsPending](https://2019.www.torproject.org/docs/tor-manual.html.en#MaxClientCircuitsPending)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MaxClientCircuitsPending private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MaxClientCircuitsPending",
            default = "32",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NodeFamily](https://2019.www.torproject.org/docs/tor-manual.html.en#NodeFamily)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class NodeFamily private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NodeFamily",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [EnforceDistinctSubnets](https://2019.www.torproject.org/docs/tor-manual.html.en#EnforceDistinctSubnets)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class EnforceDistinctSubnets private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "EnforceDistinctSubnets",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [SocksPolicy](https://2019.www.torproject.org/docs/tor-manual.html.en#SocksPolicy)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class SocksPolicy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SocksPolicy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [SocksTimeout](https://2019.www.torproject.org/docs/tor-manual.html.en#SocksTimeout)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class SocksTimeout private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SocksTimeout",
            default = 2.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TokenBucketRefillInterval](https://2019.www.torproject.org/docs/tor-manual.html.en#TokenBucketRefillInterval)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TokenBucketRefillInterval private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TokenBucketRefillInterval",
            default = 100.milliseconds.inWholeMilliseconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TrackHostExits](https://2019.www.torproject.org/docs/tor-manual.html.en#TrackHostExits)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TrackHostExits private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TrackHostExits",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TrackHostExitsExpire](https://2019.www.torproject.org/docs/tor-manual.html.en#TrackHostExitsExpire)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TrackHostExitsExpire private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TrackHostExitsExpire",
            default = 30.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [UpdateBridgesFromAuthority](https://2019.www.torproject.org/docs/tor-manual.html.en#UpdateBridgesFromAuthority)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class UpdateBridgesFromAuthority private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UpdateBridgesFromAuthority",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [UseBridges](https://2019.www.torproject.org/docs/tor-manual.html.en#UseBridges)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class UseBridges private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseBridges",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [UseEntryGuards](https://2019.www.torproject.org/docs/tor-manual.html.en#UseEntryGuards)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class UseEntryGuards private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseEntryGuards",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [GuardfractionFile](https://2019.www.torproject.org/docs/tor-manual.html.en#GuardfractionFile)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class GuardfractionFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "GuardfractionFile",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [UseGuardFraction](https://2019.www.torproject.org/docs/tor-manual.html.en#UseGuardFraction)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class UseGuardFraction private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseGuardFraction",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NumEntryGuards](https://2019.www.torproject.org/docs/tor-manual.html.en#NumEntryGuards)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class NumEntryGuards private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NumEntryGuards",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NumPrimaryGuards](https://2019.www.torproject.org/docs/tor-manual.html.en#NumPrimaryGuards)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class NumPrimaryGuards private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NumPrimaryGuards",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NumDirectoryGuards](https://2019.www.torproject.org/docs/tor-manual.html.en#NumDirectoryGuards)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class NumDirectoryGuards private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NumDirectoryGuards",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [GuardLifetime](https://2019.www.torproject.org/docs/tor-manual.html.en#GuardLifetime)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class GuardLifetime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "GuardLifetime",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [SafeSocks](https://2019.www.torproject.org/docs/tor-manual.html.en#SafeSocks)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class SafeSocks private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SafeSocks",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestSocks](https://2019.www.torproject.org/docs/tor-manual.html.en#TestSocks)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestSocks private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestSocks",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AllowNonRFC953Hostnames](https://2019.www.torproject.org/docs/tor-manual.html.en#AllowNonRFC953Hostnames)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AllowNonRFC953Hostnames private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AllowNonRFC953Hostnames",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TransProxyType](https://2019.www.torproject.org/docs/tor-manual.html.en#TransProxyType)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TransProxyType private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TransProxyType",
            default = "default",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientDNSRejectInternalAddresses](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientDNSRejectInternalAddresses)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientDNSRejectInternalAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientDNSRejectInternalAddresses",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientRejectInternalAddresses](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientRejectInternalAddresses)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientRejectInternalAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientRejectInternalAddresses",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DownloadExtraInfo](https://2019.www.torproject.org/docs/tor-manual.html.en#DownloadExtraInfo)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DownloadExtraInfo private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DownloadExtraInfo",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [WarnPlaintextPorts](https://2019.www.torproject.org/docs/tor-manual.html.en#WarnPlaintextPorts)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class WarnPlaintextPorts private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "WarnPlaintextPorts",
            default = "23,109,110,143",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RejectPlaintextPorts](https://2019.www.torproject.org/docs/tor-manual.html.en#RejectPlaintextPorts)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RejectPlaintextPorts private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RejectPlaintextPorts",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [OptimisticData](https://2019.www.torproject.org/docs/tor-manual.html.en#OptimisticData)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class OptimisticData private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "OptimisticData",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HSLayer2Nodes](https://2019.www.torproject.org/docs/tor-manual.html.en#HSLayer2Nodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HSLayer2Nodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HSLayer2Nodes",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HSLayer3Nodes](https://2019.www.torproject.org/docs/tor-manual.html.en#HSLayer3Nodes)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HSLayer3Nodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HSLayer3Nodes",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [UseMicrodescriptors](https://2019.www.torproject.org/docs/tor-manual.html.en#UseMicrodescriptors)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class UseMicrodescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseMicrodescriptors",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PathBiasScaleThreshold](https://2019.www.torproject.org/docs/tor-manual.html.en#PathBiasScaleThreshold)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PathBiasScaleThreshold private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasScaleThreshold",
            default = "-1",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PathBiasScaleUseThreshold](https://2019.www.torproject.org/docs/tor-manual.html.en#PathBiasScaleUseThreshold)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PathBiasScaleUseThreshold private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasScaleUseThreshold",
            default = "-1",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientUseIPv4](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientUseIPv4)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientUseIPv4 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientUseIPv4",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientUseIPv6](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientUseIPv6)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientUseIPv6 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientUseIPv6",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientPreferIPv6ORPort](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientPreferIPv6ORPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientPreferIPv6ORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientPreferIPv6ORPort",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientAutoIPv6ORPort](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientAutoIPv6ORPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientAutoIPv6ORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientAutoIPv6ORPort",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PathsNeededToBuildCircuits](https://2019.www.torproject.org/docs/tor-manual.html.en#PathsNeededToBuildCircuits)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PathsNeededToBuildCircuits private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathsNeededToBuildCircuits",
            default = "-1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientBootstrapConsensusAuthorityDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientBootstrapConsensusAuthorityDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientBootstrapConsensusAuthorityDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientBootstrapConsensusAuthorityDownloadInitialDelay",
            default = "6",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientBootstrapConsensusFallbackDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientBootstrapConsensusFallbackDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientBootstrapConsensusFallbackDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientBootstrapConsensusFallbackDownloadInitialDelay",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ClientBootstrapConsensusMaxInProgressTries](https://2019.www.torproject.org/docs/tor-manual.html.en#ClientBootstrapConsensusMaxInProgressTries)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ClientBootstrapConsensusMaxInProgressTries private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientBootstrapConsensusMaxInProgressTries",
            default = "3",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    //////////////////////
    //  SERVER OPTIONS  //
    //////////////////////

    /**
     * [Address](https://2019.www.torproject.org/docs/tor-manual.html.en#Address)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Address private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Address",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AssumeReachable](https://2019.www.torproject.org/docs/tor-manual.html.en#AssumeReachable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AssumeReachable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AssumeReachable",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [BridgeRelay](https://2019.www.torproject.org/docs/tor-manual.html.en#BridgeRelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class BridgeRelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgeRelay",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [BridgeDistribution](https://2019.www.torproject.org/docs/tor-manual.html.en#BridgeDistribution)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class BridgeDistribution private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgeDistribution",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ContactInfo](https://2019.www.torproject.org/docs/tor-manual.html.en#ContactInfo)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ContactInfo private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ContactInfo",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExitRelay](https://2019.www.torproject.org/docs/tor-manual.html.en#ExitRelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExitRelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitRelay",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExitPolicy](https://2019.www.torproject.org/docs/tor-manual.html.en#ExitPolicy)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExitPolicy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitPolicy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExitPolicyRejectPrivate](https://2019.www.torproject.org/docs/tor-manual.html.en#ExitPolicyRejectPrivate)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExitPolicyRejectPrivate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitPolicyRejectPrivate",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExitPolicyRejectLocalInterfaces](https://2019.www.torproject.org/docs/tor-manual.html.en#ExitPolicyRejectLocalInterfaces)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExitPolicyRejectLocalInterfaces private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitPolicyRejectLocalInterfaces",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ReducedExitPolicy](https://2019.www.torproject.org/docs/tor-manual.html.en#ReducedExitPolicy)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ReducedExitPolicy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ReducedExitPolicy",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [IPv6Exit](https://2019.www.torproject.org/docs/tor-manual.html.en#IPv6Exit)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class IPv6Exit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "IPv6Exit",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MaxOnionQueueDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#MaxOnionQueueDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MaxOnionQueueDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MaxOnionQueueDelay",
            default = 1_750.milliseconds.inWholeMilliseconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MyFamily](https://2019.www.torproject.org/docs/tor-manual.html.en#MyFamily)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MyFamily private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MyFamily",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [Nickname](https://2019.www.torproject.org/docs/tor-manual.html.en#Nickname)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class Nickname private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Nickname",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NumCPUs](https://2019.www.torproject.org/docs/tor-manual.html.en#NumCPUs)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class NumCPUs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NumCPUs",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ORPort](https://2019.www.torproject.org/docs/tor-manual.html.en#ORPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ORPort",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PublishServerDescriptor](https://2019.www.torproject.org/docs/tor-manual.html.en#PublishServerDescriptor)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PublishServerDescriptor private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PublishServerDescriptor",
            default = "1",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ShutdownWaitLength](https://2019.www.torproject.org/docs/tor-manual.html.en#ShutdownWaitLength)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ShutdownWaitLength private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ShutdownWaitLength",
            default = 30.seconds.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [SSLKeyLifetime](https://2019.www.torproject.org/docs/tor-manual.html.en#SSLKeyLifetime)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class SSLKeyLifetime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SSLKeyLifetime",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HeartbeatPeriod](https://2019.www.torproject.org/docs/tor-manual.html.en#HeartbeatPeriod)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HeartbeatPeriod private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HeartbeatPeriod",
            default = 6.hours.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MainloopStats](https://2019.www.torproject.org/docs/tor-manual.html.en#MainloopStats)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MainloopStats private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MainloopStats",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AccountingMax](https://2019.www.torproject.org/docs/tor-manual.html.en#AccountingMax)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AccountingMax private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AccountingMax",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AccountingRule](https://2019.www.torproject.org/docs/tor-manual.html.en#AccountingRule)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AccountingRule private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AccountingRule",
            default = "max",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AccountingStart](https://2019.www.torproject.org/docs/tor-manual.html.en#AccountingStart)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AccountingStart private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AccountingStart",
            default = "month 1 0:00",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RefuseUnknownExits](https://2019.www.torproject.org/docs/tor-manual.html.en#RefuseUnknownExits)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RefuseUnknownExits private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RefuseUnknownExits",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerDNSResolvConfFile](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerDNSResolvConfFile)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerDNSResolvConfFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSResolvConfFile",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerDNSAllowBrokenConfig](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerDNSAllowBrokenConfig)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerDNSAllowBrokenConfig private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSAllowBrokenConfig",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerDNSSearchDomains](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerDNSSearchDomains)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerDNSSearchDomains private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSSearchDomains",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerDNSDetectHijacking](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerDNSDetectHijacking)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerDNSDetectHijacking private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSDetectHijacking",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerDNSTestAddresses](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerDNSTestAddresses)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerDNSTestAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSTestAddresses",
            default = "www.google.com,www.mit.edu,www.yahoo.com,www.slashdot.org",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerDNSAllowNonRFC953Hostnames](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerDNSAllowNonRFC953Hostnames)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerDNSAllowNonRFC953Hostnames private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSAllowNonRFC953Hostnames",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [BridgeRecordUsageByCountry](https://2019.www.torproject.org/docs/tor-manual.html.en#BridgeRecordUsageByCountry)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class BridgeRecordUsageByCountry private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgeRecordUsageByCountry",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ServerDNSRandomizeCase](https://2019.www.torproject.org/docs/tor-manual.html.en#ServerDNSRandomizeCase)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ServerDNSRandomizeCase private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSRandomizeCase",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CellStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#CellStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class CellStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CellStatistics",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [PaddingStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#PaddingStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PaddingStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PaddingStatistics",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DirReqStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#DirReqStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirReqStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirReqStatistics",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [EntryStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#EntryStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class EntryStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "EntryStatistics",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExitPortStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#ExitPortStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExitPortStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitPortStatistics",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ConnDirectionStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#ConnDirectionStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ConnDirectionStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConnDirectionStatistics",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServiceStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HiddenServiceStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceStatistics",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExtraInfoStatistics](https://2019.www.torproject.org/docs/tor-manual.html.en#ExtraInfoStatistics)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExtraInfoStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtraInfoStatistics",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ExtendAllowPrivateAddresses](https://2019.www.torproject.org/docs/tor-manual.html.en#ExtendAllowPrivateAddresses)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ExtendAllowPrivateAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtendAllowPrivateAddresses",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MaxMemInQueues](https://2019.www.torproject.org/docs/tor-manual.html.en#MaxMemInQueues)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MaxMemInQueues private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MaxMemInQueues",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DisableOOSCheck](https://2019.www.torproject.org/docs/tor-manual.html.en#DisableOOSCheck)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DisableOOSCheck private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DisableOOSCheck",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [SigningKeyLifetime](https://2019.www.torproject.org/docs/tor-manual.html.en#SigningKeyLifetime)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class SigningKeyLifetime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SigningKeyLifetime",
            default = 30.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [OfflineMasterKey](https://2019.www.torproject.org/docs/tor-manual.html.en#OfflineMasterKey)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class OfflineMasterKey private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "OfflineMasterKey",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [KeyDirectory](https://2019.www.torproject.org/docs/tor-manual.html.en#KeyDirectory)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class KeyDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KeyDirectory",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [KeyDirectoryGroupReadable](https://2019.www.torproject.org/docs/tor-manual.html.en#KeyDirectoryGroupReadable)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class KeyDirectoryGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KeyDirectoryGroupReadable",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RephistTrackTime](https://2019.www.torproject.org/docs/tor-manual.html.en#RephistTrackTime)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RephistTrackTime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RephistTrackTime",
            default = 24.hours.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    ////////////////////////////////
    //  DIRECTORY SERVER OPTIONS  //
    ////////////////////////////////

    /**
     * [DirPortFrontPage](https://2019.www.torproject.org/docs/tor-manual.html.en#DirPortFrontPage)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirPortFrontPage private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirPortFrontPage",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DirPort](https://2019.www.torproject.org/docs/tor-manual.html.en#DirPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirPort",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DirPolicy](https://2019.www.torproject.org/docs/tor-manual.html.en#DirPolicy)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirPolicy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirPolicy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DirCache](https://2019.www.torproject.org/docs/tor-manual.html.en#DirCache)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirCache private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirCache",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MaxConsensusAgeForDiffs](https://2019.www.torproject.org/docs/tor-manual.html.en#MaxConsensusAgeForDiffs)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MaxConsensusAgeForDiffs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MaxConsensusAgeForDiffs",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    ////////////////////////////////////////////
    //  DENIAL OF SERVICE MITIGATION OPTIONS  //
    ////////////////////////////////////////////

    /**
     * [DoSCircuitCreationEnabled](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSCircuitCreationEnabled)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSCircuitCreationEnabled private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSCircuitCreationEnabled",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSCircuitCreationMinConnections](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSCircuitCreationMinConnections)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSCircuitCreationMinConnections private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSCircuitCreationMinConnections",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSCircuitCreationRate](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSCircuitCreationRate)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSCircuitCreationRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSCircuitCreationRate",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSCircuitCreationBurst](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSCircuitCreationBurst)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSCircuitCreationBurst private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSCircuitCreationBurst",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSCircuitCreationDefenseType](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSCircuitCreationDefenseType)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSCircuitCreationDefenseType private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSCircuitCreationDefenseType",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSCircuitCreationDefenseTimePeriod](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSCircuitCreationDefenseTimePeriod)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSCircuitCreationDefenseTimePeriod private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSCircuitCreationDefenseTimePeriod",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSConnectionEnabled](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSConnectionEnabled)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSConnectionEnabled private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSConnectionEnabled",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSConnectionMaxConcurrentCount](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSConnectionMaxConcurrentCount)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSConnectionMaxConcurrentCount private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSConnectionMaxConcurrentCount",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSConnectionDefenseType](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSConnectionDefenseType)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSConnectionDefenseType private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSConnectionDefenseType",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DoSRefuseSingleHopClientRendezvous](https://2019.www.torproject.org/docs/tor-manual.html.en#DoSRefuseSingleHopClientRendezvous)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DoSRefuseSingleHopClientRendezvous private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSRefuseSingleHopClientRendezvous",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    //////////////////////////////////////////
    //  DIRECTORY AUTHORITY SERVER OPTIONS  //
    //////////////////////////////////////////

    /**
     * [AuthoritativeDirectory](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthoritativeDirectory)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthoritativeDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthoritativeDirectory",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [V3AuthoritativeDirectory](https://2019.www.torproject.org/docs/tor-manual.html.en#V3AuthoritativeDirectory)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class V3AuthoritativeDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthoritativeDirectory",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [VersioningAuthoritativeDirectory](https://2019.www.torproject.org/docs/tor-manual.html.en#VersioningAuthoritativeDirectory)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class VersioningAuthoritativeDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "VersioningAuthoritativeDirectory",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RecommendedVersions](https://2019.www.torproject.org/docs/tor-manual.html.en#RecommendedVersions)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RecommendedVersions private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RecommendedVersions",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RecommendedPackages](https://2019.www.torproject.org/docs/tor-manual.html.en#RecommendedPackages)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RecommendedPackages private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RecommendedPackages",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RecommendedClientVersions](https://2019.www.torproject.org/docs/tor-manual.html.en#RecommendedClientVersions)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RecommendedClientVersions private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RecommendedClientVersions",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [BridgeAuthoritativeDir](https://2019.www.torproject.org/docs/tor-manual.html.en#BridgeAuthoritativeDir)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class BridgeAuthoritativeDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgeAuthoritativeDir",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MinUptimeHidServDirectoryV2](https://2019.www.torproject.org/docs/tor-manual.html.en#MinUptimeHidServDirectoryV2)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MinUptimeHidServDirectoryV2 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MinUptimeHidServDirectoryV2",
            default = 4.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [RecommendedServerVersions](https://2019.www.torproject.org/docs/tor-manual.html.en#RecommendedServerVersions)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class RecommendedServerVersions private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "RecommendedServerVersions",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ConsensusParams](https://2019.www.torproject.org/docs/tor-manual.html.en#ConsensusParams)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class ConsensusParams private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConsensusParams",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [DirAllowPrivateAddresses](https://2019.www.torproject.org/docs/tor-manual.html.en#DirAllowPrivateAddresses)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class DirAllowPrivateAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirAllowPrivateAddresses",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirBadExit](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirBadExit)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirBadExit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirBadExit",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirInvalid](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirInvalid)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirInvalid private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirInvalid",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirReject](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirReject)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirReject private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirReject",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirRejectCCs](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirRejectCCs)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirRejectCCs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirRejectCCs",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirListBadExits](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirListBadExits)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirListBadExits private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirListBadExits",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirMaxServersPerAddr](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirMaxServersPerAddr)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirMaxServersPerAddr private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirMaxServersPerAddr",
            default = "2",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirFastGuarantee](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirFastGuarantee)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirFastGuarantee private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirFastGuarantee",
            default = ((2.0F.pow(10)) * 100).toInt().toString(), // 100 KBytes
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirGuardBWGuarantee](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirGuardBWGuarantee)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirGuardBWGuarantee private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirGuardBWGuarantee",
            default = (2.0F.pow(21)).toInt().toString(), // 2MBytes
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirPinKeys](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirPinKeys)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirPinKeys private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirPinKeys",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirSharedRandomness](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirSharedRandomness)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirSharedRandomness private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirSharedRandomness",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirTestEd25519LinkKeys](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirTestEd25519LinkKeys)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirTestEd25519LinkKeys private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirTestEd25519LinkKeys",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [BridgePassword](https://2019.www.torproject.org/docs/tor-manual.html.en#BridgePassword)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class BridgePassword private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgePassword",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [V3AuthVotingInterval](https://2019.www.torproject.org/docs/tor-manual.html.en#V3AuthVotingInterval)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class V3AuthVotingInterval private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthVotingInterval",
            default = 1.hours.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [V3AuthVoteDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#V3AuthVoteDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class V3AuthVoteDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthVoteDelay",
            default = 5.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [V3AuthDistDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#V3AuthDistDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class V3AuthDistDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthDistDelay",
            default = 5.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [V3AuthNIntervalsValid](https://2019.www.torproject.org/docs/tor-manual.html.en#V3AuthNIntervalsValid)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class V3AuthNIntervalsValid private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthNIntervalsValid",
            default = "3",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [V3BandwidthsFile](https://2019.www.torproject.org/docs/tor-manual.html.en#V3BandwidthsFile)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class V3BandwidthsFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3BandwidthsFile",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [V3AuthUseLegacyKey](https://2019.www.torproject.org/docs/tor-manual.html.en#V3AuthUseLegacyKey)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class V3AuthUseLegacyKey private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthUseLegacyKey",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AuthDirHasIPv6Connectivity](https://2019.www.torproject.org/docs/tor-manual.html.en#AuthDirHasIPv6Connectivity)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class AuthDirHasIPv6Connectivity private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirHasIPv6Connectivity",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MinMeasuredBWsForAuthToIgnoreAdvertised](https://2019.www.torproject.org/docs/tor-manual.html.en#MinMeasuredBWsForAuthToIgnoreAdvertised)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class MinMeasuredBWsForAuthToIgnoreAdvertised private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MinMeasuredBWsForAuthToIgnoreAdvertised",
            default = "500",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    //////////////////////////////
    //  HIDDEN SERVICE OPTIONS  //
    //////////////////////////////

    /**
     * [PublishHidServDescriptors](https://2019.www.torproject.org/docs/tor-manual.html.en#PublishHidServDescriptors)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class PublishHidServDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PublishHidServDescriptors",
            default = true.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServiceAuthorizeClient](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceAuthorizeClient)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HiddenServiceAuthorizeClient private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceAuthorizeClient",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServiceExportCircuitID](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceExportCircuitID)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HiddenServiceExportCircuitID private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceExportCircuitID",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServiceSingleHopMode](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceSingleHopMode)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HiddenServiceSingleHopMode private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceSingleHopMode",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServiceNonAnonymousMode](https://2019.www.torproject.org/docs/tor-manual.html.en#HiddenServiceNonAnonymousMode)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class HiddenServiceNonAnonymousMode private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceNonAnonymousMode",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    ///////////////////////////////
    //  TESTING NETWORK OPTIONS  //
    ///////////////////////////////

    /**
     * [TestingTorNetwork](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingTorNetwork)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingTorNetwork private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingTorNetwork",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingV3AuthInitialVotingInterval](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingV3AuthInitialVotingInterval)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingV3AuthInitialVotingInterval private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingV3AuthInitialVotingInterval",
            default = 30.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingV3AuthInitialVoteDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingV3AuthInitialVoteDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingV3AuthInitialVoteDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingV3AuthInitialVoteDelay",
            default = 5.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingV3AuthInitialDistDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingV3AuthInitialDistDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingV3AuthInitialDistDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingV3AuthInitialDistDelay",
            default = 5.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingV3AuthVotingStartOffset](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingV3AuthVotingStartOffset)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingV3AuthVotingStartOffset private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingV3AuthVotingStartOffset",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingAuthDirTimeToLearnReachability](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingAuthDirTimeToLearnReachability)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingAuthDirTimeToLearnReachability private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingAuthDirTimeToLearnReachability",
            default = 30.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingEstimatedDescriptorPropagationTime](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingEstimatedDescriptorPropagationTime)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingEstimatedDescriptorPropagationTime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingEstimatedDescriptorPropagationTime",
            default = 10.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingMinFastFlagThreshold](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingMinFastFlagThreshold)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingMinFastFlagThreshold private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingMinFastFlagThreshold",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingServerDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingServerDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingServerDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingServerDownloadInitialDelay",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingClientDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingClientDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingClientDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingClientDownloadInitialDelay",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingServerConsensusDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingServerConsensusDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingServerConsensusDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingServerConsensusDownloadInitialDelay",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingClientConsensusDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingClientConsensusDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingClientConsensusDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingClientConsensusDownloadInitialDelay",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingBridgeDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingBridgeDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingBridgeDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingBridgeDownloadInitialDelay",
            default = 3.hours.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingBridgeBootstrapDownloadInitialDelay](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingBridgeBootstrapDownloadInitialDelay)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingBridgeBootstrapDownloadInitialDelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingBridgeBootstrapDownloadInitialDelay",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingClientMaxIntervalWithoutRequest](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingClientMaxIntervalWithoutRequest)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingClientMaxIntervalWithoutRequest private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingClientMaxIntervalWithoutRequest",
            default = 10.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingDirConnectionMaxStall](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingDirConnectionMaxStall)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingDirConnectionMaxStall private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirConnectionMaxStall",
            default = 5.minutes.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingDirAuthVoteExit](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingDirAuthVoteExit)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteExit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteExit",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingDirAuthVoteExitIsStrict](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingDirAuthVoteExitIsStrict)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteExitIsStrict private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteExitIsStrict",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingDirAuthVoteGuard](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingDirAuthVoteGuard)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteGuard private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteGuard",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingDirAuthVoteGuardIsStrict](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingDirAuthVoteGuardIsStrict)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteGuardIsStrict private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteGuardIsStrict",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingDirAuthVoteHSDir](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingDirAuthVoteHSDir)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteHSDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteHSDir",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingDirAuthVoteHSDirIsStrict](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingDirAuthVoteHSDirIsStrict)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteHSDirIsStrict private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteHSDirIsStrict",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingEnableConnBwEvent](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingEnableConnBwEvent)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingEnableConnBwEvent private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingEnableConnBwEvent",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingEnableCellStatsEvent](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingEnableCellStatsEvent)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingEnableCellStatsEvent private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingEnableCellStatsEvent",
            default = false.toByte().toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingMinExitFlagThreshold](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingMinExitFlagThreshold)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingMinExitFlagThreshold private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingMinExitFlagThreshold",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingLinkCertLifetime](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingLinkCertLifetime)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingLinkCertLifetime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingLinkCertLifetime",
            default = 2.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingAuthKeyLifetime](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingAuthKeyLifetime)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingAuthKeyLifetime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingAuthKeyLifetime",
            default = 2.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [TestingSigningKeySlop](https://2019.www.torproject.org/docs/tor-manual.html.en#TestingSigningKeySlop)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class TestingSigningKeySlop private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingSigningKeySlop",
            default = 1.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    //////////////////////////////
    //  NON_PERSISTENT OPTIONS  //
    //////////////////////////////

    /**
     * [__DirPort](https://2019.www.torproject.org/docs/tor-manual.html.en#DirPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __DirPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__DirPort",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__ExtORPort](https://2019.www.torproject.org/docs/tor-manual.html.en#ExtORPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __ExtORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__ExtORPort",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__ORPort](https://2019.www.torproject.org/docs/tor-manual.html.en#ORPort)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __ORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__ORPort",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__AllDirActionsPrivate](https://2019.www.torproject.org/docs/tor-manual.html.en#AllDirActionsPrivate)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __AllDirActionsPrivate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__AllDirActionsPrivate",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__DisablePredictedCircuits](https://2019.www.torproject.org/docs/tor-manual.html.en#DisablePredictedCircuits)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __DisablePredictedCircuits private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__DisablePredictedCircuits",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__LeaveStreamsUnattached](https://2019.www.torproject.org/docs/tor-manual.html.en#LeaveStreamsUnattached)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __LeaveStreamsUnattached private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__LeaveStreamsUnattached",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__HashedControlSessionPassword](https://2019.www.torproject.org/docs/tor-manual.html.en#HashedControlSessionPassword)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __HashedControlSessionPassword private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__HashedControlSessionPassword",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__ReloadTorrcOnSIGHUP](https://2019.www.torproject.org/docs/tor-manual.html.en#__ReloadTorrcOnSIGHUP)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __ReloadTorrcOnSIGHUP private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__ReloadTorrcOnSIGHUP",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__OwningControllerFD](https://2019.www.torproject.org/docs/tor-manual.html.en#__OwningControllerFD)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __OwningControllerFD private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__OwningControllerFD",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [__DisableSignalHandlers](https://2019.www.torproject.org/docs/tor-manual.html.en#__DisableSignalHandlers)
     *
     * TODO: Implement
     * */
    @KmpTorDsl
    public class __DisableSignalHandlers private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__DisableSignalHandlers",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
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
            default: String,
            attributes: Set<Attribute>,
            isCmdLineArg: Boolean,
            isUnique: Boolean,
            private val factory: () -> B,
            private val build: B.() -> S,
        ): Keyword(name, default, attributes, isCmdLineArg, isUnique) {

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
     * @param [default] Tor's default value for this Keyword
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
        public val default: String,
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
