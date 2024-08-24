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
@file:Suppress("FunctionName")

package io.matthewnelson.kmp.tor.runtime.core.config

import io.matthewnelson.immutable.collections.immutableSetOf
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.config.builder.RealBuilderScopeTorConfig
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Holder for [TorSetting].
 *
 * @see [Builder]
 * @see [TorSetting.filterByAttribute]
 * @see [TorSetting.filterByOption]
 * */
public class TorConfig private constructor(settings: Set<TorSetting>): Iterable<TorSetting> {

    /**
     * All [TorSetting] which make up this configuration.
     * */
    @JvmField
    public val settings: Set<TorSetting> = settings.toImmutableSet()

    public override fun iterator(): Iterator<TorSetting> = settings.iterator()

    public companion object {

        /**
         * Opener for [BuilderScope] DSL to create [TorConfig].
         *
         * @see [BuilderScope]
         * */
        @JvmStatic
        public fun Builder(
            block: ThisBlock<BuilderScope>,
        ): TorConfig {
            @OptIn(InternalKmpTorApi::class)
            return RealBuilderScopeTorConfig.build(::TorConfig, block)
        }

        /**
         * Wraps a single [TorSetting] in [TorConfig].
         * */
        @JvmStatic
        public fun TorSetting.toConfig(): TorConfig {
            return TorConfig(immutableSetOf(this))
        }
    }

