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
@file:Suppress("FunctionName", "PropertyName", "ConvertSecondaryConstructorToPrimary")

package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.internal.configure
import io.matthewnelson.kmp.tor.runtime.core.internal.toUnixSocketPath
import io.matthewnelson.kmp.tor.runtime.core.util.isAvailableAsync
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorOption] containing
 * the attribute(s) [TorOption.Attribute.UNIX_SOCKET] and/or
 * [TorOption.Attribute.PORT].
 * */
@KmpTorDsl
public abstract class BuilderScopePort: TorSetting.BuilderScope {

    private constructor(option: TorOption): super(option, INIT) {
        // Always instantiate with `true` added.
        // Can be disabled if desired.
        extras[EXTRA_REASSIGNABLE] = true
    }

    public companion object {

        /**
         * A [TorSetting.extras] key for TCP port configurations used
         * by `kmp-tor:runtime`.
         *
         * @see [BuilderScopePort.reassignable]
         * */
        public const val EXTRA_REASSIGNABLE: String = "REASSIGNABLE"
    }

    /**
     * A DSL builder scope for [TorOption.__ControlPort] and
     * [TorOption.ControlPort].
     * */
    @KmpTorDsl
    public class Control: BuilderScopePort {

        private constructor(option: TorOption.__ControlPort): super(option)
        private constructor(option: TorOption.ControlPort): super(option)

        @KmpTorDsl
        public override fun auto(): Control = super.auto() as Control

        @KmpTorDsl
        public override fun disable(): Control = super.disable() as Control

        @KmpTorDsl
        public override fun port(
            value: Port.Ephemeral,
        ): Control = super.port(value) as Control

        @KmpTorDsl
        @Throws(UnsupportedOperationException::class)
        public override fun unixSocket(
            value: File,
        ): Control = super.unixSocket(value) as Control

        @KmpTorDsl
        public override fun reassignable(
            allow: Boolean,
        ): Control = super.reassignable(allow) as Control

        @KmpTorDsl
        public override fun flagsUnix(
            block: ThisBlock<FlagsBuilderUnix>,
        ): Control = super.flagsUnix(block) as Control

        internal companion object {

            @JvmSynthetic
            internal fun of(
                isNonPersistent: Boolean,
            ): Control = if (isNonPersistent) {
                Control(TorOption.__ControlPort)
            } else {
                Control(TorOption.ControlPort)
            }
        }
    }

    /**
     * A DSL builder scope for [TorOption.__DNSPort] and [TorOption.DNSPort].
     * */
    @KmpTorDsl
    public class DNS: BuilderScopePort {

        private constructor(option: TorOption.__DNSPort): super(option)
        private constructor(option: TorOption.DNSPort): super(option)

        @KmpTorDsl
        public override fun auto(): DNS = super.auto() as DNS

        @KmpTorDsl
        public override fun disable(): DNS = super.disable() as DNS

        @KmpTorDsl
        public override fun port(
            value: Port.Ephemeral,
        ): DNS = super.port(value) as DNS

        @KmpTorDsl
        public override fun reassignable(
            allow: Boolean,
        ): DNS = super.reassignable(allow) as DNS

        @KmpTorDsl
        public override fun flagsIsolation(
            block: ThisBlock<FlagsBuilderIsolation>,
        ): DNS = super.flagsIsolation(block) as DNS

        internal companion object {

            @JvmSynthetic
            internal fun of(
                isNonPersistent: Boolean,
            ): DNS  = if (isNonPersistent) {
                DNS(TorOption.__DNSPort)
            } else {
                DNS(TorOption.DNSPort)
            }
        }
    }

    /**
     * A DSL builder scope for [TorOption.__HTTPTunnelPort] and
     * [TorOption.HTTPTunnelPort].
     * */
    @KmpTorDsl
    public class HTTPTunnel: BuilderScopePort {

        private constructor(option: TorOption.__HTTPTunnelPort): super(option)
        private constructor(option: TorOption.HTTPTunnelPort): super(option)

        @KmpTorDsl
        public override fun auto(): HTTPTunnel = super.auto() as HTTPTunnel

        @KmpTorDsl
        public override fun disable(): HTTPTunnel = super.disable() as HTTPTunnel

        @KmpTorDsl
        public override fun port(
            value: Port.Ephemeral,
        ): HTTPTunnel = super.port(value) as HTTPTunnel

