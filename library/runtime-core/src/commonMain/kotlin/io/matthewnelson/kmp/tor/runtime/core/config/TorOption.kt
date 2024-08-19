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
@file:Suppress("ClassName", "FunctionName", "ConvertSecondaryConstructorToPrimary")

package io.matthewnelson.kmp.tor.runtime.core.config

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeAutoBoolean
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeAutoBoolean.Companion.toBuilderScopeAutoBoolean
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeHS
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeOwningControllerProcess
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopePort
import io.matthewnelson.kmp.tor.runtime.core.ctrl.TorCmd
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
 * Option models for those specified in the [tor-manual](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc)
 * and [control-spec](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options).
 *
 * @see [TorConfig2]
 * @see [TorSetting]
 * @see [ConfigurableContract]
 * @see [entries]
 * @see [valueOf]
 * @see [valueOfOrNull]
 * */
public abstract class TorOption: Comparable<TorOption>, CharSequence {

    /**
     * Tor's default value for this option.
     * */
    @JvmField
    public val default: String

    /**
     * `kmp-tor` specific list of attributes used for filtering
     * and sorting.
     *
     * @see [Attribute]
     * */
    @JvmField
    public val attributes: Set<Attribute>

    /**
     * `kmp-tor:runtime` specific flag used to determine if a setting
     * for this option should be used as an argument when starting
     * the tor process, or via [TorCmd.Config.Load].
     * */
    @JvmField
    public val isCmdLineArg: Boolean

    /**
     * If expression of this option is allowed multiple times
     * within a [Set] or not.
     * */
    @JvmField
    public val isUnique: Boolean

    /**
     * The "name" of this option.
     *
     * e.g.
     *
     *     println(TorOption.DisableNetwork)
     *     // DisableNetwork
     *     println(TorOption.DisableNetwork.name)
     *     // DisableNetwork
     * */
    @get:JvmName("name")
    public val name: String get() = toString()

    private constructor(
        default: Any,
        attributes: Set<Attribute>,
        isCmdLineArg: Boolean,
        isUnique: Boolean,
    ) {
        this.default = default.toString()
        this.attributes = attributes.toImmutableSet()
        this.isCmdLineArg = isCmdLineArg
        this.isUnique = isUnique
    }

    ///////////////////////
    //  GENERAL OPTIONS  //
    ///////////////////////

