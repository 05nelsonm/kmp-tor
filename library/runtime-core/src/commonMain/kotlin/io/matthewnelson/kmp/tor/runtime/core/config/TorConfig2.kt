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

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeHS
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
public class TorConfig2 private constructor(settings: Set<TorSetting>) {

    /**
     * All [TorSetting] which make up this configuration.
     * */
    @JvmField
    public val settings: Set<TorSetting> = settings.toImmutableSet()

    public companion object {

        /**
         * Opener for [BuilderScope] DSL to create [TorConfig2].
         *
         * @see [BuilderScope]
         * */
        @JvmStatic
        public fun Builder(
            block: ThisBlock<BuilderScope>,
        ): TorConfig2 {
            @OptIn(InternalKmpTorApi::class)
            return RealBuilderScopeTorConfig.build(::TorConfig2, block)
        }
    }

    /**
     * A DSL builder scope for creating [TorConfig2].
     *
     * e.g. (Kotlin)
     *
     *     val config = TorConfig.Builder {
     *
     *         TorOption.DisableNetwork.configure(true)
     *
     *         TorOption.__SocksPort.configure {
     *             port(9055.toPortEphemeral())
     *
     *             flagsSocks {
     *                 OnionTrafficOnly = true
     *                 PreferIPv6 = true
     *             }
     *         }
     *
     *         put(myPreDefinedTorSetting)
     *     }
     *
     * e.g. (Java)
     *
     *     TorConfig config = TorConfig.Builder(c -> {
     *
     *         c.configure(TorOption.DisableNetwork.INSTANCE, true);
     *
     *         c.configure(TorOption.DataDirectory.INSTANCE, File("/path/to/data"));
     *
     *         c.configure(TorOption.__SocksPort.INSTANCE, b -> {
     *             b.port(Port.Ephemeral.get(9055));
     *
     *             b.flagsSocks(f -> {
     *                 f.OnionTrafficOnly = true;
     *                 f.PreferIPv6 = true;
     *             });
     *         });
     *
     *         c.put(myPreDefinedTorSetting);
     *     });
     *
     * @see [Builder]
     * */
    @KmpTorDsl
    public abstract class BuilderScope
    @Throws(IllegalStateException::class)
    internal constructor(init: Any) {

        /**
         * Configures a [TorOption] which implements the [ConfigurableBuildable]
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
         * @throws [ClassCastException] when [ConfigurableBuildable] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun <B: TorSetting.BuilderScope> ConfigurableBuildable<B>.configure(
            block: ThisBlock<B>,
        ): BuilderScope = put(buildContract(block))

        /**
         * Configures a [TorOption] which implements the [ConfigurableBoolean]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.DisableNetwork.configure(true)
         *     }
         *
         * @throws [ClassCastException] when [ConfigurableBoolean] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigurableBoolean.configure(
            enable: Boolean,
        ): BuilderScope = put(buildContract(enable))

        /**
         * Configures a [TorOption] which implements the [ConfigurableDirectory]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.DataDirectory.configure(directory = "/path/to/data".toFile())
         *     }
         *
         * @throws [ClassCastException] when [ConfigurableDirectory] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigurableDirectory.configure(
            directory: File,
        ): BuilderScope = put(buildContract(directory))

        /**
         * Configures a [TorOption] which implements the [ConfigurableFile]
         * contract type, adding the resultant [TorSetting] to [BuilderScope].
         *
         * e.g.
         *
         *     TorConfig.Builder {
         *         TorOption.GeoIPFile.configure(file = "/path/to/geoip".toFile())
         *     }
         *
         * @throws [ClassCastException] when [ConfigurableFile] is not
         *   an instance of [TorOption].
         * */
        @KmpTorDsl
        public fun ConfigurableFile.configure(
            file: File,
        ): BuilderScope = put(buildContract(file))

        /**
         * Configures a Hidden Service, adding the resultant [TorSetting] to
         * [BuilderScope].
         *
         * @see [BuilderScopeHS]
         * @see [TorOption.HiddenServiceDir.asSetting]
         * @throws [IllegalArgumentException] if misconfigured. See [BuilderScopeHS].
         * */
        @KmpTorDsl
        public fun TorOption.HiddenServiceDir.configure(
            directory: File,
            block: ThisBlock<BuilderScopeHS>,
        ): BuilderScope = put(asSetting(directory, block))

        /**
         * Adds all the already configured [TorSetting] to [BuilderScope]
         * */
        @KmpTorDsl
        public fun putAll(
            setting: Collection<TorSetting>,
        ): BuilderScope {
            setting.forEach { put(it) }
            return this
        }

        /**
         * Adds the already configured [TorSetting] to [BuilderScope]
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

// TODO
//    /** @suppress */
//    public override fun equals(other: Any?): Boolean {
//        return super.equals(other)
//    }
//    /** @suppress */
//    public override fun hashCode(): Int {
//        return super.hashCode()
//    }
//    /** @suppress */
//    public override fun toString(): String {
//        return super.toString()
//    }
}