        @KmpTorDsl
        public override fun reassignable(
            allow: Boolean,
        ): HTTPTunnel = super.reassignable(allow) as HTTPTunnel

        @KmpTorDsl
        public override fun flagsIsolation(
            block: ThisBlock<FlagsBuilderIsolation>,
        ): HTTPTunnel = super.flagsIsolation(block) as HTTPTunnel

        internal companion object {

            @JvmSynthetic
            internal fun of(
                isNonPersistent: Boolean,
            ): HTTPTunnel  = if (isNonPersistent) {
                HTTPTunnel(TorOption.__HTTPTunnelPort)
            } else {
                HTTPTunnel(TorOption.HTTPTunnelPort)
            }
        }
    }

    /**
     * A DSL builder scope for [TorOption.__SocksPort] and
     * [TorOption.SocksPort].
     * */
    @KmpTorDsl
    public class Socks: BuilderScopePort {

        private constructor(option: TorOption.__SocksPort): super(option)
        private constructor(option: TorOption.SocksPort): super(option)

        @KmpTorDsl
        public override fun auto(): Socks = super.auto() as Socks

        @KmpTorDsl
        public override fun disable(): Socks = super.disable() as Socks

        @KmpTorDsl
        public override fun port(
            value: Port.Ephemeral,
        ): Socks = super.port(value) as Socks

        @KmpTorDsl
        public override fun reassignable(
            allow: Boolean,
        ): Socks = super.reassignable(allow) as Socks

        @KmpTorDsl
        @Throws(UnsupportedOperationException::class)
        public override fun unixSocket(
            value: File,
        ): Socks = super.unixSocket(value) as Socks

        @KmpTorDsl
        public override fun flagsIsolation(
            block: ThisBlock<FlagsBuilderIsolation>,
        ): Socks = super.flagsIsolation(block) as Socks

        @KmpTorDsl
        public override fun flagsSocks(
            block: ThisBlock<FlagsBuilderSocks>,
        ): Socks = super.flagsSocks(block) as Socks

        @KmpTorDsl
        public override fun flagsUnix(
            block: ThisBlock<FlagsBuilderUnix>,
        ): Socks = super.flagsUnix(block) as Socks

        internal companion object {

            @JvmSynthetic
            internal fun of(
                isNonPersistent: Boolean,
            ): Socks = if (isNonPersistent) {
                Socks(TorOption.__SocksPort)
            } else {
                Socks(TorOption.SocksPort)
            }
        }
    }

    /**
     * A DSL builder scope for [TorOption.__TransPort] and
     * [TorOption.TransPort].
     *
     * **NOTE:** This builder scope is **only** available for
     * unix-like hosts. [UnsupportedOperationException] may
     * be thrown when attempting to instantiate it.
     * */
    @KmpTorDsl
    public class Trans: BuilderScopePort {

        private constructor(option: TorOption.__TransPort): super(option)
        private constructor(option: TorOption.TransPort): super(option)

        @KmpTorDsl
        public override fun auto(): Trans = super.auto() as Trans

        @KmpTorDsl
        public override fun disable(): Trans = super.disable() as Trans

        @KmpTorDsl
        public override fun port(
            value: Port.Ephemeral,
        ): Trans = super.port(value) as Trans

        @KmpTorDsl
        public override fun reassignable(
            allow: Boolean,
        ): Trans = super.reassignable(allow) as Trans

        @KmpTorDsl
        public override fun flagsIsolation(
            block: ThisBlock<FlagsBuilderIsolation>,
        ): Trans = super.flagsIsolation(block) as Trans

        internal companion object {

            @JvmSynthetic
            internal fun of(
                isNonPersistent: Boolean,
            ): Trans = if (isNonPersistent) {
                Trans(TorOption.__TransPort)
            } else {
                Trans(TorOption.TransPort)
            }
        }
    }

    // BuilderScopePort functions below. override and change
    // visibility to `public` to expose functionality to the
    // final builder class.
    private val _flagsIsolation = LinkedHashSet<String>(1, 1.0f)
    private val _flagsSocks = LinkedHashSet<String>(1, 1.0f)
    private val _flagsUnix = LinkedHashSet<String>(1, 1.0f)

    /**
     * Sets the [argument] to `auto`, indicating that tor
     * should pick a port value for this [TorOption].
     * */
    @KmpTorDsl
    protected open fun auto(): BuilderScopePort {
        argument = TorOption.AUTO
        return this
    }