    /**
     * [tor-man#AccelDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccelDir)
     * */
    public data object AccelDir: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.DIRECTORY),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableDirectory {

        @JvmStatic
        public fun asSetting(directory: File): TorSetting = buildContract(directory)
    }

    /**
     * [tor-man#AccelName](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccelName)
     * */
    public data object AccelName: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    )

    /**
     * [tor-man#AlternateBridgeAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AlternateBridgeAuthority)
     * */
    public data object AlternateBridgeAuthority: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#AlternateDirAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AlternateDirAuthority)
     * */
    public data object AlternateDirAuthority: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#AvoidDiskWrites](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AvoidDiskWrites)
     * */
    public data object AvoidDiskWrites: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#BandwidthBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BandwidthBurst)
     * */
    public data object BandwidthBurst: TorOption(
        default = (2.0F.pow(30)).toInt(), // 1 GByte
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#BandwidthRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BandwidthRate)
     * */
    public data object BandwidthRate: TorOption(
        default = (2.0F.pow(30)).toInt(), // 1 GByte
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#CacheDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CacheDirectory)
     * */
    public data object CacheDirectory: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.DIRECTORY),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableDirectory {

        @JvmStatic
        public fun asSetting(directory: File): TorSetting = buildContract(directory)
    }

    /**
     * [tor-man#CacheDirectoryGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CacheDirectoryGroupReadable)
     * */
    public data object CacheDirectoryGroupReadable: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#CircuitPriorityHalflife](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitPriorityHalflife)
     * */
    public data object CircuitPriorityHalflife: TorOption(
        default = "-1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ClientTransportPlugin](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientTransportPlugin)
     * */
    public data object ClientTransportPlugin: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#ConfluxEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConfluxEnabled)
     * */
    public data object ConfluxEnabled: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#ConfluxClientUX](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConfluxClientUX)
     * */
    public data object ConfluxClientUX: TorOption(
        default = "throughput",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ConnLimit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConnLimit)
     * */
    public data object ConnLimit: TorOption(
        default = 1000,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ConstrainedSockets](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConstrainedSockets)
     * */
    public data object ConstrainedSockets: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ConstrainedSockSize](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConstrainedSockSize)
     * */
    public data object ConstrainedSockSize: TorOption(
        default = (2.0F.pow(13)).toInt(), // 8192 bytes
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ControlPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPort)
     *
     * @see [__ControlPort]
     * */
    public data object ControlPort: TorOption(
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT, Attribute.UNIX_SOCKET),
        isCmdLineArg = true,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.Control> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.Control>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.Control = BuilderScopePort.Control.of(isNonPersistent = false)
    }

    /**
     * [tor-man#ControlPortFileGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPortFileGroupReadable)
     * */
    public data object ControlPortFileGroupReadable: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ControlPortWriteToFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPortWriteToFile)
     * */
    public data object ControlPortWriteToFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#ControlSocket](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlSocket)
     * */
    public data object ControlSocket: TorOption(
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.UNIX_SOCKET),
        isCmdLineArg = true,
        isUnique = true,
    )

    /**
     * [tor-man#ControlSocketsGroupWritable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlSocketsGroupWritable)
     * */
    public data object ControlSocketsGroupWritable: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#CookieAuthentication](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CookieAuthentication)
     * */
    public data object CookieAuthentication: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#CookieAuthFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CookieAuthFile)
     * */
    public data object CookieAuthFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#CookieAuthFileGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CookieAuthFileGroupReadable)
     * */
    public data object CookieAuthFileGroupReadable: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#CountPrivateBandwidth](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CountPrivateBandwidth)
     * */
    public data object CountPrivateBandwidth: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DataDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DataDirectory)
     * */
    public data object DataDirectory: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.DIRECTORY),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableDirectory {

        @JvmStatic
        public fun asSetting(directory: File): TorSetting = buildContract(directory)
    }

    /**
     * [tor-man#DataDirectoryGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DataDirectoryGroupReadable)
     * */
    public data object DataDirectoryGroupReadable: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DirAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirAuthority)
     * */
    public data object DirAuthority: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#DirAuthorityFallbackRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirAuthorityFallbackRate)
     * */
    public data object DirAuthorityFallbackRate: TorOption(
        default = "0.100000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DisableAllSwap](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableAllSwap)
     * */
    public data object DisableAllSwap: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DisableDebuggerAttachment](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableDebuggerAttachment)
     * */
    public data object DisableDebuggerAttachment: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DisableNetwork](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableNetwork)
     * */
    public data object DisableNetwork: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ExtendByEd25519ID](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtendByEd25519ID)
     * */
    public data object ExtendByEd25519ID: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#ExtORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPort)
     *
     * @see [__ExtORPort]
     * */
    public data object ExtORPort: TorOption(
        // Note: If modifying, update __ExtORPort
        default = "", // TODO: Is it 0? tor-man is F'd
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#ExtORPortCookieAuthFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPortCookieAuthFile)
     * */
    public data object ExtORPortCookieAuthFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#ExtORPortCookieAuthFileGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPortCookieAuthFileGroupReadable)
     * */
    public data object ExtORPortCookieAuthFileGroupReadable: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#FallbackDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FallbackDir)
     * */
    public data object FallbackDir: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#FetchDirInfoEarly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchDirInfoEarly)
     * */
    public data object FetchDirInfoEarly: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#FetchDirInfoExtraEarly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchDirInfoExtraEarly)
     * */
    public data object FetchDirInfoExtraEarly: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#FetchHidServDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchHidServDescriptors)
     * */
    public data object FetchHidServDescriptors: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#FetchServerDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchServerDescriptors)
     * */
    public data object FetchServerDescriptors: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#FetchUselessDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FetchUselessDescriptors)
     * */
    public data object FetchUselessDescriptors: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#HardwareAccel](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HardwareAccel)
     * */
    public data object HardwareAccel: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#HashedControlPassword](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HashedControlPassword)
     * */
    public data object HashedControlPassword: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = false,
    ) {
        // TODO: Issue #1
    }

    // (DEPRECATED) HTTPProxy
    // (DEPRECATED) HTTPProxyAuthenticator

    /**
     * [tor-man#HTTPSProxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HTTPSProxy)
     * */
    public data object HTTPSProxy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#HTTPSProxyAuthenticator](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HTTPSProxyAuthenticator)
     * */
    public data object HTTPSProxyAuthenticator: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#KeepalivePeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeepalivePeriod)
     * */
    public data object KeepalivePeriod: TorOption(
        default = 5.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#KeepBindCapabilities](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeepBindCapabilities)
     * */
    public data object KeepBindCapabilities: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#Log](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Log)
     * */
    public data object Log: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE, Attribute.LOGGING),
        isCmdLineArg = false,
        isUnique = false,
    ) {
        // TODO: Need a builder because of multiple configurations
    }

    /**
     * [tor-man#LogMessageDomains](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LogMessageDomains)
     * */
    public data object LogMessageDomains: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.LOGGING),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#LogTimeGranularity](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LogTimeGranularity)
     * */
    public data object LogTimeGranularity: TorOption(
        default = 1.seconds.inWholeMilliseconds,
        attributes = immutableSetOf(Attribute.LOGGING),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MaxAdvertisedBandwidth](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxAdvertisedBandwidth)
     * */
    public data object MaxAdvertisedBandwidth: TorOption(
        default = (2.0F.pow(30)).toInt(), // 1 GByte
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MaxUnparseableDescSizeToLog](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxUnparseableDescSizeToLog)
     * */
    public data object MaxUnparseableDescSizeToLog: TorOption(
        default = (2.0F.pow(20) * 10).toInt(), // 10 MB
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MetricsPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MetricsPort)
     *
     * @see [__MetricsPort]
     * */
    public data object MetricsPort: TorOption(
        // Note: If modifying, update __MetricsPort
        default = "",
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#MetricsPortPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MetricsPortPolicy)
     * */
    public data object MetricsPortPolicy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#NoExec](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NoExec)
     * */
    public data object NoExec: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    // Can be utilized 2 times max. IPv4 & IPv6
    /**
     * [tor-man#OutboundBindAddress](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddress)
     * */
    public data object OutboundBindAddress: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    // Can be utilized 2 times max. IPv4 & IPv6
    /**
     * [tor-man#OutboundBindAddressExit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddressExit)
     * */
    public data object OutboundBindAddressExit: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    // Can be utilized 2 times max. IPv4 & IPv6
    /**
     * [tor-man#OutboundBindAddressOR](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddressOR)
     * */
    public data object OutboundBindAddressOR: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PerConnBWBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PerConnBWBurst)
     * */
    public data object PerConnBWBurst: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PerConnBWRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PerConnBWRate)
     * */
    public data object PerConnBWRate: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    // Can be utilized 2 times max. IPv4 & IPv6
    /**
     * [tor-man#OutboundBindAddressPT](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OutboundBindAddressPT)
     * */
    public data object OutboundBindAddressPT: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PidFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PidFile)
     * */
    public data object PidFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#ProtocolWarnings](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ProtocolWarnings)
     * */
    public data object ProtocolWarnings: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#RelayBandwidthBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RelayBandwidthBurst)
     * */
    public data object RelayBandwidthBurst: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#RelayBandwidthRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RelayBandwidthRate)
     * */
    public data object RelayBandwidthRate: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#RephistTrackTime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RephistTrackTime)
     * */
    public data object RephistTrackTime: TorOption(
        default = 24.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#RunAsDaemon](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RunAsDaemon)
     * */
    public data object RunAsDaemon: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#SafeLogging](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SafeLogging)
     * */
    public data object SafeLogging: TorOption(
        default = "1",
        attributes = immutableSetOf(Attribute.LOGGING),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#Sandbox](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Sandbox)
     * */
    public data object Sandbox: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#Schedulers](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Schedulers)
     * */
    public data object Schedulers: TorOption(
        default = "KIST,KISTLite,Vanilla",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#KISTSchedRunInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KISTSchedRunInterval)
     * */
    public data object KISTSchedRunInterval: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#KISTSockBufSizeFactor](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KISTSockBufSizeFactor)
     * */
    public data object KISTSockBufSizeFactor: TorOption(
        default = "1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#Socks4Proxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks4Proxy)
     * */
    public data object Socks4Proxy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#Socks5Proxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks5Proxy)
     * */
    public data object Socks5Proxy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#Socks5ProxyUsername](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks5ProxyUsername)
     * */
    public data object Socks5ProxyUsername: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#Socks5ProxyPassword](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Socks5ProxyPassword)
     * */
    public data object Socks5ProxyPassword: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#SyslogIdentityTag](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SyslogIdentityTag)
     * */
    public data object SyslogIdentityTag: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.LOGGING),
        isCmdLineArg = true,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
    }

    /**
     * [tor-man#SyslogIdentityTag](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SyslogIdentityTag)
     * */
    public data object AndroidIdentityTag: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.LOGGING),
        isCmdLineArg = true,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
    }

    /**
     * [tor-man#TCPProxy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TCPProxy)
     * */
    public data object TCPProxy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TruncateLogFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TruncateLogFile)
     * */
    public data object TruncateLogFile: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.LOGGING),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#UnixSocksGroupWritable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UnixSocksGroupWritable)
     * */
    public data object UnixSocksGroupWritable: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#UseDefaultFallbackDirs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseDefaultFallbackDirs)
     * */
    public data object UseDefaultFallbackDirs: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#User](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#User)
     * */
    public data object User: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    )

    //////////////////////
    //  CLIENT OPTIONS  //
    //////////////////////

    /**
     * [tor-man#AllowNonRFC953Hostnames](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AllowNonRFC953Hostnames)
     * */
    public data object AllowNonRFC953Hostnames: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AutomapHostsOnResolve](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AutomapHostsOnResolve)
     * */
    public data object AutomapHostsOnResolve: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AutomapHostsSuffixes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AutomapHostsSuffixes)
     * */
    public data object AutomapHostsSuffixes: TorOption(
        default = ".onion,.exit",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
    }

    /**
     * [tor-man#Bridge](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Bridge)
     * */
    public data object Bridge: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#CircuitPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitPadding)
     * */
    public data object CircuitPadding: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ReducedCircuitPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReducedCircuitPadding)
     * */
    public data object ReducedCircuitPadding: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ClientBootstrapConsensusAuthorityDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusAuthorityDownloadInitialDelay)
     * */
    public data object ClientBootstrapConsensusAuthorityDownloadInitialDelay: TorOption(
        default = 6,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay)
     * */
    public data object ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ClientBootstrapConsensusFallbackDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusFallbackDownloadInitialDelay)
     * */
    public data object ClientBootstrapConsensusFallbackDownloadInitialDelay: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ClientBootstrapConsensusMaxInProgressTries](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientBootstrapConsensusMaxInProgressTries)
     * */
    public data object ClientBootstrapConsensusMaxInProgressTries: TorOption(
        default = 3,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ClientDNSRejectInternalAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientDNSRejectInternalAddresses)
     * */
    public data object ClientDNSRejectInternalAddresses: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ClientOnionAuthDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientOnionAuthDir)
     * */
    public data object ClientOnionAuthDir: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.DIRECTORY),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableDirectory {

        @JvmStatic
        public fun asSetting(directory: File): TorSetting = buildContract(directory)
    }

    /**
     * [tor-man#ClientOnly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientOnly)
     * */
    public data object ClientOnly: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    // (DEPRECATED) ClientPreferIPv6DirPort

    /**
     * [tor-man#ClientPreferIPv6ORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientPreferIPv6ORPort)
     * */
    public data object ClientPreferIPv6ORPort: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#ClientRejectInternalAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientRejectInternalAddresses)
     * */
    public data object ClientRejectInternalAddresses: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ClientUseIPv4](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientUseIPv4)
     * */
    public data object ClientUseIPv4: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ClientUseIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ClientUseIPv6)
     * */
    public data object ClientUseIPv6: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ConnectionPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConnectionPadding)
     * */
    public data object ConnectionPadding: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#ReducedConnectionPadding](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReducedConnectionPadding)
     * */
    public data object ReducedConnectionPadding: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DNSPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DNSPort)
     *
     * @see [__DNSPort]
     * */
    public data object DNSPort: TorOption(
        // Note: If modifying, update __DNSPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.DNS> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.DNS>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.DNS = BuilderScopePort.DNS.of(isNonPersistent = false)
    }

    /**
     * [tor-man#DownloadExtraInfo](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DownloadExtraInfo)
     * */
    public data object DownloadExtraInfo: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#EnforceDistinctSubnets](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#EnforceDistinctSubnets)
     * */
    public data object EnforceDistinctSubnets: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#FascistFirewall](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#FascistFirewall)
     * */
    public data object FascistFirewall: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    // (DEPRECATED) FirewallPorts

    /**
     * [tor-man#HTTPTunnelPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HTTPTunnelPort)
     *
     * @see [__HTTPTunnelPort]
     * */
    public data object HTTPTunnelPort: TorOption(
        // Note: If modifying, update __HTTPTunnelPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.HTTPTunnel> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.HTTPTunnel>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.HTTPTunnel = BuilderScopePort.HTTPTunnel.of(isNonPersistent = false)
    }

    /**
     * [tor-man#LongLivedPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LongLivedPorts)
     * */
    public data object LongLivedPorts: TorOption(
        default = "21,22,706,1863,5050,5190,5222,5223,6523,6667,6697,8300",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MapAddress](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MapAddress)
     * */
    public data object MapAddress: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#MaxCircuitDirtiness](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxCircuitDirtiness)
     * */
    public data object MaxCircuitDirtiness: TorOption(
        default = 10.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MaxClientCircuitsPending](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxClientCircuitsPending)
     * */
    public data object MaxClientCircuitsPending: TorOption(
        default = 32,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#NATDPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NATDPort)
     *
     * @see [__NATDPort]
     * */
    public data object NATDPort: TorOption(
        // Note: If modifying, update __NATDPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#NewCircuitPeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NewCircuitPeriod)
     * */
    public data object NewCircuitPeriod: TorOption(
        default = 30,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasCircThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasCircThreshold)
     * */
    public data object PathBiasCircThreshold: TorOption(
        default = -1,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasDropGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasDropGuards)
     * */
    public data object PathBiasDropGuards: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasExtremeRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasExtremeRate)
     * */
    public data object PathBiasExtremeRate: TorOption(
        default = "-1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasNoticeRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasNoticeRate)
     * */
    public data object PathBiasNoticeRate: TorOption(
        default = "-1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasWarnRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasWarnRate)
     * */
    public data object PathBiasWarnRate: TorOption(
        default = "-1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasScaleThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasScaleThreshold)
     * */
    public data object PathBiasScaleThreshold: TorOption(
        default = -1,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasUseThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasUseThreshold)
     * */
    public data object PathBiasUseThreshold: TorOption(
        default = -1,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasNoticeUseRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasNoticeUseRate)
     * */
    public data object PathBiasNoticeUseRate: TorOption(
        default = "-1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasExtremeUseRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasExtremeUseRate)
     * */
    public data object PathBiasExtremeUseRate: TorOption(
        default = "-1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathBiasScaleUseThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathBiasScaleUseThreshold)
     * */
    public data object PathBiasScaleUseThreshold: TorOption(
        default = -1,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PathsNeededToBuildCircuits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PathsNeededToBuildCircuits)
     * */
    public data object PathsNeededToBuildCircuits: TorOption(
        default = "-1.000000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ReachableAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReachableAddresses)
     * */
    public data object ReachableAddresses: TorOption(
        default = "accept *:*",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    // (DEPRECATED) ReachableDirAddresses

    /**
     * [tor-man#ReachableORAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReachableORAddresses)
     * */
    public data object ReachableORAddresses: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#SafeSocks](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SafeSocks)
     * */
    public data object SafeSocks: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#TestSocks](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestSocks)
     * */
    public data object TestSocks: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#WarnPlaintextPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#WarnPlaintextPorts)
     * */
    public data object WarnPlaintextPorts: TorOption(
        default = "23,109,110,143",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#RejectPlaintextPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RejectPlaintextPorts)
     * */
    public data object RejectPlaintextPorts: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#SocksPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksPolicy)
     * */
    public data object SocksPolicy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#SocksPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksPort)
     *
     * @see [__SocksPort]
     * */
    public data object SocksPort: TorOption(
        // Note: If modifying, update SocksPort
        default = 9050,
        attributes = immutableSetOf(Attribute.PORT, Attribute.UNIX_SOCKET),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.Socks> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.Socks>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.Socks = BuilderScopePort.Socks.of(isNonPersistent = false)
    }

    /**
     * [tor-man#TokenBucketRefillInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TokenBucketRefillInterval)
     * */
    public data object TokenBucketRefillInterval: TorOption(
        default = 100.milliseconds.inWholeMilliseconds,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    )

    /**
     * [tor-man#TrackHostExits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TrackHostExits)
     * */
    public data object TrackHostExits: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TrackHostExitsExpire](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TrackHostExitsExpire)
     * */
    public data object TrackHostExitsExpire: TorOption(
        default = 30.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TransPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TransPort)
     *
     * @see [__TransPort]
     * */
    public data object TransPort: TorOption(
        // Note: If modifying, update __TransPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.Trans> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.Trans>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.Trans = BuilderScopePort.Trans.of(isNonPersistent = false)
    }

    /**
     * [tor-man#TransProxyType](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TransProxyType)
     * */
    public data object TransProxyType: TorOption(
        default = "default",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#UpdateBridgesFromAuthority](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UpdateBridgesFromAuthority)
     * */
    public data object UpdateBridgesFromAuthority: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: May need a "group" builder.
    }

    /**
     * [tor-man#UseBridges](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseBridges)
     * */
    public data object UseBridges: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: May need a "group" builder.
    }

    /**
     * [tor-man#UseEntryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseEntryGuards)
     * */
    public data object UseEntryGuards: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#UseGuardFraction](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseGuardFraction)
     * */
    public data object UseGuardFraction: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#GuardLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GuardLifetime)
     * */
    public data object GuardLifetime: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#NumDirectoryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumDirectoryGuards)
     * */
    public data object NumDirectoryGuards: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#NumEntryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumEntryGuards)
     * */
    public data object NumEntryGuards: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#NumPrimaryGuards](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumPrimaryGuards)
     * */
    public data object NumPrimaryGuards: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#VanguardsLiteEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VanguardsLiteEnabled)
     * */
    public data object VanguardsLiteEnabled: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#UseMicrodescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#UseMicrodescriptors)
     * */
    public data object UseMicrodescriptors: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#VirtualAddrNetworkIPv4](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VirtualAddrNetworkIPv4)
     * */
    public data object VirtualAddrNetworkIPv4: TorOption(
        default = "127.192.0.0/10",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
    }

    /**
     * [tor-man#VirtualAddrNetworkIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VirtualAddrNetworkIPv6)
     * */
    public data object VirtualAddrNetworkIPv6: TorOption(
        default = "[FE80::]/10",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
    }

    ///////////////////////////////
    //  CIRCUIT TIMEOUT OPTIONS  //
    ///////////////////////////////

    /**
     * [tor-man#CircuitsAvailableTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitsAvailableTimeout)
     * */
    public data object CircuitsAvailableTimeout: TorOption(
        default = 30.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#LearnCircuitBuildTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#LearnCircuitBuildTimeout)
     * */
    public data object LearnCircuitBuildTimeout: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#CircuitBuildTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitBuildTimeout)
     * */
    public data object CircuitBuildTimeout: TorOption(
        default = 60.seconds.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#CircuitStreamTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CircuitStreamTimeout)
     * */
    public data object CircuitStreamTimeout: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#SocksTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksTimeout)
     * */
    public data object SocksTimeout: TorOption(
        default = 2.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    //////////////////////////
    // DORMANT MODE OPTIONS //
    //////////////////////////

    /**
     * [tor-man#DormantCanceledByStartup](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantCanceledByStartup)
     * */
    public data object DormantCanceledByStartup: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DormantClientTimeout](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantClientTimeout)
     * */
    public data object DormantClientTimeout: TorOption(
        default = 24.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
    }

    /**
     * [tor-man#DormantOnFirstStartup](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantOnFirstStartup)
     * */
    public data object DormantOnFirstStartup: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DormantTimeoutDisabledByIdleStreams](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantTimeoutDisabledByIdleStreams)
     * */
    public data object DormantTimeoutDisabledByIdleStreams: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DormantTimeoutEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DormantTimeoutEnabled)
     * */
    public data object DormantTimeoutEnabled: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    ////////////////////////////
    // NODE SELECTION OPTIONS //
    ////////////////////////////

    /**
     * [tor-man#EntryNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#EntryNodes)
     * */
    public data object EntryNodes: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#ExcludeNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExcludeNodes)
     * */
    public data object ExcludeNodes: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#ExcludeExitNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExcludeExitNodes)
     * */
    public data object ExcludeExitNodes: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#ExitNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitNodes)
     * */
    public data object ExitNodes: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#GeoIPExcludeUnknown](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GeoIPExcludeUnknown)
     * */
    public data object GeoIPExcludeUnknown: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#HSLayer2Nodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HSLayer2Nodes)
     * */
    public data object HSLayer2Nodes: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HSLayer3Nodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HSLayer3Nodes)
     * */
    public data object HSLayer3Nodes: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#MiddleNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MiddleNodes)
     * */
    public data object MiddleNodes: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#NodeFamily](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NodeFamily)
     * */
    public data object NodeFamily: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#StrictNodes](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#StrictNodes)
     * */
    public data object StrictNodes: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    //////////////////////
    //  SERVER OPTIONS  //
    //////////////////////

    /**
     * [tor-man#AccountingMax](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccountingMax)
     * */
    public data object AccountingMax: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AccountingRule](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccountingRule)
     * */
    public data object AccountingRule: TorOption(
        default = "max",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AccountingStart](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AccountingStart)
     * */
    public data object AccountingStart: TorOption(
        default = "month 1 0:00",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#Address](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Address)
     * */
    public data object Address: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AddressDisableIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AddressDisableIPv6)
     * */
    public data object AddressDisableIPv6: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AssumeReachable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AssumeReachable)
     * */
    public data object AssumeReachable: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AssumeReachableIPv6](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AssumeReachableIPv6)
     * */
    public data object AssumeReachableIPv6: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#BridgeRelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeRelay)
     * */
    public data object BridgeRelay: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#BridgeDistribution](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeDistribution)
     * */
    public data object BridgeDistribution: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ContactInfo](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ContactInfo)
     * */
    public data object ContactInfo: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DisableOOSCheck](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DisableOOSCheck)
     * */
    public data object DisableOOSCheck: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ExitPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPolicy)
     * */
    public data object ExitPolicy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#ExitPolicyRejectLocalInterfaces](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPolicyRejectLocalInterfaces)
     * */
    public data object ExitPolicyRejectLocalInterfaces: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: May need a "group" builder.
    }

    /**
     * [tor-man#ExitPolicyRejectPrivate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPolicyRejectPrivate)
     * */
    public data object ExitPolicyRejectPrivate: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ) {
        // TODO: May need a "group" builder.
    }

    /**
     * [tor-man#ExitRelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitRelay)
     * */
    public data object ExitRelay: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#ExtendAllowPrivateAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtendAllowPrivateAddresses)
     * */
    public data object ExtendAllowPrivateAddresses: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#GeoIPFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GeoIPFile)
     * */
    public data object GeoIPFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#GeoIPv6File](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GeoIPv6File)
     * */
    public data object GeoIPv6File: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#HeartbeatPeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HeartbeatPeriod)
     * */
    public data object HeartbeatPeriod: TorOption(
        default = 6.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#IPv6Exit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#IPv6Exit)
     * */
    public data object IPv6Exit: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#KeyDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeyDirectory)
     * */
    public data object KeyDirectory: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.DIRECTORY),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableDirectory {

        @JvmStatic
        public fun asSetting(directory: File): TorSetting = buildContract(directory)
    }

    /**
     * [tor-man#KeyDirectoryGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#KeyDirectoryGroupReadable)
     * */
    public data object KeyDirectoryGroupReadable: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#MainloopStats](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MainloopStats)
     * */
    public data object MainloopStats: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#MaxMemInQueues](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxMemInQueues)
     * */
    public data object MaxMemInQueues: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MaxOnionQueueDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxOnionQueueDelay)
     * */
    public data object MaxOnionQueueDelay: TorOption(
        default = 1_750.milliseconds.inWholeMilliseconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MyFamily](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MyFamily)
     * */
    public data object MyFamily: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#Nickname](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#Nickname)
     * */
    public data object Nickname: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#NumCPUs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NumCPUs)
     * */
    public data object NumCPUs: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#OfflineMasterKey](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OfflineMasterKey)
     * */
    public data object OfflineMasterKey: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ORPort)
     *
     * @see [__ORPort]
     * */
    public data object ORPort: TorOption(
        // Note: If modifying, update __ORPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#PublishServerDescriptor](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PublishServerDescriptor)
     * */
    public data object PublishServerDescriptor: TorOption(
        default = "1",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ReducedExitPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ReducedExitPolicy)
     * */
    public data object ReducedExitPolicy: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#RefuseUnknownExits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RefuseUnknownExits)
     * */
    public data object RefuseUnknownExits: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#ServerDNSAllowBrokenConfig](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSAllowBrokenConfig)
     * */
    public data object ServerDNSAllowBrokenConfig: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ServerDNSAllowNonRFC953Hostnames](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSAllowNonRFC953Hostnames)
     * */
    public data object ServerDNSAllowNonRFC953Hostnames: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ServerDNSDetectHijacking](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSDetectHijacking)
     * */
    public data object ServerDNSDetectHijacking: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ServerDNSRandomizeCase](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSRandomizeCase)
     * */
    public data object ServerDNSRandomizeCase: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ServerDNSResolvConfFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSResolvConfFile)
     * */
    public data object ServerDNSResolvConfFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#ServerDNSSearchDomains](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSSearchDomains)
     * */
    public data object ServerDNSSearchDomains: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ServerDNSTestAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerDNSTestAddresses)
     * */
    public data object ServerDNSTestAddresses: TorOption(
        default = "www.google.com,www.mit.edu,www.yahoo.com,www.slashdot.org",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ServerTransportListenAddr](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerTransportListenAddr)
     * */
    public data object ServerTransportListenAddr: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ServerTransportOptions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerTransportOptions)
     * */
    public data object ServerTransportOptions: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ServerTransportPlugin](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ServerTransportPlugin)
     * */
    public data object ServerTransportPlugin: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ShutdownWaitLength](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ShutdownWaitLength)
     * */
    public data object ShutdownWaitLength: TorOption(
        default = 30.seconds.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#SigningKeyLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SigningKeyLifetime)
     * */
    public data object SigningKeyLifetime: TorOption(
        default = 30.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#SSLKeyLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SSLKeyLifetime)
     * */
    public data object SSLKeyLifetime: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    ////////////////////////
    // STATISTICS OPTIONS //
    ////////////////////////

    /**
     * [tor-man#BridgeRecordUsageByCountry](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeRecordUsageByCountry)
     * */
    public data object BridgeRecordUsageByCountry: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#CellStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CellStatistics)
     * */
    public data object CellStatistics: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ConnDirectionStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConnDirectionStatistics)
     * */
    public data object ConnDirectionStatistics: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DirReqStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirReqStatistics)
     * */
    public data object DirReqStatistics: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#EntryStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#EntryStatistics)
     * */
    public data object EntryStatistics: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ExitPortStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExitPortStatistics)
     * */
    public data object ExitPortStatistics: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#ExtraInfoStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtraInfoStatistics)
     * */
    public data object ExtraInfoStatistics: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#HiddenServiceStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceStatistics)
     * */
    public data object HiddenServiceStatistics: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#OverloadStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OverloadStatistics)
     * */
    public data object OverloadStatistics: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#PaddingStatistics](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PaddingStatistics)
     * */
    public data object PaddingStatistics: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    ////////////////////////////////
    //  DIRECTORY SERVER OPTIONS  //
    ////////////////////////////////

    /**
     * [tor-man#DirCache](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirCache)
     * */
    public data object DirCache: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#DirPolicy](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPolicy)
     * */
    public data object DirPolicy: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DirPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPort)
     *
     * @see [__DirPort]
     * */
    public data object DirPort: TorOption(
        // Note: If modifying, update __DirPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#DirPortFrontPage](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPortFrontPage)
     * */
    public data object DirPortFrontPage: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#MaxConsensusAgeForDiffs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MaxConsensusAgeForDiffs)
     * */
    public data object MaxConsensusAgeForDiffs: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    ////////////////////////////////////////////
    //  DENIAL OF SERVICE MITIGATION OPTIONS  //
    ////////////////////////////////////////////

    /**
     * [tor-man#DoSCircuitCreationEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationEnabled)
     * */
    public data object DoSCircuitCreationEnabled: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#DoSCircuitCreationBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationBurst)
     * */
    public data object DoSCircuitCreationBurst: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSCircuitCreationDefenseTimePeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationDefenseTimePeriod)
     * */
    public data object DoSCircuitCreationDefenseTimePeriod: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSCircuitCreationDefenseType](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationDefenseType)
     * */
    public data object DoSCircuitCreationDefenseType: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSCircuitCreationMinConnections](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationMinConnections)
     * */
    public data object DoSCircuitCreationMinConnections: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSCircuitCreationRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSCircuitCreationRate)
     * */
    public data object DoSCircuitCreationRate: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSConnectionEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionEnabled)
     * */
    public data object DoSConnectionEnabled: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#DoSConnectionDefenseType](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionDefenseType)
     * */
    public data object DoSConnectionDefenseType: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSConnectionMaxConcurrentCount](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionMaxConcurrentCount)
     * */
    public data object DoSConnectionMaxConcurrentCount: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSConnectionConnectRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionConnectRate)
     * */
    public data object DoSConnectionConnectRate: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSConnectionConnectBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionConnectBurst)
     * */
    public data object DoSConnectionConnectBurst: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSConnectionConnectDefenseTimePeriod](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSConnectionConnectDefenseTimePeriod)
     * */
    public data object DoSConnectionConnectDefenseTimePeriod: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#DoSRefuseSingleHopClientRendezvous](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DoSRefuseSingleHopClientRendezvous)
     * */
    public data object DoSRefuseSingleHopClientRendezvous: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    /**
     * [tor-man#HiddenServiceEnableIntroDoSDefense](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceEnableIntroDoSDefense)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceEnableIntroDoSDefense: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceEnableIntroDoSBurstPerSec](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceEnableIntroDoSBurstPerSec)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceEnableIntroDoSBurstPerSec: TorOption(
        default = 200,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceEnableIntroDoSRatePerSec](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceEnableIntroDoSRatePerSec)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceEnableIntroDoSRatePerSec: TorOption(
        default = 25,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServicePoWDefensesEnabled](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePoWDefensesEnabled)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServicePoWDefensesEnabled: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServicePoWQueueRate](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePoWQueueRate)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServicePoWQueueRate: TorOption(
        default = 250,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServicePoWQueueBurst](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePoWQueueBurst)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServicePoWQueueBurst: TorOption(
        default = 2500,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#CompiledProofOfWorkHash](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#CompiledProofOfWorkHash)
     * */
    public data object CompiledProofOfWorkHash: TorOption(
        default = AUTO,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeAutoBoolean> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeAutoBoolean>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeAutoBoolean = toBuilderScopeAutoBoolean()
    }

    //////////////////////////////////////////
    //  DIRECTORY AUTHORITY SERVER OPTIONS  //
    //////////////////////////////////////////

    /**
     * [tor-man#AuthoritativeDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthoritativeDirectory)
     * */
    public data object AuthoritativeDirectory: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#BridgeAuthoritativeDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgeAuthoritativeDir)
     * */
    public data object BridgeAuthoritativeDir: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#V3AuthoritativeDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthoritativeDirectory)
     * */
    public data object V3AuthoritativeDirectory: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirBadExit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirBadExit)
     * */
    public data object AuthDirBadExit: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirMiddleOnly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirMiddleOnly)
     * */
    public data object AuthDirMiddleOnly: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )


    /**
     * [tor-man#AuthDirFastGuarantee](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirFastGuarantee)
     * */
    public data object AuthDirFastGuarantee: TorOption(
        default = ((2.0F.pow(10)) * 100).toInt(), // 100 KBytes
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirGuardBWGuarantee](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirGuardBWGuarantee)
     * */
    public data object AuthDirGuardBWGuarantee: TorOption(
        default = (2.0F.pow(20) * 2).toInt(), // 2MBytes
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirHasIPv6Connectivity](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirHasIPv6Connectivity)
     * */
    public data object AuthDirHasIPv6Connectivity: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirInvalid](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirInvalid)
     * */
    public data object AuthDirInvalid: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirListBadExits](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirListBadExits)
     * */
    public data object AuthDirListBadExits: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirListMiddleOnly](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirListMiddleOnly)
     * */
    public data object AuthDirListMiddleOnly: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirMaxServersPerAddr](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirMaxServersPerAddr)
     * */
    public data object AuthDirMaxServersPerAddr: TorOption(
        default = 2,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirPinKeys](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirPinKeys)
     * */
    public data object AuthDirPinKeys: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirReject](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirReject)
     * */
    public data object AuthDirReject: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirRejectRequestsUnderLoad](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirRejectRequestsUnderLoad)
     * */
    public data object AuthDirRejectRequestsUnderLoad: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirBadExitCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirBadExitCCs)
     * */
    public data object AuthDirBadExitCCs: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirInvalidCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirInvalidCCs)
     * */
    public data object AuthDirInvalidCCs: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirMiddleOnlyCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirMiddleOnlyCCs)
     * */
    public data object AuthDirMiddleOnlyCCs: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )


    /**
     * [tor-man#AuthDirRejectCCs](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirRejectCCs)
     * */
    public data object AuthDirRejectCCs: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirSharedRandomness](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirSharedRandomness)
     * */
    public data object AuthDirSharedRandomness: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirTestEd25519LinkKeys](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirTestEd25519LinkKeys)
     * */
    public data object AuthDirTestEd25519LinkKeys: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirTestReachability](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirTestReachability)
     * */
    public data object AuthDirTestReachability: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#AuthDirVoteGuard](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuard)
     * */
    public data object AuthDirVoteGuard: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirVoteGuardBwThresholdFraction](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuardBwThresholdFraction)
     * */
    public data object AuthDirVoteGuardBwThresholdFraction: TorOption(
        default = "0.750000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirVoteGuardGuaranteeTimeKnown](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuardGuaranteeTimeKnown)
     * */
    public data object AuthDirVoteGuardGuaranteeTimeKnown: TorOption(
        default = 8.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirVoteGuardGuaranteeWFU](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteGuardGuaranteeWFU)
     * */
    public data object AuthDirVoteGuardGuaranteeWFU: TorOption(
        default = "0.980000",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirVoteStableGuaranteeMinUptime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteStableGuaranteeMinUptime)
     * */
    public data object AuthDirVoteStableGuaranteeMinUptime: TorOption(
        default = 30.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#AuthDirVoteStableGuaranteeMTBF](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#AuthDirVoteStableGuaranteeMTBF)
     * */
    public data object AuthDirVoteStableGuaranteeMTBF: TorOption(
        default = 5.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#BridgePassword](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#BridgePassword)
     * */
    public data object BridgePassword: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#ConsensusParams](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ConsensusParams)
     * */
    public data object ConsensusParams: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#DirAllowPrivateAddresses](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirAllowPrivateAddresses)
     * */
    public data object DirAllowPrivateAddresses: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#GuardfractionFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#GuardfractionFile)
     * */
    public data object GuardfractionFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#MinMeasuredBWsForAuthToIgnoreAdvertised](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MinMeasuredBWsForAuthToIgnoreAdvertised)
     * */
    public data object MinMeasuredBWsForAuthToIgnoreAdvertised: TorOption(
        default = 500,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#MinUptimeHidServDirectoryV2](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MinUptimeHidServDirectoryV2)
     * */
    public data object MinUptimeHidServDirectoryV2: TorOption(
        default = 96.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#RecommendedClientVersions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RecommendedClientVersions)
     * */
    public data object RecommendedClientVersions: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#RecommendedServerVersions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RecommendedServerVersions)
     * */
    public data object RecommendedServerVersions: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#RecommendedVersions](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#RecommendedVersions)
     * */
    public data object RecommendedVersions: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#V3AuthDistDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthDistDelay)
     * */
    public data object V3AuthDistDelay: TorOption(
        default = 5.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#V3AuthNIntervalsValid](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthNIntervalsValid)
     * */
    public data object V3AuthNIntervalsValid: TorOption(
        default = 3,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#V3AuthUseLegacyKey](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthUseLegacyKey)
     * */
    public data object V3AuthUseLegacyKey: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [tor-man#V3AuthVoteDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthVoteDelay)
     * */
    public data object V3AuthVoteDelay: TorOption(
        default = 5.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#V3AuthVotingInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3AuthVotingInterval)
     * */
    public data object V3AuthVotingInterval: TorOption(
        default = 1.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#V3BandwidthsFile](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#V3BandwidthsFile)
     * */
    public data object V3BandwidthsFile: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.FILE),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableFile {

        @JvmStatic
        public fun asSetting(file: File): TorSetting = buildContract(file)
    }

    /**
     * [tor-man#VersioningAuthoritativeDirectory](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#VersioningAuthoritativeDirectory)
     * */
    public data object VersioningAuthoritativeDirectory: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    //////////////////////////////
    //  HIDDEN SERVICE OPTIONS  //
    //////////////////////////////

    /**
     * [tor-man#HiddenServiceAllowUnknownPorts](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceAllowUnknownPorts)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceAllowUnknownPorts: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceDir)
     *
     * @see [TorConfig2.BuilderScope.configure]
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceDir: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.DIRECTORY, Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    ) {

        /**
         * See [BuilderScopeHS]
         *
         * @throws [IllegalArgumentException] if misconfigured. See [BuilderScopeHS]
         * */
        @JvmStatic
        public fun asSetting(
            directory: File,
            block: ThisBlock<BuilderScopeHS>,
        ): TorSetting = BuilderScopeHS.build(directory, block)
    }

    /**
     * [tor-man#HiddenServiceDirGroupReadable](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceDirGroupReadable)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceDirGroupReadable: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceExportCircuitID](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceExportCircuitID)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceExportCircuitID: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceOnionBalanceInstance](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceOnionBalanceInstance)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceOnionBalanceInstance: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceMaxStreams](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceMaxStreams)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceMaxStreams: TorOption(
        default = 0,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceMaxStreamsCloseCircuit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceMaxStreamsCloseCircuit)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceMaxStreamsCloseCircuit: TorOption(
        default = false.byte,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceNumIntroductionPoints](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceNumIntroductionPoints)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceNumIntroductionPoints: TorOption(
        default = 3,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServicePort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServicePort)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServicePort: TorOption(
        default = "",
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE, Attribute.PORT, Attribute.UNIX_SOCKET),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceVersion](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceVersion)
     *
     * @see [HiddenServiceDir.asSetting]
     * */
    public data object HiddenServiceVersion: TorOption(
        default = 3,
        attributes = immutableSetOf(Attribute.HIDDEN_SERVICE),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#HiddenServiceSingleHopMode](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceSingleHopMode)
     * */
    public data object HiddenServiceSingleHopMode: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
        // TODO: Hidden Service NonAnonymousMode builder.
        //  Requires HiddenServiceNonAnonymousMode to be enabled
    }

    /**
     * [tor-man#HiddenServiceNonAnonymousMode](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HiddenServiceNonAnonymousMode)
     * */
    public data object HiddenServiceNonAnonymousMode: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ) {
        // TODO: IMPLEMENT
        // TODO: Hidden Service NonAnonymousMode builder.
    }

    /**
     * [tor-man#PublishHidServDescriptors](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#PublishHidServDescriptors)
     * */
    public data object PublishHidServDescriptors: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    ///////////////////////////////
    //  TESTING NETWORK OPTIONS  //
    ///////////////////////////////

    // TODO: TestingNetwork builder.
    //  Most options require TestingTorNetwork to be enabled

    /**
     * [tor-man#TestingTorNetwork](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingTorNetwork)
     * */
    public data object TestingTorNetwork: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    )

    /**
     * [tor-man#TestingAuthDirTimeToLearnReachability](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingAuthDirTimeToLearnReachability)
     * */
    public data object TestingAuthDirTimeToLearnReachability: TorOption(
        default = 30.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingAuthKeyLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingAuthKeyLifetime)
     * */
    public data object TestingAuthKeyLifetime: TorOption(
        default = 2.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingAuthKeySlop](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingAuthKeySlop)
     * */
    public data object TestingAuthKeySlop: TorOption(
        default = 3.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingBridgeBootstrapDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingBridgeBootstrapDownloadInitialDelay)
     * */
    public data object TestingBridgeBootstrapDownloadInitialDelay: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingBridgeDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingBridgeDownloadInitialDelay)
     * */
    public data object TestingBridgeDownloadInitialDelay: TorOption(
        default = 3.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingClientConsensusDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingClientConsensusDownloadInitialDelay)
     * */
    public data object TestingClientConsensusDownloadInitialDelay: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingClientDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingClientDownloadInitialDelay)
     * */
    public data object TestingClientDownloadInitialDelay: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingClientMaxIntervalWithoutRequest](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingClientMaxIntervalWithoutRequest)
     * */
    public data object TestingClientMaxIntervalWithoutRequest: TorOption(
        default = 10.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingDirAuthVoteExit](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteExit)
     * */
    public data object TestingDirAuthVoteExit: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingDirAuthVoteExitIsStrict](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteExitIsStrict)
     * */
    public data object TestingDirAuthVoteExitIsStrict: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingDirAuthVoteGuard](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteGuard)
     * */
    public data object TestingDirAuthVoteGuard: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingDirAuthVoteGuardIsStrict](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteGuardIsStrict)
     * */
    public data object TestingDirAuthVoteGuardIsStrict: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingDirAuthVoteHSDir](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteHSDir)
     * */
    public data object TestingDirAuthVoteHSDir: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingDirAuthVoteHSDirIsStrict](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirAuthVoteHSDirIsStrict)
     * */
    public data object TestingDirAuthVoteHSDirIsStrict: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingDirConnectionMaxStall](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingDirConnectionMaxStall)
     * */
    public data object TestingDirConnectionMaxStall: TorOption(
        default = 5.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingEnableCellStatsEvent](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingEnableCellStatsEvent)
     * */
    public data object TestingEnableCellStatsEvent: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingEnableConnBwEvent](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingEnableConnBwEvent)
     * */
    public data object TestingEnableConnBwEvent: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingLinkCertLifetime](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingLinkCertLifetime)
     * */
    public data object TestingLinkCertLifetime: TorOption(
        default = 2.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingLinkKeySlop](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingLinkKeySlop)
     * */
    public data object TestingLinkKeySlop: TorOption(
        default = 3.hours.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingMinExitFlagThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingMinExitFlagThreshold)
     * */
    public data object TestingMinExitFlagThreshold: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingMinFastFlagThreshold](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingMinFastFlagThreshold)
     * */
    public data object TestingMinFastFlagThreshold: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingMinTimeToReportBandwidth](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingMinTimeToReportBandwidth)
     * */
    public data object TestingMinTimeToReportBandwidth: TorOption(
        default = 1.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingServerConsensusDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingServerConsensusDownloadInitialDelay)
     * */
    public data object TestingServerConsensusDownloadInitialDelay: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingServerDownloadInitialDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingServerDownloadInitialDelay)
     * */
    public data object TestingServerDownloadInitialDelay: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingSigningKeySlop](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingSigningKeySlop)
     * */
    public data object TestingSigningKeySlop: TorOption(
        default = 1.days.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingV3AuthInitialDistDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthInitialDistDelay)
     * */
    public data object TestingV3AuthInitialDistDelay: TorOption(
        default = 5.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingV3AuthInitialVoteDelay](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthInitialVoteDelay)
     * */
    public data object TestingV3AuthInitialVoteDelay: TorOption(
        default = 5.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingV3AuthInitialVotingInterval](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthInitialVotingInterval)
     * */
    public data object TestingV3AuthInitialVotingInterval: TorOption(
        default = 30.minutes.inWholeSeconds,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    /**
     * [tor-man#TestingV3AuthVotingStartOffset](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TestingV3AuthVotingStartOffset)
     * */
    public data object TestingV3AuthVotingStartOffset: TorOption(
        default = 0,
        attributes = emptySet(),
        isCmdLineArg = false,
        isUnique = true,
    )

    //////////////////////////////
    //  NON-PERSISTENT OPTIONS  //
    //////////////////////////////

    /**
     * [tor-man#ControlPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [ControlPort]
     * */
    public data object __ControlPort: TorOption(
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT, Attribute.UNIX_SOCKET),
        isCmdLineArg = true,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.Control> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.Control>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.Control = BuilderScopePort.Control.of(isNonPersistent = true)
    }

    /**
     * [tor-man#DirPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DirPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [DirPort]
     * */
    public data object __DirPort: TorOption(
        // Note: If modifying, update DirPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#DNSPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#DNSPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [DNSPort]
     * */
    public data object __DNSPort: TorOption(
        // Note: If modifying, update DNSPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.DNS> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.DNS>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.DNS = BuilderScopePort.DNS.of(isNonPersistent = true)
    }

    /**
     * [tor-man#HTTPTunnelPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#HTTPTunnelPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [HTTPTunnelPort]
     * */
    public data object __HTTPTunnelPort: TorOption(
        // Note: If modifying, update HTTPTunnelPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.HTTPTunnel> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.HTTPTunnel>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.HTTPTunnel = BuilderScopePort.HTTPTunnel.of(isNonPersistent = true)
    }

    /**
     * [tor-man#ExtORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ExtORPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [ExtORPort]
     * */
    public data object __ExtORPort: TorOption(
        // Note: If modifying, update ExtORPort
        default = "", // TODO: Is it 0? tor-man is F'd
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#MetricsPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#MetricsPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [MetricsPort]
     * */
    public data object __MetricsPort: TorOption(
        // Note: If modifying, update MetricsPort
        default = "",
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#NATDPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#NATDPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [NATDPort]
     * */
    public data object __NATDPort: TorOption(
        // Note: If modifying, update NATDPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#ORPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ORPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [ORPort]
     * */
    public data object __ORPort: TorOption(
        // Note: If modifying, update ORPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    )

    /**
     * [tor-man#SocksPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#SocksPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [SocksPort]
     * */
    public data object __SocksPort: TorOption(
        // Note: If modifying, update SocksPort
        default = 9050,
        attributes = immutableSetOf(Attribute.PORT, Attribute.UNIX_SOCKET),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.Socks> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.Socks>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.Socks = BuilderScopePort.Socks.of(isNonPersistent = true)
    }

    /**
     * [tor-man#TransPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#TransPort)
     *
     * [tor-man#Non-Persistent Options](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#non-persistent-options)
     *
     * @see [TransPort]
     * */
    public data object __TransPort: TorOption(
        // Note: If modifying, update TransPort
        default = Port.ZERO,
        attributes = immutableSetOf(Attribute.PORT),
        isCmdLineArg = false,
        isUnique = false,
    ), ConfigurableBuildable<BuilderScopePort.Trans> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopePort.Trans>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopePort.Trans = BuilderScopePort.Trans.of(isNonPersistent = true)
    }

    /**
     * [control-spec#__AllDirActionsPrivate](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __AllDirActionsPrivate: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [control-spec#__DisablePredictedCircuits](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __DisablePredictedCircuits: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [control-spec#__LeaveStreamsUnattached](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __LeaveStreamsUnattached: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [control-spec#__HashedControlSessionPassword](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __HashedControlSessionPassword: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = false,
    ) {
        // TODO: Issue #1
    }

    /**
     * [control-spec#__ReloadTorrcOnSIGHUP](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __ReloadTorrcOnSIGHUP: TorOption(
        default = true.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [control-spec#__OwningControllerProcess](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __OwningControllerProcess: TorOption(
        default = "",
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBuildable<BuilderScopeOwningControllerProcess> {

        @JvmStatic
        public fun asSetting(
            block: ThisBlock<BuilderScopeOwningControllerProcess>,
        ): TorSetting = buildContract(block)

        override fun buildable(): BuilderScopeOwningControllerProcess = BuilderScopeOwningControllerProcess.get()
    }

    /**
     * [control-spec#__OwningControllerFD](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __OwningControllerFD: TorOption(
        default = -1,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    )

    /**
     * [control-spec#__DisableSignalHandlers](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __DisableSignalHandlers: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [control-spec#__AlwaysCongestionControl](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __AlwaysCongestionControl: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * [control-spec#__SbwsExit](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options)
     * */
    public data object __SbwsExit: TorOption(
        default = false.byte,
        attributes = emptySet(),
        isCmdLineArg = true,
        isUnique = true,
    ), ConfigurableBoolean {

        @JvmStatic
        public fun asSetting(enable: Boolean): TorSetting = buildContract(enable)
    }

    /**
     * Attributes specified via [TorOption.attributes] for the
     * given option.
     *
     * @see [TorSetting.filterByAttribute]
     * */
    public abstract class Attribute private constructor() {

        /**
         * Indicates the [TorOption] argument is that
         * of a path for a directory.
         *
         * @see [TorSetting.LineItem.isDirectory]
         * */
        public data object DIRECTORY: Attribute()

        /**
         * Indicates the [TorOption] argument is that
         * of a path for a file.
         *
         * @see [TorSetting.LineItem.isFile]
         * */
        public data object FILE: Attribute()

        /**
         * Indicates that the [TorOption] is a part of the
         * Hidden Service "group" (expressed per Hidden Service).
         *
         * @see [TorSetting.LineItem.isHiddenService]
         * @see [HiddenServiceDir.asSetting]
         * */
        public data object HIDDEN_SERVICE: Attribute()

        /**
         * Indicates the [TorOption] is for logging
         * purposes.
         * */
        public data object LOGGING: Attribute()

        /**
         * Indicates the [TorOption] argument can be
         * that of a TCP Port, `auto`, or `0`.
         *
         * @see [TorSetting.LineItem.isPort]
         * @see [TorSetting.LineItem.isPortAndAuto]
         * @see [TorSetting.LineItem.isPortAndDisabled]
         * @see [TorSetting.LineItem.isPortAndDistinct]
         * */
        public data object PORT: Attribute()

        /**
         * Indicates the [TorOption] argument can be
         * that of a Unix Socket [File] path (will be
         * prefixed with `unix:` if that is the case).
         *
         * @see [TorSetting.LineItem.isUnixSocket]
         * */
        public data object UNIX_SOCKET: Attribute()
    }

    public final override val length: Int get() = name.length
    public final override operator fun get(index: Int): Char = name[index]
    public final override fun subSequence(
        startIndex: Int,
        endIndex: Int,
    ): CharSequence = name.subSequence(startIndex, endIndex)

    public final override operator fun compareTo(other: TorOption): Int = name.compareTo(other.name)

    public operator fun plus(other: Any?): String = name.plus(other)

    public companion object {

        /**
         * Retrieves the [TorOption] for provided [name].
         *
         * @throws [IllegalArgumentException] if [name] is unknown.
         * */
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        public fun valueOf(name: String): TorOption = valueOfOrNull(name)
            ?: throw IllegalArgumentException("Unknown name[$name]")

        /**
         * Retrieves the [TorOption] for the provided [name], or `null`
         * if [name] is unknown.
         * */
        @JvmStatic
        public fun valueOfOrNull(name: String): TorOption? {
            return entries.firstOrNull { it.name == name }
        }

        /**
         * All available [TorOption] entries, ordered as they appear in the
         * [tor-manual](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc)
         * and [control-spec](https://spec.torproject.org/control-spec/implementation-notes.html?highlight=__#special-config-options).
         * */
        @get:JvmStatic
        @get:JvmName("entries")
        public val entries: Set<TorOption> by lazy {
            immutableSetOf(
                AccelDir,
                AccelName,
                AlternateBridgeAuthority,
                AlternateDirAuthority,
                AvoidDiskWrites,
                BandwidthBurst,
                BandwidthRate,
                CacheDirectory,
                CacheDirectoryGroupReadable,
                CircuitPriorityHalflife,
                ClientTransportPlugin,
                ConfluxEnabled,
                ConfluxClientUX,
                ConnLimit,
                ConstrainedSockets,
                ConstrainedSockSize,
                ControlPort,
                ControlPortFileGroupReadable,
                ControlPortWriteToFile,
                ControlSocket,
                ControlSocketsGroupWritable,
                CookieAuthentication,
                CookieAuthFile,
                CookieAuthFileGroupReadable,
                CountPrivateBandwidth,
                DataDirectory,
                DataDirectoryGroupReadable,
                DirAuthority,
                DirAuthorityFallbackRate,
                DisableAllSwap,
                DisableDebuggerAttachment,
                DisableNetwork,
                ExtendByEd25519ID,
                ExtORPort,
                ExtORPortCookieAuthFile,
                ExtORPortCookieAuthFileGroupReadable,
                FallbackDir,
                FetchDirInfoEarly,
                FetchDirInfoExtraEarly,
                FetchHidServDescriptors,
                FetchServerDescriptors,
                FetchUselessDescriptors,
                HardwareAccel,
                HashedControlPassword,
                HTTPSProxy,
                HTTPSProxyAuthenticator,
                KeepalivePeriod,
                KeepBindCapabilities,
                Log,
                LogMessageDomains,
                LogTimeGranularity,
                MaxAdvertisedBandwidth,
                MaxUnparseableDescSizeToLog,
                MetricsPort,
                MetricsPortPolicy,
                NoExec,
                OutboundBindAddress,
                OutboundBindAddressExit,
                OutboundBindAddressOR,
                PerConnBWBurst,
                PerConnBWRate,
                OutboundBindAddressPT,
                PidFile,
                ProtocolWarnings,
                RelayBandwidthBurst,
                RelayBandwidthRate,
                RephistTrackTime,
                RunAsDaemon,
                SafeLogging,
                Sandbox,
                Schedulers,
                KISTSchedRunInterval,
                KISTSockBufSizeFactor,
                Socks4Proxy,
                Socks5Proxy,
                Socks5ProxyUsername,
                Socks5ProxyPassword,
                SyslogIdentityTag,
                AndroidIdentityTag,
                TCPProxy,
                TruncateLogFile,
                UnixSocksGroupWritable,
                UseDefaultFallbackDirs,
                User,
                AllowNonRFC953Hostnames,
                AutomapHostsOnResolve,
                AutomapHostsSuffixes,
                Bridge,
                CircuitPadding,
                ReducedCircuitPadding,
                ClientBootstrapConsensusAuthorityDownloadInitialDelay,
                ClientBootstrapConsensusAuthorityOnlyDownloadInitialDelay,
                ClientBootstrapConsensusFallbackDownloadInitialDelay,
                ClientBootstrapConsensusMaxInProgressTries,
                ClientDNSRejectInternalAddresses,
                ClientOnionAuthDir,
                ClientOnly,
                ClientPreferIPv6ORPort,
                ClientRejectInternalAddresses,
                ClientUseIPv4,
                ClientUseIPv6,
                ConnectionPadding,
                ReducedConnectionPadding,
                DNSPort,
                DownloadExtraInfo,
                EnforceDistinctSubnets,
                FascistFirewall,
                HTTPTunnelPort,
                LongLivedPorts,
                MapAddress,
                MaxCircuitDirtiness,
                MaxClientCircuitsPending,
                NATDPort,
                NewCircuitPeriod,
                PathBiasCircThreshold,
                PathBiasDropGuards,
                PathBiasExtremeRate,
                PathBiasNoticeRate,
                PathBiasWarnRate,
                PathBiasScaleThreshold,
                PathBiasUseThreshold,
                PathBiasNoticeUseRate,
                PathBiasExtremeUseRate,
                PathBiasScaleUseThreshold,
                PathsNeededToBuildCircuits,
                ReachableAddresses,
                ReachableORAddresses,
                SafeSocks,
                TestSocks,
                WarnPlaintextPorts,
                RejectPlaintextPorts,
                SocksPolicy,
                SocksPort,
                TokenBucketRefillInterval,
                TrackHostExits,
                TrackHostExitsExpire,
                TransPort,
                TransProxyType,
                UpdateBridgesFromAuthority,
                UseBridges,
                UseEntryGuards,
                UseGuardFraction,
                GuardLifetime,
                NumDirectoryGuards,
                NumEntryGuards,
                NumPrimaryGuards,
                VanguardsLiteEnabled,
                UseMicrodescriptors,
                VirtualAddrNetworkIPv4,
                VirtualAddrNetworkIPv6,
                CircuitsAvailableTimeout,
                LearnCircuitBuildTimeout,
                CircuitBuildTimeout,
                CircuitStreamTimeout,
                SocksTimeout,
                DormantCanceledByStartup,
                DormantClientTimeout,
                DormantOnFirstStartup,
                DormantTimeoutDisabledByIdleStreams,
                DormantTimeoutEnabled,
                EntryNodes,
                ExcludeNodes,
                ExcludeExitNodes,
                ExitNodes,
                GeoIPExcludeUnknown,
                HSLayer2Nodes,
                HSLayer3Nodes,
                MiddleNodes,
                NodeFamily,
                StrictNodes,
                AccountingMax,
                AccountingRule,
                AccountingStart,
                Address,
                AddressDisableIPv6,
                AssumeReachable,
                AssumeReachableIPv6,
                BridgeRelay,
                BridgeDistribution,
                ContactInfo,
                DisableOOSCheck,
                ExitPolicy,
                ExitPolicyRejectLocalInterfaces,
                ExitPolicyRejectPrivate,
                ExitRelay,
                ExtendAllowPrivateAddresses,
                GeoIPFile,
                GeoIPv6File,
                HeartbeatPeriod,
                IPv6Exit,
                KeyDirectory,
                KeyDirectoryGroupReadable,
                MainloopStats,
                MaxMemInQueues,
                MaxOnionQueueDelay,
                MyFamily,
                Nickname,
                NumCPUs,
                OfflineMasterKey,
                ORPort,
                PublishServerDescriptor,
                ReducedExitPolicy,
                RefuseUnknownExits,
                ServerDNSAllowBrokenConfig,
                ServerDNSAllowNonRFC953Hostnames,
                ServerDNSDetectHijacking,
                ServerDNSRandomizeCase,
                ServerDNSResolvConfFile,
                ServerDNSSearchDomains,
                ServerDNSTestAddresses,
                ServerTransportListenAddr,
                ServerTransportOptions,
                ServerTransportPlugin,
                ShutdownWaitLength,
                SigningKeyLifetime,
                SSLKeyLifetime,
                BridgeRecordUsageByCountry,
                CellStatistics,
                ConnDirectionStatistics,
                DirReqStatistics,
                EntryStatistics,
                ExitPortStatistics,
                ExtraInfoStatistics,
                HiddenServiceStatistics,
                OverloadStatistics,
                PaddingStatistics,
                DirCache,
                DirPolicy,
                DirPort,
                DirPortFrontPage,
                MaxConsensusAgeForDiffs,
                DoSCircuitCreationEnabled,
                DoSCircuitCreationBurst,
                DoSCircuitCreationDefenseTimePeriod,
                DoSCircuitCreationDefenseType,
                DoSCircuitCreationMinConnections,
                DoSCircuitCreationRate,
                DoSConnectionEnabled,
                DoSConnectionDefenseType,
                DoSConnectionMaxConcurrentCount,
                DoSConnectionConnectRate,
                DoSConnectionConnectBurst,
                DoSConnectionConnectDefenseTimePeriod,
                DoSRefuseSingleHopClientRendezvous,
                HiddenServiceEnableIntroDoSDefense,
                HiddenServiceEnableIntroDoSBurstPerSec,
                HiddenServiceEnableIntroDoSRatePerSec,
                HiddenServicePoWDefensesEnabled,
                HiddenServicePoWQueueRate,
                HiddenServicePoWQueueBurst,
                CompiledProofOfWorkHash,
                AuthoritativeDirectory,
                BridgeAuthoritativeDir,
                V3AuthoritativeDirectory,
                AuthDirBadExit,
                AuthDirMiddleOnly,
                AuthDirFastGuarantee,
                AuthDirGuardBWGuarantee,
                AuthDirHasIPv6Connectivity,
                AuthDirInvalid,
                AuthDirListBadExits,
                AuthDirListMiddleOnly,
                AuthDirMaxServersPerAddr,
                AuthDirPinKeys,
                AuthDirReject,
                AuthDirRejectRequestsUnderLoad,
                AuthDirBadExitCCs,
                AuthDirInvalidCCs,
                AuthDirMiddleOnlyCCs,
                AuthDirRejectCCs,
                AuthDirSharedRandomness,
                AuthDirTestEd25519LinkKeys,
                AuthDirTestReachability,
                AuthDirVoteGuard,
                AuthDirVoteGuardBwThresholdFraction,
                AuthDirVoteGuardGuaranteeTimeKnown,
                AuthDirVoteGuardGuaranteeWFU,
                AuthDirVoteStableGuaranteeMinUptime,
                AuthDirVoteStableGuaranteeMTBF,
                BridgePassword,
                ConsensusParams,
                DirAllowPrivateAddresses,
                GuardfractionFile,
                MinMeasuredBWsForAuthToIgnoreAdvertised,
                MinUptimeHidServDirectoryV2,
                RecommendedClientVersions,
                RecommendedServerVersions,
                RecommendedVersions,
                V3AuthDistDelay,
                V3AuthNIntervalsValid,
                V3AuthUseLegacyKey,
                V3AuthVoteDelay,
                V3AuthVotingInterval,
                V3BandwidthsFile,
                VersioningAuthoritativeDirectory,
                HiddenServiceAllowUnknownPorts,
                HiddenServiceDir,
                HiddenServiceDirGroupReadable,
                HiddenServiceExportCircuitID,
                HiddenServiceOnionBalanceInstance,
                HiddenServiceMaxStreams,
                HiddenServiceMaxStreamsCloseCircuit,
                HiddenServiceNumIntroductionPoints,
                HiddenServicePort,
                HiddenServiceVersion,
                HiddenServiceSingleHopMode,
                HiddenServiceNonAnonymousMode,
                PublishHidServDescriptors,
                TestingTorNetwork,
                TestingAuthDirTimeToLearnReachability,
                TestingAuthKeyLifetime,
                TestingAuthKeySlop,
                TestingBridgeBootstrapDownloadInitialDelay,
                TestingBridgeDownloadInitialDelay,
                TestingClientConsensusDownloadInitialDelay,
                TestingClientDownloadInitialDelay,
                TestingClientMaxIntervalWithoutRequest,
                TestingDirAuthVoteExit,
                TestingDirAuthVoteExitIsStrict,
                TestingDirAuthVoteGuard,
                TestingDirAuthVoteGuardIsStrict,
                TestingDirAuthVoteHSDir,
                TestingDirAuthVoteHSDirIsStrict,
                TestingDirConnectionMaxStall,
                TestingEnableCellStatsEvent,
                TestingEnableConnBwEvent,
                TestingLinkCertLifetime,
                TestingLinkKeySlop,
                TestingMinExitFlagThreshold,
                TestingMinFastFlagThreshold,
                TestingMinTimeToReportBandwidth,
                TestingServerConsensusDownloadInitialDelay,
                TestingServerDownloadInitialDelay,
                TestingSigningKeySlop,
                TestingV3AuthInitialDistDelay,
                TestingV3AuthInitialVoteDelay,
                TestingV3AuthInitialVotingInterval,
                TestingV3AuthVotingStartOffset,
                __ControlPort,
                __DirPort,
                __DNSPort,
                __HTTPTunnelPort,
                __ExtORPort,
                __MetricsPort,
                __NATDPort,
                __ORPort,
                __SocksPort,
                __TransPort,
                __AllDirActionsPrivate,
                __DisablePredictedCircuits,
                __LeaveStreamsUnattached,
                __HashedControlSessionPassword,
                __ReloadTorrcOnSIGHUP,
                __OwningControllerProcess,
                __OwningControllerFD,
                __DisableSignalHandlers,
                __AlwaysCongestionControl,
                __SbwsExit,
            )
        }

        internal const val AUTO: String = "auto"

        @JvmSynthetic
        internal fun TorOption.buildableInternal(): TorSetting.BuilderScope? = buildable()
    }

    /**
     * Factory function for a [TorOption] that, when implemented along
     * with implementation of the [ConfigurableBuildable] interface, makes the
     * [TorOption] available for use with [TorConfig2.BuilderScope.configure].
     * */
    protected open fun buildable(): TorSetting.BuilderScope? = null
}