    /**
     * A DSL builder scope for creating [TorConfig].
     *
     * e.g. (Kotlin)
     *
     *     val config = TorConfig.Builder {
     *
     *         TorOption.DisableNetwork.configure(true)
     *
     *         TorOption.DataDirectory.configure("/path/to/data".toFile())
     *
     *         TorOption.__SocksPort.configure {
     *             port(9055.toPortEphemeral())
     *             flagsSocks {
     *                 OnionTrafficOnly = true
     *                 PreferIPv6 = true
     *             }
     *         }
     *
     *         try {
     *             TorOption.__TransPort.tryConfigure {
     *                 auto()
     *                 flagsIsolation {
     *                     IsolateClientProtocol = true
     *                 }
     *             }
     *         } catch(_: UnsupportedOperationException) {
     *             // recover
     *         }
     *
     *         put(somePreDefinedTorSetting)
     *     }
     *
     * e.g. (Java)
     *
     *     TorConfig config = TorConfig.Builder(c -> {
     *
     *         c.configure(TorOption.DisableNetwork.INSTANCE, true);
     *
     *         c.configure(TorOption.DataDirectory.INSTANCE, new File("/path/to/data"));
     *
     *         c.configure(TorOption.__SocksPort.INSTANCE, b -> {
     *             b.port(Port.Ephemeral.get(9055));
     *             b.flagsSocks(f -> {
     *                 f.OnionTrafficOnly = true;
     *                 f.PreferIPv6 = true;
     *             });
     *         });
     *
     *         try {
     *             c.tryConfigure(TorOption.TransPort.INSTANCE, b -> {
     *                 b.auto();
     *                 b.flagsIsolation(f -> {
     *                     f.IsolateClientProtocol = true;
     *                 });
     *             });
     *         } catch (UnsupportedOperationException e) {
     *             // recover
     *         }
     *
     *         c.put(somePreDefinedTorSetting);
     *     });
     *
     * @see [Builder]
     * @see [ConfigurableContract]
     * */
    @KmpTorDsl
    public abstract class BuilderScope
    @Throws(IllegalStateException::class)
    internal constructor(init: Any) {

        /**
         * Configures a [TorOption] which implements the [ConfigureBuildable]
         * contract type for [TorSetting.BuilderScope] of type [B], adding the
         * resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.ConnectionPadding.configure {
         *             disable()
         *         }
         *     }
         *
         * @throws [ClassCastException] when [ConfigureBuildable] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun <B: TorSetting.BuilderScope> ConfigureBuildable<B>.configure(
            block: ThisBlock<B>,
        ): BuilderScope = put(buildContract(block))

        /**
         * Configures a [TorOption] which implements the [ConfigureBuildableTry]
         * contract type for [TorSetting.BuilderScope] of type [B], adding the
         * resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         try {
         *             TorOption.TransPort.tryConfigure {
         *                 auto()
         *             }
         *         } catch(_: UnsupportedOperationException) {
         *             // Unavailable for current host
         *             // do something else
         *         }
         *
         *         // No try/catch needed because we know requirements
         *         // for BuilderScopeHS are going to be met.
         *         TorOption.HiddenServiceDir.tryConfigure {
         *             directory("/path/to/this/hs/dir".toFile())
         *             version(3)
         *             port(virtual = Port.HTTP) {
         *                 target(port = 8080.toPort())
         *             }
         *         }
         *     }
         *
         * @throws [ClassCastException] when [ConfigureBuildableTry] is not
         *   an instance of [TorOption].
         * @throws [IllegalArgumentException] if [B] was misconfigured when
         *   build was called on [block] closure.
         * @throws [UnsupportedOperationException] when the [TorOption] has
         *   host and/or environment requirements that were not met.
         * */
        @KmpTorDsl
        public fun <B: TorSetting.BuilderScope> ConfigureBuildableTry<B>.tryConfigure(
            block: ThisBlock<B>,
        ): BuilderScope = put(buildContract(block))

        /**
         * Configures a [TorOption] which implements the [ConfigureBoolean]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.DisableNetwork.configure(true)
         *     }
         *
         * @throws [ClassCastException] when [ConfigureBoolean] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigureBoolean.configure(
            enable: Boolean,
        ): BuilderScope = put(buildContract(enable))

        /**
         * Configures a [TorOption] which implements the [ConfigureDirectory]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.DataDirectory.configure(directory = "/path/to/data".toFile())
         *     }
         *
         * @throws [ClassCastException] when [ConfigureDirectory] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigureDirectory.configure(
            directory: File,
        ): BuilderScope = put(buildContract(directory))

        /**
         * Configures a [TorOption] which implements the [ConfigureFile]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.GeoIPFile.configure(file = "/path/to/geoip".toFile())
         *     }
         *
         * @throws [ClassCastException] when [ConfigureFile] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigureFile.configure(
            file: File,
        ): BuilderScope = put(buildContract(file))

        /**
         * Configures a [TorOption] which implements the [ConfigureInterval]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g. (Disabling HeartbeatPeriod)
         *
         *     TorConfig.Builder {
         *         TorOption.HeartbeatPeriod.configure(0, IntervalUnit.SECONDS)
         *     }
         *
         * @throws [ClassCastException] when [ConfigureInterval] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigureInterval.configure(
            num: Int,
            interval: IntervalUnit,
        ): BuilderScope = put(buildContract(num, interval))

        /**
         * Configures a [TorOption] which implements the [ConfigureIntervalMsec]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.LogTimeGranularity.configure(milliseconds = 2_000)
         *     }
         *
         * @throws [ClassCastException] when [ConfigureIntervalMsec] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigureIntervalMsec.configure(
            milliseconds: Int,
        ): BuilderScope = put(buildContract(milliseconds))

        /**
         * Add already configured [TorSetting] to [BuilderScope].
         * */
        @KmpTorDsl
        public fun putAll(
            setting: Collection<TorSetting>,
        ): BuilderScope {
            setting.forEach { put(it) }
            return this
        }

        /**
         * Add an already configured [TorSetting] to [BuilderScope].
         * */
        @KmpTorDsl
        public abstract fun put(
            setting: TorSetting,
        ): BuilderScope

        protected companion object {
            @JvmSynthetic
            internal val INIT = Any()
        }

        init {
            check(init == INIT) { "TorConfig.BuilderScope cannot be extended" }
        }
    }

    /** @suppress */
    public override fun equals(other: Any?): Boolean = other is TorConfig && other.settings == settings
    /** @suppress */
    public override fun hashCode(): Int = 5 * 42 + settings.hashCode()
    /** @suppress */
    public override fun toString(): String = buildString { settings.joinTo(this, separator = "\n") }
}