    /**
     * Sets the [argument] to `0`, indicating that tor will
     * not utilize this [TorOption].
     * */
    @KmpTorDsl
    protected open fun disable(): BuilderScopePort {
        argument = Port.ZERO.toString()
        return this
    }

    // TODO: IPAddress. Issue #313

    /**
     * Sets the [argument] to the specified port.
     *
     * @see [reassignable]
     * */
    @KmpTorDsl
    protected open fun port(
        value: Port.Ephemeral,
    ): BuilderScopePort {
        argument = value.toString()
        return this
    }

    /**
     * In the event a configured [Port] is unavailable on the host device,
     * tor will fail to start.
     *
     * This option will add to the [TorSetting.extras] map for this builder
     * result, a `true` or `false` boolean value for the [EXTRA_REASSIGNABLE]
     * key. If `true`, `kmp-tor:runtime` will check [Port.isAvailableAsync]
     * just prior to tor process start and, if unavailable, reassign the
     * argument "auto".
     *
     * `kmp-tor:runtime` will only use this if a [port] has been declared.
     * Otherwise, it is ignored.
     *
     * By default, `true` is always added upon [BuilderScopePort] instantiation,
     * but can be disabled by passing `false` to this function.
     * */
    @KmpTorDsl
    protected open fun reassignable(
        allow: Boolean,
    ): BuilderScopePort {
        extras[EXTRA_REASSIGNABLE] = allow
        return this
    }

    /**
     * For a [TorOption] which can be configured to use Unix Sockets,
     * containing the attribute [TorOption.Attribute.UNIX_SOCKET].
     *
     * Unix sockets should always be preferred over using a TCP port
     * (especially for the [TorOption.ControlPort] and its alternative
     * option names) as Unix Sockets are unaffected by whether the
     * host TCP layer is constrained (e.g. airplane mode, or a VPN).
     *
     * This sets the [argument] to the expressed file path, which will
     * be formatted as `unix:\"${file-path}\"`. The [value] passed is
     * always sanitized via [File.absoluteFile] + [File.normalize]
     * before applying final formatting.
     *
     * e.g.
     *
     *     try {
     *         unixSocket("/path/to/my/ctrl.sock".toFile())
     *     } catch(_: UnsupportedOperationException) {
     *         auto()
     *     }
     *
     * @throws [UnsupportedOperationException] when:
     *   - Is Windows (tor does not support Unix Sockets on windows).
     *   - Is Java 15 or below (Jvm only, Android is always available).
     *   - Configured path exceeds `104` characters in length.
     *   - Configured path is multiple lines.
     * */
    @KmpTorDsl
    @Throws(UnsupportedOperationException::class)
    protected open fun unixSocket(
        value: File,
    ): BuilderScopePort {
        val path = value.toUnixSocketPath()
        argument = path
        return this
    }

    /**
     * Adds isolation flags to the [TorSetting.LineItem.optionals]
     * of this builder result.
     *
     * @see [FlagsBuilderIsolation]
     * */
    @KmpTorDsl
    protected open fun flagsIsolation(
        block: ThisBlock<FlagsBuilderIsolation>,
    ): BuilderScopePort {
        FlagsBuilderIsolation.configure(_flagsIsolation, block)
        return this
    }

    /**
     * Adds socks flags to the [TorSetting.LineItem.optionals]
     * of this builder result.
     *
     * @see [FlagsBuilderSocks]
     * */
    @KmpTorDsl
    protected open fun flagsSocks(
        block: ThisBlock<FlagsBuilderSocks>,
    ): BuilderScopePort {
        FlagsBuilderSocks.configure(_flagsSocks, block)
        return this
    }

    /**
     * Adds unix flags to the [TorSetting.LineItem.optionals]
     * of this builder result.
     *
     * **NOTE:** flags will only be applied if the final [argument]
     * is configured as a [unixSocket]. They can be configured, but
     * may not be added.
     *
     * @see [FlagsBuilderUnix]
     * */
    @KmpTorDsl
    protected open fun flagsUnix(
        block: ThisBlock<FlagsBuilderUnix>,
    ): BuilderScopePort {
        FlagsBuilderUnix.configure(this is Control, _flagsUnix, block)
        return this
    }

