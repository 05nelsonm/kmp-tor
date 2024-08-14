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
import io.matthewnelson.kmp.tor.runtime.core.internal.byte
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
 * [tor-manual](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc)
 * are declared within [TorConfig] as subclasses. Many of them do not
 * have their builders implemented, but are available for use with
 * [TorCmd.Config.Get] and [TorCmd.Config.Reset] queries.
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
        protected val settings: MutableSet<Setting> = LinkedHashSet(1, 1.0f)
        // For dealing with inherited disabled port
        @JvmField
        protected val inheritedDisabledPorts: MutableSet<Setting> = LinkedHashSet(1, 1.0f)

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
                val disabledPorts = LinkedHashSet<Setting>(1, 1.0f)

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

                return TorConfig(settings.sortedBy { it.keyword }.toImmutableSet())
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
     * [ControlPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
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
        private val unixFlags = LinkedHashSet<String>(1, 1.0f)

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
     * [DNSPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DNSPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
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
        private val isolationFlags = LinkedHashSet<String>(1, 1.0f)

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
     * [HTTPTunnelPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HTTPTunnelPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
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
        private val isolationFlags = LinkedHashSet<String>(1, 1.0f)

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
     * [SocksPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
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
        private val socksFlags = LinkedHashSet<String>(1, 1.0f)
        private val unixFlags = LinkedHashSet<String>(1, 1.0f)
        private val isolationFlags = LinkedHashSet<String>(1, 1.0f)

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
     * [TransPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TransPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
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
        private val isolationFlags = LinkedHashSet<String>(1, 1.0f)

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
        )
    }

    /**
     * [__OwningControllerProcess](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OwningControllerProcess)
     *
     * [TAKEOWNERSHIP](https://spec.torproject.org/control-spec/commands.html#takeownership)
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
     * [__ReloadTorrcOnSIGHUP](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __ReloadTorrcOnSIGHUP private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var reload: Boolean = true

        public companion object: Setting.Factory<__ReloadTorrcOnSIGHUP, Setting>(
            name = "__ReloadTorrcOnSIGHUP",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { __ReloadTorrcOnSIGHUP() },
            build = { build(reload.byte.toString())!! },
        )
    }

    /**
     * [CacheDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CacheDirectory)
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
     * [ControlPortWriteToFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPortWriteToFile)
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
     * [CookieAuthentication](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CookieAuthentication)
     * */
    @KmpTorDsl
    public class CookieAuthentication private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<CookieAuthentication, Setting>(
            name = "CookieAuthentication",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { CookieAuthentication() },
            build = { build(enable.byte.toString())!! },
        )
    }

    /**
     * [CookieAuthFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CookieAuthFile)
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
     * [DataDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DataDirectory)
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
     * [DisableNetwork](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableNetwork)
     * */
    @KmpTorDsl
    public class DisableNetwork private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var disable: Boolean = false

        public companion object: Setting.Factory<DisableNetwork, Setting>(
            name = "DisableNetwork",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { DisableNetwork() },
            build = { build(disable.byte.toString())!! },
        )
    }

    /**
     * [RunAsDaemon](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RunAsDaemon)
     * */
    @KmpTorDsl
    public class RunAsDaemon private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<RunAsDaemon, Setting>(
            name = "RunAsDaemon",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
            factory = { RunAsDaemon() },
            build = { build(enable.byte.toString())!! },
        )
    }

    /**
     * [SyslogIdentityTag](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SyslogIdentityTag)
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
     * [AndroidIdentityTag](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AndroidIdentityTag)
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
     * [AutomapHostsOnResolve](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AutomapHostsOnResolve)
     * */
    @KmpTorDsl
    public class AutomapHostsOnResolve private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<AutomapHostsOnResolve, Setting>(
            name = "AutomapHostsOnResolve",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { AutomapHostsOnResolve() },
            build = { build(enable.byte.toString())!! },
        )
    }

    /**
     * [AutomapHostsSuffixes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AutomapHostsSuffixes)
     * */
    @KmpTorDsl
    public class AutomapHostsSuffixes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        private val suffixes = LinkedHashSet<String>(1, 1.0f)

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
     * [ClientOnionAuthDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientOnionAuthDir)
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
     * [ConnectionPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConnectionPadding)
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
            argument = enable.byte.toString()
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
     * [ReducedConnectionPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReducedConnectionPadding)
     * */
    @KmpTorDsl
    public class ReducedConnectionPadding private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<ReducedConnectionPadding, Setting>(
            name = "ReducedConnectionPadding",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { ReducedConnectionPadding() },
            build = { build(enable.byte.toString())!! },
        )
    }

    /**
     * [VirtualAddrNetworkIPv4](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VirtualAddrNetworkIPv4)
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
     * [VirtualAddrNetworkIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VirtualAddrNetworkIPv6)
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

    /**
     * [DormantCanceledByStartup](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantCanceledByStartup)
     * */
    @KmpTorDsl
    public class DormantCanceledByStartup private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var cancel: Boolean = false

        public companion object: Setting.Factory<DormantCanceledByStartup, Setting>(
            name = "DormantCanceledByStartup",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { DormantCanceledByStartup() },
            build = { build(cancel.byte.toString())!! },
        )
    }

    /**
     * [DormantClientTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantClientTimeout)
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
     * [DormantOnFirstStartup](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantOnFirstStartup)
     * */
    @KmpTorDsl
    public class DormantOnFirstStartup private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = false

        public companion object: Setting.Factory<DormantOnFirstStartup, Setting>(
            name = "DormantOnFirstStartup",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { DormantOnFirstStartup() },
            build = { build(enable.byte.toString())!! },
        )
    }

    /**
     * [DormantTimeoutDisabledByIdleStreams](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantTimeoutDisabledByIdleStreams)
     * */
    @KmpTorDsl
    public class DormantTimeoutDisabledByIdleStreams private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        @JvmField
        public var enable: Boolean = true

        public companion object: Setting.Factory<DormantTimeoutDisabledByIdleStreams, Setting>(
            name = "DormantTimeoutDisabledByIdleStreams",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
            factory = { DormantTimeoutDisabledByIdleStreams() },
            build = { build(enable.byte.toString())!! },
        )
    }

    /**
     * [GeoIPExcludeUnknown](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GeoIPExcludeUnknown)
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
            argument = exclude.byte.toString()
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
     * [GeoIPFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GeoIPFile)
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
     * [GeoIPv6File](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GeoIPv6File)
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
     * [HiddenServiceDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceDir)
     * */
    @KmpTorDsl
    public class HiddenServiceDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {

        private val ports = LinkedHashSet<LineItem>(1, 1.0f)
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
     * [HiddenServicePort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePort)
     * */
    @KmpTorDsl
    public class HiddenServicePort private constructor() {

        private var targetArgument: String? = null

        // TODO: Check if can be 0 (Issue #419)
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
            ): LineItem? {
                val b = HiddenServicePort().apply(block)
                val virtual = b.virtual ?: return null
                val target = b.targetArgument ?: virtual.toString()
                return toLineItem("$virtual $target")
            }
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
     * [HiddenServiceVersion](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceVersion)
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
            ): LineItem? {
                val b = HiddenServiceVersion().apply(block)
                return toLineItem(b.argument?.toString())
            }
        }
    }

    /**
     * [HiddenServiceAllowUnknownPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceAllowUnknownPorts)
     * */
    @KmpTorDsl
    public class HiddenServiceAllowUnknownPorts private constructor() {

        @JvmField
        public var allow: Boolean = false

        public companion object: Keyword(
            name = "HiddenServiceAllowUnknownPorts",
            default = false.byte.toString(),
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceAllowUnknownPorts>,
            ): LineItem {
                val b = HiddenServiceAllowUnknownPorts().apply(block)
                return toLineItem(b.allow.byte.toString())!!
            }
        }
    }

    /**
     * [HiddenServiceMaxStreams](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceMaxStreams)
     * */
    @KmpTorDsl
    public class HiddenServiceMaxStreams private constructor() {

        /**
         * If not between a value of 0 and 65535 (inclusive), the
         * default value of 0 (Unlimited) will be utilized.
         * */
        @JvmField
        public var maximum: Int = 0

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
            ): LineItem {
                val b = HiddenServiceMaxStreams().apply(block)
                var maximum = b.maximum
                if (maximum !in Port.MIN..Port.MAX) maximum = 0
                return toLineItem(maximum.toString())!!
            }
        }
    }

    /**
     * [HiddenServiceMaxStreamsCloseCircuit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceMaxStreamsCloseCircuit)
     * */
    @KmpTorDsl
    public class HiddenServiceMaxStreamsCloseCircuit private constructor() {

        @JvmField
        public var close: Boolean = false

        public companion object: Keyword(
            name = "HiddenServiceMaxStreamsCloseCircuit",
            default = false.byte.toString(),
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceMaxStreamsCloseCircuit>,
            ): LineItem {
                val b = HiddenServiceMaxStreamsCloseCircuit().apply(block)
                return toLineItem(b.close.byte.toString())!!
            }
        }
    }

    /**
     * [HiddenServiceDirGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceDirGroupReadable)
     * */
    @KmpTorDsl
    public class HiddenServiceDirGroupReadable private constructor() {

        @JvmField
        public var readable: Boolean = false

        public companion object: Keyword(
            name = "HiddenServiceDirGroupReadable",
            default = false.byte.toString(),
            attributes = immutableSetOf(Attribute.HiddenService),
            isCmdLineArg = false,
            isUnique = false,
        ) {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenServiceDirGroupReadable>,
            ): LineItem {
                val b = HiddenServiceDirGroupReadable().apply(block)
                return toLineItem(b.readable.byte.toString())!!
            }
        }
    }

    /**
     * [HiddenServiceNumIntroductionPoints](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceNumIntroductionPoints)
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

    ////////////////////////////////////////
    ////////////////////////////////////////
    ////                                ////
    ////    Not implemented settings    ////
    ////                                ////
    ////////////////////////////////////////
    ////////////////////////////////////////

    //////////////////////////////
    //  NON-PERSISTENT OPTIONS  //
    //////////////////////////////

    // (IMPLEMENTED) __ControlPort

    /**
     * [DirPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     * */
    @KmpTorDsl
    public class __DirPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__DirPort",
            default = "0",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    // (IMPLEMENTED) __DNSPort

    /**
     * [ExtORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     * */
    @KmpTorDsl
    public class __ExtORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__ExtORPort",
            default = "",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [MetricsPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MetricsPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     * */
    @KmpTorDsl
    public class __MetricsPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__MetricsPort",
            default = "",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [NATDPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NATDPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     * */
    @KmpTorDsl
    public class __NATDPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__NATDPort",
            default = "0",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [ORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ORPort)
     *
     * [Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     * */
    @KmpTorDsl
    public class __ORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__ORPort",
            default = "0",
            attributes = immutableSetOf(Attribute.Port),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    // (IMPLEMENTED) __SocksPort
    // (IMPLEMENTED) __TransPort

    /**
     * [__AllDirActionsPrivate](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __AllDirActionsPrivate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__AllDirActionsPrivate",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [__AlwaysCongestionControl](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __AlwaysCongestionControl private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__AlwaysCongestionControl",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [__DisablePredictedCircuits](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __DisablePredictedCircuits private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__DisablePredictedCircuits",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [__DisableSignalHandlers](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __DisableSignalHandlers private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__DisableSignalHandlers",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [__LeaveStreamsUnattached](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __LeaveStreamsUnattached private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__LeaveStreamsUnattached",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [__HashedControlSessionPassword](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __HashedControlSessionPassword private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__HashedControlSessionPassword",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = false,
        )
    }

    /**
     * [__OwningControllerFD](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __OwningControllerFD private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__OwningControllerFD",
            default = "-1",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) __ReloadTorrcOnSIGHUP

    /**
     * [__SbwsExit](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    @KmpTorDsl
    public class __SbwsExit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "__SbwsExit",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    ///////////////////////
    //  GENERAL OPTIONS  //
    ///////////////////////

    /**
     * [AccelDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccelDir)
     * */
    @KmpTorDsl
    public class AccelDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AccelDir",
            default = "",
            attributes = immutableSetOf(Attribute.Directory),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [AccelName](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccelName)
     * */
    @KmpTorDsl
    public class AccelName private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AccelName",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [AlternateBridgeAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AlternateBridgeAuthority)
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
     * [AlternateDirAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AlternateDirAuthority)
     * */
    @KmpTorDsl
    public class AlternateDirAuthority private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AlternateDirAuthority",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [AvoidDiskWrites](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AvoidDiskWrites)
     * */
    @KmpTorDsl
    public class AvoidDiskWrites private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AvoidDiskWrites",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [BandwidthBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BandwidthBurst)
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
            isUnique = true,
        )
    }

    /**
     * [BandwidthRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BandwidthRate)
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
            isUnique = true,
        )
    }

    // (IMPLEMENTED) CacheDirectory

    /**
     * [CacheDirectoryGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CacheDirectoryGroupReadable)
     * */
    @KmpTorDsl
    public class CacheDirectoryGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CacheDirectoryGroupReadable",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [CircuitPriorityHalflife](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitPriorityHalflife)
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
            isUnique = true,
        )
    }

    /**
     * [ClientTransportPlugin](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientTransportPlugin)
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
     * [ConfluxEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConfluxEnabled)
     * */
    @KmpTorDsl
    public class ConfluxEnabled private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConfluxEnabled",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ConfluxClientUX](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConfluxClientUX)
     * */
    @KmpTorDsl
    public class ConfluxClientUX private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConfluxClientUX",
            default = "throughput",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ConnLimit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConnLimit)
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
            isUnique = true,
        )
    }

    /**
     * [ConstrainedSockets](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConstrainedSockets)
     * */
    @KmpTorDsl
    public class ConstrainedSockets private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConstrainedSockets",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ConstrainedSockSize](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConstrainedSockSize)
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
            isUnique = true,
        )
    }

    /**
     * See ephemeral setting [__ControlPort].
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
     * [ControlPortFileGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPortFileGroupReadable)
     * */
    @KmpTorDsl
    public class ControlPortFileGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ControlPortFileGroupReadable",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) ControlPortWriteToFile

    /**
     * [ControlSocket](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlSocket)
     * */
    @KmpTorDsl
    public class ControlSocket private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ControlSocket",
            default = "0",
            attributes = immutableSetOf(Attribute.UnixSocket),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [ControlSocketsGroupWritable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlSocketsGroupWritable)
     * */
    @KmpTorDsl
    public class ControlSocketsGroupWritable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ControlSocketsGroupWritable",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) CookieAuthentication
    // (IMPLEMENTED) CookieAuthFile

    /**
     * [CookieAuthFileGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CookieAuthFileGroupReadable)
     * */
    @KmpTorDsl
    public class CookieAuthFileGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CookieAuthFileGroupReadable",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [CountPrivateBandwidth](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CountPrivateBandwidth)
     * */
    @KmpTorDsl
    public class CountPrivateBandwidth private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CountPrivateBandwidth",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) DataDirectory

    /**
     * [DataDirectoryGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DataDirectoryGroupReadable)
     * */
    @KmpTorDsl
    public class DataDirectoryGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DataDirectoryGroupReadable",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [DirAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirAuthority)
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
     * [DirAuthorityFallbackRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirAuthorityFallbackRate)
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
            isUnique = true,
        )
    }

    /**
     * [DisableAllSwap](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableAllSwap)
     * */
    @KmpTorDsl
    public class DisableAllSwap private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DisableAllSwap",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [DisableDebuggerAttachment](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableDebuggerAttachment)
     * */
    @KmpTorDsl
    public class DisableDebuggerAttachment private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DisableDebuggerAttachment",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) DisableNetwork

    /**
     * [ExtendByEd25519ID](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtendByEd25519ID)
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
            isUnique = true,
        )
    }

    /**
     * [ExtORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPort)
     * */
    @KmpTorDsl
    public class ExtORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __ExtORPort.name.drop(2),
            default = __ExtORPort.default,
            attributes = __ExtORPort.attributes,
            isCmdLineArg = __ExtORPort.isCmdLineArg,
            isUnique = __ExtORPort.isUnique,
        )
    }

    /**
     * [ExtORPortCookieAuthFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPortCookieAuthFile)
     * */
    @KmpTorDsl
    public class ExtORPortCookieAuthFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtORPortCookieAuthFile",
            default = "",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [ExtORPortCookieAuthFileGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPortCookieAuthFileGroupReadable)
     * */
    @KmpTorDsl
    public class ExtORPortCookieAuthFileGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtORPortCookieAuthFileGroupReadable",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [FallbackDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FallbackDir)
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
            isUnique = true,
        )
    }

    /**
     * [FetchDirInfoEarly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchDirInfoEarly)
     * */
    @KmpTorDsl
    public class FetchDirInfoEarly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchDirInfoEarly",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [FetchDirInfoExtraEarly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchDirInfoExtraEarly)
     * */
    @KmpTorDsl
    public class FetchDirInfoExtraEarly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchDirInfoExtraEarly",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [FetchHidServDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchHidServDescriptors)
     * */
    @KmpTorDsl
    public class FetchHidServDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchHidServDescriptors",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [FetchServerDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchServerDescriptors)
     * */
    @KmpTorDsl
    public class FetchServerDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchServerDescriptors",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [FetchUselessDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchUselessDescriptors)
     * */
    @KmpTorDsl
    public class FetchUselessDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FetchUselessDescriptors",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [HardwareAccel](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HardwareAccel)
     * */
    @KmpTorDsl
    public class HardwareAccel private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HardwareAccel",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [HashedControlPassword](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HashedControlPassword)
     * */
    @KmpTorDsl
    public class HashedControlPassword private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HashedControlPassword",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = false,
        )
    }

    // (DEPRECATED) HTTPProxy
    // (DEPRECATED) HTTPProxyAuthenticator

    /**
     * [HTTPSProxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HTTPSProxy)
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
            isUnique = true,
        )
    }

    /**
     * [HTTPSProxyAuthenticator](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HTTPSProxyAuthenticator)
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
            isUnique = true,
        )
    }

    /**
     * [KeepalivePeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeepalivePeriod)
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
            isUnique = true,
        )
    }

    /**
     * [KeepBindCapabilities](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeepBindCapabilities)
     * */
    @KmpTorDsl
    public class KeepBindCapabilities private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KeepBindCapabilities",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [Log](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Log)
     * */
    @KmpTorDsl
    public class Log private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Log",
            default = "",
            attributes = immutableSetOf(Attribute.Logging),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [LogMessageDomains](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LogMessageDomains)
     * */
    @KmpTorDsl
    public class LogMessageDomains private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "LogMessageDomains",
            default = false.byte.toString(),
            attributes = immutableSetOf(Attribute.Logging),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [LogTimeGranularity](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LogTimeGranularity)
     * */
    @KmpTorDsl
    public class LogTimeGranularity private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "LogTimeGranularity",
            default = 1.seconds.inWholeMilliseconds.toString(),
            attributes = immutableSetOf(Attribute.Logging),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [MaxAdvertisedBandwidth](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxAdvertisedBandwidth)
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
            isUnique = true,
        )
    }

    /**
     * [MaxUnparseableDescSizeToLog](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxUnparseableDescSizeToLog)
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
            isUnique = true,
        )
    }

    /**
     * [MetricsPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MetricsPort)
     * */
    @KmpTorDsl
    public class MetricsPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __MetricsPort.name.drop(2),
            default = __MetricsPort.default,
            attributes = __MetricsPort.attributes,
            isCmdLineArg = __MetricsPort.isCmdLineArg,
            isUnique = __MetricsPort.isUnique,
        )
    }

    /**
     * [MetricsPortPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MetricsPortPolicy)
     * */
    @KmpTorDsl
    public class MetricsPortPolicy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MetricsPortPolicy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [NoExec](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NoExec)
     * */
    @KmpTorDsl
    public class NoExec private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "NoExec",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [OutboundBindAddress](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddress)
     * */
    @KmpTorDsl
    public class OutboundBindAddress private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        // Can be utilized 2 times max. IPv4 & IPv6

        public companion object: Keyword(
            name = "OutboundBindAddress",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [OutboundBindAddressExit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddressExit)
     * */
    @KmpTorDsl
    public class OutboundBindAddressExit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        // Can be utilized 2 times max. IPv4 & IPv6

        public companion object: Keyword(
            name = "OutboundBindAddressExit",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [OutboundBindAddressOR](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddressOR)
     * */
    @KmpTorDsl
    public class OutboundBindAddressOR private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        // Can be utilized 2 times max. IPv4 & IPv6

        public companion object: Keyword(
            name = "OutboundBindAddressOR",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) __OwningControllerProcess

    /**
     * [PerConnBWBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PerConnBWBurst)
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
            isUnique = true,
        )
    }

    /**
     * [PerConnBWRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PerConnBWRate)
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
            isUnique = true,
        )
    }

    /**
     * [OutboundBindAddressPT](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddressPT)
     * */
    @KmpTorDsl
    public class OutboundBindAddressPT private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        // Can be utilized 2 times max. IPv4 & IPv6

        public companion object: Keyword(
            name = "OutboundBindAddressPT",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PidFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PidFile)
     * */
    @KmpTorDsl
    public class PidFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PidFile",
            default = "",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [ProtocolWarnings](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ProtocolWarnings)
     * */
    @KmpTorDsl
    public class ProtocolWarnings private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ProtocolWarnings",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [RelayBandwidthBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RelayBandwidthBurst)
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
            isUnique = true,
        )
    }

    /**
     * [RelayBandwidthRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RelayBandwidthRate)
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
            isUnique = true,
        )
    }

    /**
     * [RephistTrackTime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RephistTrackTime)
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
            isUnique = true,
        )
    }

    // (IMPLEMENTED) RunAsDaemon

    /**
     * [SafeLogging](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SafeLogging)
     * */
    @KmpTorDsl
    public class SafeLogging private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SafeLogging",
            default = "1",
            attributes = immutableSetOf(Attribute.Logging),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [Sandbox](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Sandbox)
     * */
    @KmpTorDsl
    public class Sandbox private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Sandbox",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [Schedulers](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Schedulers)
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
            isUnique = true,
        )
    }

    /**
     * [KISTSchedRunInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KISTSchedRunInterval)
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
            isUnique = true,
        )
    }

    /**
     * [KISTSockBufSizeFactor](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KISTSockBufSizeFactor)
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
            isUnique = true,
        )
    }

    /**
     * [Socks4Proxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks4Proxy)
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
            isUnique = true,
        )
    }

    /**
     * [Socks5Proxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks5Proxy)
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
            isUnique = true,
        )
    }

    /**
     * [Socks5ProxyUsername](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks5ProxyUsername)
     * */
    @KmpTorDsl
    public class Socks5ProxyUsername private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "Socks5ProxyUsername",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [Socks5ProxyPassword](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks5ProxyPassword)
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
            isUnique = true,
        )
    }

    // (IMPLEMENTED) SyslogIdentityTag

    /**
     * [TCPProxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TCPProxy)
     * */
    @KmpTorDsl
    public class TCPProxy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TCPProxy",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TruncateLogFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TruncateLogFile)
     * */
    @KmpTorDsl
    public class TruncateLogFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TruncateLogFile",
            default = false.byte.toString(),
            attributes = immutableSetOf(Attribute.Logging),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [UnixSocksGroupWritable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UnixSocksGroupWritable)
     * */
    @KmpTorDsl
    public class UnixSocksGroupWritable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UnixSocksGroupWritable",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [UseDefaultFallbackDirs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseDefaultFallbackDirs)
     * */
    @KmpTorDsl
    public class UseDefaultFallbackDirs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseDefaultFallbackDirs",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [User](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#User)
     * */
    @KmpTorDsl
    public class User private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "User",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    //////////////////////
    //  CLIENT OPTIONS  //
    //////////////////////

    /**
     * [AllowNonRFC953Hostnames](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AllowNonRFC953Hostnames)
     * */
    @KmpTorDsl
    public class AllowNonRFC953Hostnames private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AllowNonRFC953Hostnames",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) AutomapHostsOnResolve
    // (IMPLEMENTED) AutomapHostsSuffixes

    /**
     * [Bridge](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Bridge)
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
     * [CircuitPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitPadding)
     * */
    @KmpTorDsl
    public class CircuitPadding private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CircuitPadding",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [ReducedCircuitPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReducedCircuitPadding)
     * */
    @KmpTorDsl
    public class ReducedCircuitPadding private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ReducedCircuitPadding",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ClientBootstrapConsensusAuthorityDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusAuthorityDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [ClientBootstrapConsensusFallbackDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusFallbackDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [ClientBootstrapConsensusMaxInProgressTries](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusMaxInProgressTries)
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
            isUnique = true,
        )
    }

    /**
     * [ClientDNSRejectInternalAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientDNSRejectInternalAddresses)
     * */
    @KmpTorDsl
    public class ClientDNSRejectInternalAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientDNSRejectInternalAddresses",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) ClientOnionAuthDir

    /**
     * [ClientOnly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientOnly)
     * */
    @KmpTorDsl
    public class ClientOnly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientOnly",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (DEPRECATED) ClientPreferIPv6DirPort

    /**
     * [ClientPreferIPv6ORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientPreferIPv6ORPort)
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
            isUnique = true,
        )
    }

    /**
     * [ClientRejectInternalAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientRejectInternalAddresses)
     * */
    @KmpTorDsl
    public class ClientRejectInternalAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientRejectInternalAddresses",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ClientUseIPv4](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientUseIPv4)
     * */
    @KmpTorDsl
    public class ClientUseIPv4 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientUseIPv4",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ClientUseIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientUseIPv6)
     * */
    @KmpTorDsl
    public class ClientUseIPv6 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ClientUseIPv6",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) ConnectionPadding
    // (IMPLEMENTED) ReducedConnectionPadding

    /**
     * See ephemeral setting [__DNSPort].
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
     * [DownloadExtraInfo](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DownloadExtraInfo)
     * */
    @KmpTorDsl
    public class DownloadExtraInfo private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DownloadExtraInfo",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [EnforceDistinctSubnets](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#EnforceDistinctSubnets)
     * */
    @KmpTorDsl
    public class EnforceDistinctSubnets private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "EnforceDistinctSubnets",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [FascistFirewall](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FascistFirewall)
     * */
    @KmpTorDsl
    public class FascistFirewall private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "FascistFirewall",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (DEPRECATED) FirewallPorts

    /**
     * See ephemeral setting [__HTTPTunnelPort].
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
     * [LongLivedPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LongLivedPorts)
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
            isUnique = true,
        )
    }

    /**
     * [MapAddress](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MapAddress)
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
     * [MaxCircuitDirtiness](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxCircuitDirtiness)
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
            isUnique = true,
        )
    }

    /**
     * [MaxClientCircuitsPending](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxClientCircuitsPending)
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
            isUnique = true,
        )
    }

    /**
     * [NATDPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NATDPort)
     * */
    @KmpTorDsl
    public class NATDPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __NATDPort.name.drop(2),
            default = __NATDPort.default,
            attributes = __NATDPort.attributes,
            isCmdLineArg = __NATDPort.isCmdLineArg,
            isUnique = __NATDPort.isUnique,
        )
    }

    /**
     * [NewCircuitPeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NewCircuitPeriod)
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
            isUnique = true,
        )
    }

    /**
     * [PathBiasCircThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasCircThreshold)
     * */
    @KmpTorDsl
    public class PathBiasCircThreshold private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasCircThreshold",
            default = "-1",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasDropGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasDropGuards)
     * */
    @KmpTorDsl
    public class PathBiasDropGuards private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasDropGuards",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasExtremeRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasExtremeRate)
     * */
    @KmpTorDsl
    public class PathBiasExtremeRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasExtremeRate",
            default = "-1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasNoticeRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasNoticeRate)
     * */
    @KmpTorDsl
    public class PathBiasNoticeRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasNoticeRate",
            default = "-1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasWarnRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasWarnRate)
     * */
    @KmpTorDsl
    public class PathBiasWarnRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasWarnRate",
            default = "-1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasScaleThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasScaleThreshold)
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
            isUnique = true,
        )
    }

    /**
     * [PathBiasUseThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasUseThreshold)
     * */
    @KmpTorDsl
    public class PathBiasUseThreshold private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasUseThreshold",
            default = "-1",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasNoticeUseRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasNoticeUseRate)
     * */
    @KmpTorDsl
    public class PathBiasNoticeUseRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasNoticeUseRate",
            default = "-1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasExtremeUseRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasExtremeUseRate)
     * */
    @KmpTorDsl
    public class PathBiasExtremeUseRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PathBiasExtremeUseRate",
            default = "-1.000000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PathBiasScaleUseThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasScaleUseThreshold)
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
            isUnique = true,
        )
    }

    /**
     * [PathsNeededToBuildCircuits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathsNeededToBuildCircuits)
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
            isUnique = true,
        )
    }

    /**
     * [ReachableAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReachableAddresses)
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
            isUnique = true,
        )
    }

    // (DEPRECATED) ReachableDirAddresses

    /**
     * [ReachableORAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReachableORAddresses)
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
            isUnique = true,
        )
    }

    /**
     * [SafeSocks](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SafeSocks)
     * */
    @KmpTorDsl
    public class SafeSocks private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "SafeSocks",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestSocks](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestSocks)
     * */
    @KmpTorDsl
    public class TestSocks private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestSocks",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [WarnPlaintextPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#WarnPlaintextPorts)
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
            isUnique = true,
        )
    }

    /**
     * [RejectPlaintextPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RejectPlaintextPorts)
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
            isUnique = true,
        )
    }

    /**
     * [SocksPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksPolicy)
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
            isUnique = true,
        )
    }

    /**
     * See ephemeral setting [__SocksPort].
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
     * [TokenBucketRefillInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TokenBucketRefillInterval)
     * */
    @KmpTorDsl
    public class TokenBucketRefillInterval private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TokenBucketRefillInterval",
            default = 100.milliseconds.inWholeMilliseconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [TrackHostExits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TrackHostExits)
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
            isUnique = true,
        )
    }

    /**
     * [TrackHostExitsExpire](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TrackHostExitsExpire)
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
            isUnique = true,
        )
    }

    /**
     * See ephemeral setting [__TransPort].
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

    /**
     * [TransProxyType](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TransProxyType)
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
            isUnique = true,
        )
    }

    /**
     * [UpdateBridgesFromAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UpdateBridgesFromAuthority)
     * */
    @KmpTorDsl
    public class UpdateBridgesFromAuthority private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UpdateBridgesFromAuthority",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [UseBridges](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseBridges)
     * */
    @KmpTorDsl
    public class UseBridges private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseBridges",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [UseEntryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseEntryGuards)
     * */
    @KmpTorDsl
    public class UseEntryGuards private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "UseEntryGuards",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [UseGuardFraction](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseGuardFraction)
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
            isUnique = true,
        )
    }

    /**
     * [GuardLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GuardLifetime)
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
            isUnique = true,
        )
    }

    /**
     * [NumDirectoryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumDirectoryGuards)
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
            isUnique = true,
        )
    }

    /**
     * [NumEntryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumEntryGuards)
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
            isUnique = true,
        )
    }

    /**
     * [NumPrimaryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumPrimaryGuards)
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
            isUnique = true,
        )
    }

    /**
     * [VanguardsLiteEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VanguardsLiteEnabled)
     * */
    @KmpTorDsl
    public class VanguardsLiteEnabled private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "VanguardsLiteEnabled",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [UseMicrodescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseMicrodescriptors)
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
            isUnique = true,
        )
    }

    // (IMPLEMENTED) VirtualAddrNetworkIPv4
    // (IMPLEMENTED) VirtualAddrNetworkIPv6

    ///////////////////////////////
    //  CIRCUIT TIMEOUT OPTIONS  //
    ///////////////////////////////

    /**
     * [CircuitsAvailableTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitsAvailableTimeout)
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
            isUnique = true,
        )
    }

    /**
     * [LearnCircuitBuildTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LearnCircuitBuildTimeout)
     * */
    @KmpTorDsl
    public class LearnCircuitBuildTimeout private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "LearnCircuitBuildTimeout",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [CircuitBuildTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitBuildTimeout)
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
            isUnique = true,
        )
    }

    /**
     * [CircuitStreamTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitStreamTimeout)
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
            isUnique = true,
        )
    }

    /**
     * [SocksTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksTimeout)
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
            isUnique = true,
        )
    }

    //////////////////////////
    // DORMANT MODE OPTIONS //
    //////////////////////////

    // (IMPLEMENTED) DormantCanceledByStartup
    // (IMPLEMENTED) DormantClientTimeout
    // (IMPLEMENTED) DormantOnFirstStartup
    // (IMPLEMENTED) DormantTimeoutDisabledByIdleStreams

    /**
     * [DormantTimeoutEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantTimeoutEnabled)
     * */
    @KmpTorDsl
    public class DormantTimeoutEnabled private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DormantTimeoutEnabled",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    ////////////////////////////
    // NODE SELECTION OPTIONS //
    ////////////////////////////

    /**
     * [EntryNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#EntryNodes)
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
     * [ExcludeNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExcludeNodes)
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
     * [ExcludeExitNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExcludeExitNodes)
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
     * [ExitNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitNodes)
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

    // (IMPLEMENTED) GeoIPExcludeUnknown

    /**
     * [HSLayer2Nodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HSLayer2Nodes)
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
     * [HSLayer3Nodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HSLayer3Nodes)
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
     * [MiddleNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MiddleNodes)
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
     * [NodeFamily](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NodeFamily)
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
     * [StrictNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#StrictNodes)
     * */
    @KmpTorDsl
    public class StrictNodes private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "StrictNodes",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    //////////////////////
    //  SERVER OPTIONS  //
    //////////////////////

    /**
     * [AccountingMax](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccountingMax)
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
            isUnique = true,
        )
    }

    /**
     * [AccountingRule](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccountingRule)
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
            isUnique = true,
        )
    }

    /**
     * [AccountingStart](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccountingStart)
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
            isUnique = true,
        )
    }

    /**
     * [Address](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Address)
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
            isUnique = true,
        )
    }

    /**
     * [AddressDisableIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AddressDisableIPv6)
     * */
    @KmpTorDsl
    public class AddressDisableIPv6 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AddressDisableIPv6",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AssumeReachable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AssumeReachable)
     * */
    @KmpTorDsl
    public class AssumeReachable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AssumeReachable",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AssumeReachableIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AssumeReachableIPv6)
     * */
    @KmpTorDsl
    public class AssumeReachableIPv6 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AssumeReachableIPv6",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [BridgeRelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeRelay)
     * */
    @KmpTorDsl
    public class BridgeRelay private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgeRelay",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [BridgeDistribution](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeDistribution)
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
            isUnique = true,
        )
    }

    /**
     * [ContactInfo](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ContactInfo)
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
            isUnique = true,
        )
    }

    /**
     * [DisableOOSCheck](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableOOSCheck)
     * */
    @KmpTorDsl
    public class DisableOOSCheck private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DisableOOSCheck",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ExitPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPolicy)
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
     * [ExitPolicyRejectLocalInterfaces](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPolicyRejectLocalInterfaces)
     * */
    @KmpTorDsl
    public class ExitPolicyRejectLocalInterfaces private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitPolicyRejectLocalInterfaces",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ExitPolicyRejectPrivate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPolicyRejectPrivate)
     * */
    @KmpTorDsl
    public class ExitPolicyRejectPrivate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitPolicyRejectPrivate",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ExitRelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitRelay)
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
            isUnique = true,
        )
    }

    /**
     * [ExtendAllowPrivateAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtendAllowPrivateAddresses)
     * */
    @KmpTorDsl
    public class ExtendAllowPrivateAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtendAllowPrivateAddresses",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    // (IMPLEMENTED) GeoIPFile
    // (IMPLEMENTED) GeoIPv6File

    /**
     * [HeartbeatPeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HeartbeatPeriod)
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
            isUnique = true,
        )
    }

    /**
     * [IPv6Exit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#IPv6Exit)
     * */
    @KmpTorDsl
    public class IPv6Exit private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "IPv6Exit",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [KeyDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeyDirectory)
     * */
    @KmpTorDsl
    public class KeyDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KeyDirectory",
            default = "",
            attributes = immutableSetOf(Attribute.Directory),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [KeyDirectoryGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeyDirectoryGroupReadable)
     * */
    @KmpTorDsl
    public class KeyDirectoryGroupReadable private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "KeyDirectoryGroupReadable",
            default = AUTO,
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [MainloopStats](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MainloopStats)
     * */
    @KmpTorDsl
    public class MainloopStats private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MainloopStats",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [MaxMemInQueues](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxMemInQueues)
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
            isUnique = true,
        )
    }

    /**
     * [MaxOnionQueueDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxOnionQueueDelay)
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
            isUnique = true,
        )
    }

    /**
     * [MyFamily](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MyFamily)
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
     * [Nickname](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Nickname)
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
            isUnique = true,
        )
    }

    /**
     * [NumCPUs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumCPUs)
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
            isUnique = true,
        )
    }

    /**
     * [OfflineMasterKey](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OfflineMasterKey)
     * */
    @KmpTorDsl
    public class OfflineMasterKey private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "OfflineMasterKey",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ORPort)
     * */
    @KmpTorDsl
    public class ORPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __ORPort.name.drop(2),
            default = __ORPort.default,
            attributes = __ORPort.attributes,
            isCmdLineArg = __ORPort.isCmdLineArg,
            isUnique = __ORPort.isUnique,
        )
    }

    /**
     * [PublishServerDescriptor](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PublishServerDescriptor)
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
            isUnique = true,
        )
    }

    /**
     * [ReducedExitPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReducedExitPolicy)
     * */
    @KmpTorDsl
    public class ReducedExitPolicy private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ReducedExitPolicy",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [RefuseUnknownExits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RefuseUnknownExits)
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
            isUnique = true,
        )
    }

    /**
     * [ServerDNSAllowBrokenConfig](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSAllowBrokenConfig)
     * */
    @KmpTorDsl
    public class ServerDNSAllowBrokenConfig private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSAllowBrokenConfig",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ServerDNSAllowNonRFC953Hostnames](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSAllowNonRFC953Hostnames)
     * */
    @KmpTorDsl
    public class ServerDNSAllowNonRFC953Hostnames private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSAllowNonRFC953Hostnames",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ServerDNSDetectHijacking](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSDetectHijacking)
     * */
    @KmpTorDsl
    public class ServerDNSDetectHijacking private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSDetectHijacking",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ServerDNSRandomizeCase](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSRandomizeCase)
     * */
    @KmpTorDsl
    public class ServerDNSRandomizeCase private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSRandomizeCase",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ServerDNSResolvConfFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSResolvConfFile)
     * */
    @KmpTorDsl
    public class ServerDNSResolvConfFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSResolvConfFile",
            default = "",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ServerDNSSearchDomains](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSSearchDomains)
     * */
    @KmpTorDsl
    public class ServerDNSSearchDomains private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ServerDNSSearchDomains",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ServerDNSTestAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSTestAddresses)
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
            isUnique = true,
        )
    }

    /**
     * [ServerTransportListenAddr](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerTransportListenAddr)
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
            isUnique = true,
        )
    }

    /**
     * [ServerTransportOptions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerTransportOptions)
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
            isUnique = true,
        )
    }

    /**
     * [ServerTransportPlugin](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerTransportPlugin)
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
            isUnique = true,
        )
    }

    /**
     * [ShutdownWaitLength](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ShutdownWaitLength)
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
            isUnique = true,
        )
    }

    /**
     * [SigningKeyLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SigningKeyLifetime)
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
            isUnique = true,
        )
    }

    /**
     * [SSLKeyLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SSLKeyLifetime)
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
            isUnique = true,
        )
    }

    ////////////////////////
    // STATISTICS OPTIONS //
    ////////////////////////

    /**
     * [BridgeRecordUsageByCountry](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeRecordUsageByCountry)
     * */
    @KmpTorDsl
    public class BridgeRecordUsageByCountry private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgeRecordUsageByCountry",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [CellStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CellStatistics)
     * */
    @KmpTorDsl
    public class CellStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CellStatistics",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ConnDirectionStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConnDirectionStatistics)
     * */
    @KmpTorDsl
    public class ConnDirectionStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ConnDirectionStatistics",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [DirReqStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirReqStatistics)
     * */
    @KmpTorDsl
    public class DirReqStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirReqStatistics",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [EntryStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#EntryStatistics)
     * */
    @KmpTorDsl
    public class EntryStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "EntryStatistics",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ExitPortStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPortStatistics)
     * */
    @KmpTorDsl
    public class ExitPortStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExitPortStatistics",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [ExtraInfoStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtraInfoStatistics)
     * */
    @KmpTorDsl
    public class ExtraInfoStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "ExtraInfoStatistics",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [HiddenServiceStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceStatistics)
     * */
    @KmpTorDsl
    public class HiddenServiceStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceStatistics",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [OverloadStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OverloadStatistics)
     * */
    @KmpTorDsl
    public class OverloadStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "OverloadStatistics",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [PaddingStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PaddingStatistics)
     * */
    @KmpTorDsl
    public class PaddingStatistics private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PaddingStatistics",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    ////////////////////////////////
    //  DIRECTORY SERVER OPTIONS  //
    ////////////////////////////////

    /**
     * [DirCache](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirCache)
     * */
    @KmpTorDsl
    public class DirCache private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirCache",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [DirPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPolicy)
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
            isUnique = true,
        )
    }

    /**
     * [DirPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPort)
     * */
    @KmpTorDsl
    public class DirPort private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = __DirPort.name.drop(2),
            default = __DirPort.default,
            attributes = __DirPort.attributes,
            isCmdLineArg = __DirPort.isCmdLineArg,
            isUnique = __DirPort.isUnique,
        )
    }

    /**
     * [DirPortFrontPage](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPortFrontPage)
     * */
    @KmpTorDsl
    public class DirPortFrontPage private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirPortFrontPage",
            default = "",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [MaxConsensusAgeForDiffs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxConsensusAgeForDiffs)
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
            isUnique = true,
        )
    }

    ////////////////////////////////////////////
    //  DENIAL OF SERVICE MITIGATION OPTIONS  //
    ////////////////////////////////////////////

    /**
     * [DoSCircuitCreationEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationEnabled)
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
            isUnique = true,
        )
    }

    /**
     * [DoSCircuitCreationBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationBurst)
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
            isUnique = true,
        )
    }

    /**
     * [DoSCircuitCreationDefenseTimePeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationDefenseTimePeriod)
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
            isUnique = true,
        )
    }

    /**
     * [DoSCircuitCreationDefenseType](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationDefenseType)
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
            isUnique = true,
        )
    }

    /**
     * [DoSCircuitCreationMinConnections](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationMinConnections)
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
            isUnique = true,
        )
    }

    /**
     * [DoSCircuitCreationRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationRate)
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
            isUnique = true,
        )
    }

    /**
     * [DoSConnectionEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionEnabled)
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
            isUnique = true,
        )
    }

    /**
     * [DoSConnectionDefenseType](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionDefenseType)
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
            isUnique = true,
        )
    }

    /**
     * [DoSConnectionMaxConcurrentCount](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionMaxConcurrentCount)
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
            isUnique = true,
        )
    }

    /**
     * [DoSConnectionConnectRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionConnectRate)
     * */
    @KmpTorDsl
    public class DoSConnectionConnectRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSConnectionConnectRate",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [DoSConnectionConnectBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionConnectBurst)
     * */
    @KmpTorDsl
    public class DoSConnectionConnectBurst private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSConnectionConnectBurst",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [DoSConnectionConnectDefenseTimePeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionConnectDefenseTimePeriod)
     * */
    @KmpTorDsl
    public class DoSConnectionConnectDefenseTimePeriod private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DoSConnectionConnectDefenseTimePeriod",
            default = "0",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [DoSRefuseSingleHopClientRendezvous](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSRefuseSingleHopClientRendezvous)
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
            isUnique = true,
        )
    }

    /**
     * [HiddenServiceEnableIntroDoSDefense](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceEnableIntroDoSDefense)
     * */
    @KmpTorDsl
    public class HiddenServiceEnableIntroDoSDefense private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceEnableIntroDoSDefense",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServiceEnableIntroDoSBurstPerSec](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceEnableIntroDoSBurstPerSec)
     * */
    @KmpTorDsl
    public class HiddenServiceEnableIntroDoSBurstPerSec private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceEnableIntroDoSBurstPerSec",
            default = "200",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServiceEnableIntroDoSRatePerSec](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceEnableIntroDoSRatePerSec)
     * */
    @KmpTorDsl
    public class HiddenServiceEnableIntroDoSRatePerSec private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceEnableIntroDoSRatePerSec",
            default = "25",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServicePoWDefensesEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePoWDefensesEnabled)
     * */
    @KmpTorDsl
    public class HiddenServicePoWDefensesEnabled private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServicePoWDefensesEnabled",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServicePoWQueueRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePoWQueueRate)
     * */
    @KmpTorDsl
    public class HiddenServicePoWQueueRate private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServicePoWQueueRate",
            default = "250",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [HiddenServicePoWQueueBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePoWQueueBurst)
     * */
    @KmpTorDsl
    public class HiddenServicePoWQueueBurst private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServicePoWQueueBurst",
            default = "2500",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    /**
     * [CompiledProofOfWorkHash](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CompiledProofOfWorkHash)
     * */
    @KmpTorDsl
    public class CompiledProofOfWorkHash private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "CompiledProofOfWorkHash",
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
     * [AuthoritativeDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthoritativeDirectory)
     * */
    @KmpTorDsl
    public class AuthoritativeDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthoritativeDirectory",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [BridgeAuthoritativeDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeAuthoritativeDir)
     * */
    @KmpTorDsl
    public class BridgeAuthoritativeDir private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "BridgeAuthoritativeDir",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [V3AuthoritativeDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthoritativeDirectory)
     * */
    @KmpTorDsl
    public class V3AuthoritativeDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthoritativeDirectory",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirBadExit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirBadExit)
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
            isUnique = true,
        )
    }

    /**
     * [AuthDirMiddleOnly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirMiddleOnly)
     * */
    @KmpTorDsl
    public class AuthDirMiddleOnly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirMiddleOnly",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }


    /**
     * [AuthDirFastGuarantee](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirFastGuarantee)
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
            isUnique = true,
        )
    }

    /**
     * [AuthDirGuardBWGuarantee](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirGuardBWGuarantee)
     * */
    @KmpTorDsl
    public class AuthDirGuardBWGuarantee private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirGuardBWGuarantee",
            default = (2.0F.pow(20) * 2).toInt().toString(), // 2MBytes
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirHasIPv6Connectivity](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirHasIPv6Connectivity)
     * */
    @KmpTorDsl
    public class AuthDirHasIPv6Connectivity private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirHasIPv6Connectivity",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirInvalid](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirInvalid)
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
            isUnique = true,
        )
    }

    /**
     * [AuthDirListBadExits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirListBadExits)
     * */
    @KmpTorDsl
    public class AuthDirListBadExits private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirListBadExits",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirListMiddleOnly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirListMiddleOnly)
     * */
    @KmpTorDsl
    public class AuthDirListMiddleOnly private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirListMiddleOnly",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirMaxServersPerAddr](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirMaxServersPerAddr)
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
            isUnique = true,
        )
    }

    /**
     * [AuthDirPinKeys](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirPinKeys)
     * */
    @KmpTorDsl
    public class AuthDirPinKeys private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirPinKeys",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirReject](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirReject)
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
            isUnique = true,
        )
    }

    /**
     * [AuthDirRejectRequestsUnderLoad](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirRejectRequestsUnderLoad)
     * */
    @KmpTorDsl
    public class AuthDirRejectRequestsUnderLoad private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirRejectRequestsUnderLoad",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirBadExitCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirBadExitCCs)
     * */
    @KmpTorDsl
    public class AuthDirBadExitCCs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirBadExitCCs",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirInvalidCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirInvalidCCs)
     * */
    @KmpTorDsl
    public class AuthDirInvalidCCs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirInvalidCCs",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirMiddleOnlyCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirMiddleOnlyCCs)
     * */
    @KmpTorDsl
    public class AuthDirMiddleOnlyCCs private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirMiddleOnlyCCs",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }


    /**
     * [AuthDirRejectCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirRejectCCs)
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
            isUnique = true,
        )
    }

    /**
     * [AuthDirSharedRandomness](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirSharedRandomness)
     * */
    @KmpTorDsl
    public class AuthDirSharedRandomness private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirSharedRandomness",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirTestEd25519LinkKeys](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirTestEd25519LinkKeys)
     * */
    @KmpTorDsl
    public class AuthDirTestEd25519LinkKeys private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirTestEd25519LinkKeys",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirTestReachability](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirTestReachability)
     * */
    @KmpTorDsl
    public class AuthDirTestReachability private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirTestReachability",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirVoteGuard](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuard)
     * */
    @KmpTorDsl
    public class AuthDirVoteGuard private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirVoteGuard",
            default = "",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirVoteGuardBwThresholdFraction](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuardBwThresholdFraction)
     * */
    @KmpTorDsl
    public class AuthDirVoteGuardBwThresholdFraction private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirVoteGuardBwThresholdFraction",
            default = "0.750000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirVoteGuardGuaranteeTimeKnown](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuardGuaranteeTimeKnown)
     * */
    @KmpTorDsl
    public class AuthDirVoteGuardGuaranteeTimeKnown private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirVoteGuardGuaranteeTimeKnown",
            default = 8.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirVoteGuardGuaranteeWFU](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuardGuaranteeWFU)
     * */
    @KmpTorDsl
    public class AuthDirVoteGuardGuaranteeWFU private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirVoteGuardGuaranteeWFU",
            default = "0.980000",
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirVoteStableGuaranteeMinUptime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteStableGuaranteeMinUptime)
     * */
    @KmpTorDsl
    public class AuthDirVoteStableGuaranteeMinUptime private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirVoteStableGuaranteeMinUptime",
            default = 30.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [AuthDirVoteStableGuaranteeMTBF](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteStableGuaranteeMTBF)
     * */
    @KmpTorDsl
    public class AuthDirVoteStableGuaranteeMTBF private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "AuthDirVoteStableGuaranteeMTBF",
            default = 5.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [BridgePassword](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgePassword)
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
            isUnique = true,
        )
    }

    /**
     * [ConsensusParams](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConsensusParams)
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
     * [DirAllowPrivateAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirAllowPrivateAddresses)
     * */
    @KmpTorDsl
    public class DirAllowPrivateAddresses private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "DirAllowPrivateAddresses",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [GuardfractionFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GuardfractionFile)
     * */
    @KmpTorDsl
    public class GuardfractionFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "GuardfractionFile",
            default = "",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [MinMeasuredBWsForAuthToIgnoreAdvertised](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MinMeasuredBWsForAuthToIgnoreAdvertised)
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
            isUnique = true,
        )
    }

    /**
     * [MinUptimeHidServDirectoryV2](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MinUptimeHidServDirectoryV2)
     * */
    @KmpTorDsl
    public class MinUptimeHidServDirectoryV2 private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "MinUptimeHidServDirectoryV2",
            default = 96.hours.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [RecommendedClientVersions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RecommendedClientVersions)
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
            isUnique = true,
        )
    }

    /**
     * [RecommendedServerVersions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RecommendedServerVersions)
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
            isUnique = true,
        )
    }

    /**
     * [RecommendedVersions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RecommendedVersions)
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
            isUnique = true,
        )
    }

    /**
     * [V3AuthDistDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthDistDelay)
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
            isUnique = true,
        )
    }

    /**
     * [V3AuthNIntervalsValid](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthNIntervalsValid)
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
            isUnique = true,
        )
    }

    /**
     * [V3AuthUseLegacyKey](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthUseLegacyKey)
     * */
    @KmpTorDsl
    public class V3AuthUseLegacyKey private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3AuthUseLegacyKey",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [V3AuthVoteDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthVoteDelay)
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
            isUnique = true,
        )
    }

    /**
     * [V3AuthVotingInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthVotingInterval)
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
            isUnique = true,
        )
    }

    /**
     * [V3BandwidthsFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3BandwidthsFile)
     * */
    @KmpTorDsl
    public class V3BandwidthsFile private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "V3BandwidthsFile",
            default = "",
            attributes = immutableSetOf(Attribute.File),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [VersioningAuthoritativeDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VersioningAuthoritativeDirectory)
     * */
    @KmpTorDsl
    public class VersioningAuthoritativeDirectory private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "VersioningAuthoritativeDirectory",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    //////////////////////////////
    //  HIDDEN SERVICE OPTIONS  //
    //////////////////////////////

    // (IMPLEMENTED) HiddenServiceAllowUnknownPorts
    // (IMPLEMENTED) HiddenServiceDir
    // (IMPLEMENTED) HiddenServiceDirGroupReadable

    /**
     * [HiddenServiceExportCircuitID](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceExportCircuitID)
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
     * [HiddenServiceOnionBalanceInstance](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceOnionBalanceInstance)
     * */
    @KmpTorDsl
    public class HiddenServiceOnionBalanceInstance private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceOnionBalanceInstance",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = false,
        )
    }

    // (IMPLEMENTED) HiddenServiceMaxStreams
    // (IMPLEMENTED) HiddenServiceMaxStreamsCloseCircuit
    // (IMPLEMENTED) HiddenServiceNumIntroductionPoints
    // (IMPLEMENTED) HiddenServicePort
    // (IMPLEMENTED) HiddenServiceVersion

    /**
     * [HiddenServiceSingleHopMode](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceSingleHopMode)
     * */
    @KmpTorDsl
    public class HiddenServiceSingleHopMode private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceSingleHopMode",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [HiddenServiceNonAnonymousMode](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceNonAnonymousMode)
     * */
    @KmpTorDsl
    public class HiddenServiceNonAnonymousMode private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "HiddenServiceNonAnonymousMode",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [PublishHidServDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PublishHidServDescriptors)
     * */
    @KmpTorDsl
    public class PublishHidServDescriptors private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "PublishHidServDescriptors",
            default = true.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    ///////////////////////////////
    //  TESTING NETWORK OPTIONS  //
    ///////////////////////////////

    /**
     * [TestingTorNetwork](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingTorNetwork)
     * */
    @KmpTorDsl
    public class TestingTorNetwork private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingTorNetwork",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = true,
            isUnique = true,
        )
    }

    /**
     * [TestingAuthDirTimeToLearnReachability](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingAuthDirTimeToLearnReachability)
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
            isUnique = true,
        )
    }

    /**
     * [TestingAuthKeyLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingAuthKeyLifetime)
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
            isUnique = true,
        )
    }

    /**
     * [TestingAuthKeySlop](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingAuthKeySlop)
     * */
    @KmpTorDsl
    public class TestingAuthKeySlop private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingAuthKeySlop",
            default = 3.hours.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingBridgeBootstrapDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingBridgeBootstrapDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingBridgeDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingBridgeDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingClientConsensusDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingClientConsensusDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingClientDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingClientDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingClientMaxIntervalWithoutRequest](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingClientMaxIntervalWithoutRequest)
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
            isUnique = true,
        )
    }

    /**
     * [TestingDirAuthVoteExit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteExit)
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
            isUnique = true,
        )
    }

    /**
     * [TestingDirAuthVoteExitIsStrict](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteExitIsStrict)
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteExitIsStrict private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteExitIsStrict",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingDirAuthVoteGuard](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteGuard)
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
            isUnique = true,
        )
    }

    /**
     * [TestingDirAuthVoteGuardIsStrict](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteGuardIsStrict)
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteGuardIsStrict private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteGuardIsStrict",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingDirAuthVoteHSDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteHSDir)
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
            isUnique = true,
        )
    }

    /**
     * [TestingDirAuthVoteHSDirIsStrict](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteHSDirIsStrict)
     * */
    @KmpTorDsl
    public class TestingDirAuthVoteHSDirIsStrict private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingDirAuthVoteHSDirIsStrict",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingDirConnectionMaxStall](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirConnectionMaxStall)
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
            isUnique = true,
        )
    }

    /**
     * [TestingEnableCellStatsEvent](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingEnableCellStatsEvent)
     * */
    @KmpTorDsl
    public class TestingEnableCellStatsEvent private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingEnableCellStatsEvent",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingEnableConnBwEvent](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingEnableConnBwEvent)
     * */
    @KmpTorDsl
    public class TestingEnableConnBwEvent private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingEnableConnBwEvent",
            default = false.byte.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingLinkCertLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingLinkCertLifetime)
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
            isUnique = true,
        )
    }

    /**
     * [TestingLinkKeySlop](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingLinkKeySlop)
     * */
    @KmpTorDsl
    public class TestingLinkKeySlop private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingLinkKeySlop",
            default = 3.hours.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingMinExitFlagThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingMinExitFlagThreshold)
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
            isUnique = true,
        )
    }

    /**
     * [TestingMinFastFlagThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingMinFastFlagThreshold)
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
            isUnique = true,
        )
    }

    /**
     * [TestingMinTimeToReportBandwidth](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingMinTimeToReportBandwidth)
     * */
    @KmpTorDsl
    public class TestingMinTimeToReportBandwidth private constructor(): Setting.Builder(
        keyword = Companion,
    ) {
        public companion object: Keyword(
            name = "TestingMinTimeToReportBandwidth",
            default = 1.days.inWholeSeconds.toString(),
            attributes = emptySet(),
            isCmdLineArg = false,
            isUnique = true,
        )
    }

    /**
     * [TestingServerConsensusDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingServerConsensusDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingServerDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingServerDownloadInitialDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingSigningKeySlop](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingSigningKeySlop)
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
            isUnique = true,
        )
    }

    /**
     * [TestingV3AuthInitialDistDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthInitialDistDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingV3AuthInitialVoteDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthInitialVoteDelay)
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
            isUnique = true,
        )
    }

    /**
     * [TestingV3AuthInitialVotingInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthInitialVotingInterval)
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
            isUnique = true,
        )
    }

    /**
     * [TestingV3AuthVotingStartOffset](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthVotingStartOffset)
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
            isUnique = true,
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

        /** @suppress */
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

        /** @suppress */
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

        /** @suppress */
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

        /** @suppress */
        public override fun equals(other: Any?): Boolean = other is Setting && other.items.first() == items.first()
        /** @suppress */
        public override fun hashCode(): Int = 17 * 31 + items.first().hashCode()
        /** @suppress */
        public override fun toString(): String = buildString {
            items.joinTo(this, separator = "\n")
        }

        /**
         * Returns a new [Setting] if it is a *Port that is
         * not configured as disabled or auto.
         *
         * @suppress
         * */
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
     * [Docs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc)
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

        /** @suppress */
        public final override fun equals(other: Any?): Boolean = other is Keyword && other.name == name
        /** @suppress */
        public final override fun hashCode(): Int = 21 * 31 + name.hashCode()
        /** @suppress */
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

    /** @suppress */
    public override fun equals(other: Any?): Boolean = other is TorConfig && other.settings == settings
    /** @suppress */
    public override fun hashCode(): Int = 5 * 42 + settings.hashCode()
    /** @suppress */
    public override fun toString(): String = buildString {
        settings.joinTo(this, separator = "\n")
    }
}