    /**
     * Configure the desired [TorOption] with Isolation Flags, as described
     * in [tor-man#SocksPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksPort).
     *
     * Configurability is as follows:
     *  - `null`: no action (the default).
     *  - `true`: add the flag if not present.
     *  - `false`: remove the flag if present.
     *
     * e.g.
     *
     *     val setting = TorOption.__DNSPort.asSetting {
     *         auto()
     *
     *         flagsIsolation {
     *             IsolateClientAddr = true
     *             IsolateSOCKSAuth = true
     *             KeepAliveIsolateSOCKSAuth = true
     *         }
     *         flagsIsolation {
     *             // Remove what was just added
     *             KeepAliveIsolateSOCKSAuth = false
     *         }
     *     }
     *
     *     println(setting.items.first().optionals)
     *     // [IsolateClientAddr, IsolateSOCKSAuth]
     * */
    @KmpTorDsl
    public class FlagsBuilderIsolation private constructor() {

        @JvmField
        public var IsolateClientAddr: Boolean? = null
        @JvmField
        public var IsolateSOCKSAuth: Boolean? = null
        @JvmField
        public var IsolateClientProtocol: Boolean? = null
        @JvmField
        public var IsolateDestPort: Boolean? = null
        @JvmField
        public var IsolateDestAddr: Boolean? = null
        @JvmField
        public var KeepAliveIsolateSOCKSAuth: Boolean? = null

        private var sessionGroupId: Int? = null

        /**
         * Declaring an id greater than or equal to 0 will add
         * the flag.
         *
         * Declaring an id less than 0 will remove flag if present.
         * */
        @KmpTorDsl
        public fun SessionGroup(id: Int): FlagsBuilderIsolation {
            sessionGroupId = id
            return this
        }

        internal companion object {

            @JvmSynthetic
            internal fun configure(
                flags: LinkedHashSet<String>,
                block: ThisBlock<FlagsBuilderIsolation>,
            ) {
                val b = FlagsBuilderIsolation().apply(block)

                b.IsolateClientAddr.configure(flags, "IsolateClientAddr")
                b.IsolateSOCKSAuth.configure(flags, "IsolateSOCKSAuth")
                b.IsolateClientProtocol.configure(flags, "IsolateClientProtocol")
                b.IsolateDestPort.configure(flags, "IsolateDestPort")
                b.IsolateDestAddr.configure(flags, "IsolateDestAddr")
                b.KeepAliveIsolateSOCKSAuth.configure(flags, "KeepAliveIsolateSOCKSAuth")

                b.sessionGroupId?.let { id ->
                    val flag = "SessionGroup"
                    // always remove
                    flags.firstOrNull { it.startsWith(flag) }?.let { flags.remove(it) }
                    // only add if positive
                    if (id >= 0) flags.add("$flag=$id")
                }
            }
        }
    }

    /**
     * Configure the desired [TorOption] with Socks Flags, as described
     * in [tor-man#OtherSocksPortFlags](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OtherSocksPortFlags).
     *
     * Configurability is as follows:
     *  - `null`: no action (the default).
     *  - `true`: add the flag if not present.
     *  - `false`: remove the flag if present.
     *
     * e.g.
     *
     *     val setting = TorOption.__SocksPort.asSetting {
     *         auto()
     *
     *         flagsSocks {
     *             PreferIPv6 = true
     *             OnionTrafficOnly = true
     *         }
     *         flagsSocks {
     *             // Remove what was just added
     *             OnionTrafficOnly = false
     *         }
     *     }
     *
     *     println(setting.items.first().optionals)
     *     // [PreferIPv6]
     * */
    @KmpTorDsl
    public class FlagsBuilderSocks private constructor() {

        @JvmField
        public var NoIPv4Traffic: Boolean? = null
        @JvmField
        public var IPv6Traffic: Boolean? = null
        @JvmField
        public var PreferIPv6: Boolean? = null
        @JvmField
        public var NoDNSRequest: Boolean? = null
        @JvmField
        public var NoOnionTraffic: Boolean? = null
        @JvmField
        public var OnionTrafficOnly: Boolean? = null
        @JvmField
        public var CacheIPv4DNS: Boolean? = null
        @JvmField
        public var CacheIPv6DNS: Boolean? = null
        @JvmField
        public var CacheDNS: Boolean? = null
        @JvmField
        public var UseIPv4Cache: Boolean? = null
        @JvmField
        public var UseIPv6Cache: Boolean? = null
        @JvmField
        public var UseDNSCache: Boolean? = null
        @JvmField
        public var PreferIPv6Automap: Boolean? = null
        @JvmField
        public var PreferSOCKSNoAuth: Boolean? = null

        internal companion object {

            @JvmSynthetic
            internal fun configure(
                flags: LinkedHashSet<String>,
                block: ThisBlock<FlagsBuilderSocks>,
            ) {
                val b = FlagsBuilderSocks().apply(block)

                b.NoIPv4Traffic.configure(flags, "NoIPv4Traffic")
                b.IPv6Traffic.configure(flags, "IPv6Traffic")
                b.PreferIPv6.configure(flags, "PreferIPv6")
                b.NoDNSRequest.configure(flags, "NoDNSRequest")
                b.NoOnionTraffic.configure(flags, "NoOnionTraffic")
                b.OnionTrafficOnly.configure(flags, "OnionTrafficOnly")
                b.CacheIPv4DNS.configure(flags, "CacheIPv4DNS")
                b.CacheIPv6DNS.configure(flags, "CacheIPv6DNS")
                b.CacheDNS.configure(flags, "CacheDNS")
                b.UseIPv4Cache.configure(flags, "UseIPv4Cache")
                b.UseIPv6Cache.configure(flags, "UseIPv6Cache")
                b.UseDNSCache.configure(flags, "UseDNSCache")
                b.PreferIPv6Automap.configure(flags, "PreferIPv6Automap")
                b.PreferSOCKSNoAuth.configure(flags, "PreferSOCKSNoAuth")
            }
        }
    }

    /**
     * Configure the desired [TorOption] with Unix Flags, as described
     * in [tor-man#OtherSocksPortFlags](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OtherSocksPortFlags)
     * and [tor-man#ControlPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPort).
     *
     * Configurability is as follows:
     *  - `null`: no action (the default).
     *  - `true`: add the flag if not present.
     *  - `false`: remove the flag if present.
     *
     * e.g.
     *
     *     val setting = TorOption.__SocksPort.asSetting {
     *         try {
     *             unixSocket("/path/to/my/socks.sock".toFile())
     *         } catch(_: UnsupportedOperationException) {
     *             // unix flags will not be applied in this case.
     *             auto()
     *         }
     *
     *         flagsUnix {
     *             GroupWritable = true
     *             WorldWritable = true
     *         }
     *         flagsUnix {
     *             // Remove what was just added
     *             WorldWritable = false
     *         }
     *     }
     *
     *     // IF unixSocket is supported
     *     println(setting.items.first().argument)
     *     println(setting.items.first().optionals)
     *     // unix:"/path/to/my/socks.sock"
     *     // [GroupWritable]
     *
     *     // IF unixSocket is not supported
     *     println(setting.items.first().argument)
     *     println(setting.items.first().optionals)
     *     // auto
     *     // []
     *
     * @see [BuilderScopePort.flagsUnix]
     * */
    @KmpTorDsl
    public class FlagsBuilderUnix private constructor() {

        @JvmField
        public var GroupWritable: Boolean? = null
        @JvmField
        public var WorldWritable: Boolean? = null

        /**
         * Only applicable for [Control.flagsUnix], otherwise
         * is ignored if configured.
         * */
        @JvmField
        public var RelaxDirModeCheck: Boolean? = null

        internal companion object {

            @JvmSynthetic
            internal fun configure(
                isControl: Boolean,
                flags: LinkedHashSet<String>,
                block: ThisBlock<FlagsBuilderUnix>,
            ) {
                val b = FlagsBuilderUnix().apply(block)

                b.GroupWritable.configure(flags, "GroupWritable")
                b.WorldWritable.configure(flags, "WorldWritable")

                if (!isControl) return
                b.RelaxDirModeCheck.configure(flags, "RelaxDirModeCheck")
            }
        }
    }

    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    internal final override fun build(): TorSetting {
        if (argument != Port.ZERO.toString()) {
            // Do not add flags if disabled

            optionals.addAll(_flagsIsolation)
            optionals.addAll(_flagsSocks)

            if (argument.startsWith("unix:")) {
                optionals.addAll(_flagsUnix)
            }
        }

        return try {
            super.build()
        } finally {
            optionals.clear()
        }
    }
}
