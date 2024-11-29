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
@file:Suppress("ConvertSecondaryConstructorToPrimary")

package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.file.*
import io.matthewnelson.kmp.tor.common.api.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.net.Port
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopeHSPort.Companion.configureHSPort
import io.matthewnelson.kmp.tor.runtime.core.internal.absoluteNormalizedFile
import io.matthewnelson.kmp.tor.runtime.core.internal.byte
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorOption.HiddenServiceDir] and
 * other applicable [TorOption] for Hidden Services (i.e. those options
 * that contain the attribute [TorOption.Attribute.HIDDEN_SERVICE]).
 *
 * **TL;DR** [directory], [version], and at least 1 [port] are required.
 *
 * At a minimum, tor requires [TorOption.HiddenServiceDir] and at least
 * `1` [TorOption.HiddenServicePort] be defined. Tor then uses its hard
 * coded defaults for all other Hidden Service options, unless overridden.
 *
 * This builder scope takes it further in that it **also requires** the
 * definition of [TorOption.HiddenServiceVersion], as well (see [version]).
 *
 * The requirement for the version expression is because if, in a future
 * release of the tor C library, the default value changes to a newer
 * version (and subsequently, the old version deprecated) that may be a
 * source of conflict if an explicit definition is not there (surprise
 * upgrade from `v3` -> `v4`?). In that event, `kmp-tor` consumers using this
 * builder scope will be unaffected by the change to the tor C library. A
 * conscious decision to migrate to the new version, and update usages
 * of this builder, would need to be made.
 *
 * **NOTE:** Any misconfiguration will result in an [IllegalArgumentException]
 * when the scope goes to build.
 *
 * e.g. (Minimum requirements with [directory], [version] and [port] defined)
 *
 *     // Also available via HiddenServiceDir.asSetting
 *     TorConfig.Builder {
 *
 *         // No try/catch needed b/c minimums are met
 *         TorOption.HiddenServiceDir.tryConfigure {
 *             // Must be defined
 *             directory("/path/to/this/hs/dir".toFile())
 *
 *             // Must be defined
 *             version(3)
 *
 *             // At least 1 port must be defined
 *             port(virtual = Port.HTTPS) {
 *                 try {
 *                     target(unixSocket = "/path/to/server/uds.sock".toFile())
 *                 } catch (_: UnsupportedOperationException) {
 *                     target(port = 8443.toPort())
 *                 }
 *             }
 *
 *             // ...
 *         }
 *     }
 *
 * @see [TorOption.HiddenServiceDir.asSetting]
 * @see [TorConfig.BuilderScope.tryConfigure]
 * */
@KmpTorDsl
public class BuilderScopeHS: TorSetting.BuilderScope, BuilderScopeHSPort.DSL<BuilderScopeHS> {

    private constructor(): super(TorOption.HiddenServiceDir, INIT)

    // Required to be defined
    private var _version: Int? = null
    private val _ports: LinkedHashSet<TorSetting.LineItem> = LinkedHashSet(1, 1.0f)

    // Tor defaults if undefined
    private var _allowUnknownPorts: Boolean? = null
    private var _dirGroupReadable: Boolean? = null
    private var _onionBalanceInstance: Boolean? = null
    private var _maxStreams: Int? = null
    private var _maxStreamsCloseCircuit: Boolean? = null
    private var _numIntroductionPoints: Int? = null

    /**
     * Sets the [argument] to the specified directory.
     *
     * **NOTE:** Provided [File] is always sanitized using
     * [File.absoluteFile] + [File.normalize].
     * */
    @KmpTorDsl
    public fun directory(
        dir: File,
    ): BuilderScopeHS {
        argument = dir.absoluteNormalizedFile.path
        return this
    }

    /**
     * Sets [TorOption.HiddenServiceVersion] for this Hidden Service
     * instance. Currently, the only supported version is `v3`. Anything
     * else will cause a failure when this scope builds.
     *
     * e.g.
     *
     *     version(3)
     * */
    @KmpTorDsl
    public fun version(
        num: Int,
    ): BuilderScopeHS {
        _version = num
        return this
    }

    // See BuilderScopeHSPort.DSL interface
    @KmpTorDsl
    public override fun port(
        virtual: Port
    ): BuilderScopeHS = port(virtual) {}

    // See BuilderScopeHSPort.DSL interface
    @KmpTorDsl
    public override fun port(
        virtual: Port,
        block: ThisBlock<BuilderScopeHSPort>,
    ): BuilderScopeHS = configureHSPort(virtual, _ports, block)

    /**
     * Sets [TorOption.HiddenServiceAllowUnknownPorts], if desired.
     * */
    @KmpTorDsl
    public fun allowUnknownPorts(
        enable: Boolean,
    ): BuilderScopeHS {
        _allowUnknownPorts = enable
        return this
    }

    /**
     * Sets [TorOption.HiddenServiceDirGroupReadable], if desired.
     * */
    @KmpTorDsl
    public fun dirGroupReadable(
        enable: Boolean,
    ): BuilderScopeHS {
        _dirGroupReadable = enable
        return this
    }

    // TODO: [TorOption.HiddenServiceExportCircuitID]
    // TODO: [TorOption.HiddenServiceOnionBalanceInstance]

    /**
     * Sets [TorOption.HiddenServiceMaxStreams], if desired.
     *
     * **NOTE:** Must be between [Port.MIN] and [Port.MAX] (inclusive).
     * Otherwise, will cause tor to error out.
     * */
    @KmpTorDsl
    public fun maxStreams(
        num: Int,
    ): BuilderScopeHS {
        _maxStreams = num
        return this
    }

    /**
     * Sets [TorOption.HiddenServiceMaxStreamsCloseCircuit], if desired.
     * */
    @KmpTorDsl
    public fun maxStreamsCloseCircuit(
        enable: Boolean,
    ): BuilderScopeHS {
        _maxStreamsCloseCircuit = enable
        return this
    }

    /**
     * Sets [TorOption.HiddenServiceNumIntroductionPoints], if desired.
     *
     * **NOTE:** For [version] 3 Hidden Service, the acceptable range
     * is from `1` to `20` (inclusive). Otherwise, will cause tor to
     * error out.
     * */
    @KmpTorDsl
    public fun numIntroductionPoints(
        num: Int,
    ): BuilderScopeHS {
        _numIntroductionPoints = num
        return this
    }

    // TODO: [TorOption.HiddenServiceEnableIntroDoSDefense]
    // TODO: [TorOption.HiddenServiceEnableIntroDoSBurstPerSec]
    // TODO: [TorOption.HiddenServiceEnableIntroDoSRatePerSec]
    // TODO: [TorOption.HiddenServicePoWDefensesEnabled]
    // TODO: [TorOption.HiddenServicePoWQueueRate]
    // TODO: [TorOption.HiddenServicePoWQueueBurst]

    internal companion object {

        @JvmSynthetic
        internal fun get(): BuilderScopeHS {
            return BuilderScopeHS()
        }

        private val HSV_3 by lazy {
            TorOption.HiddenServiceVersion.toLineItem(argument = 3.toString())
        }
    }

    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    internal override fun build(): TorSetting {
        require(argument.isNotEmpty()) {
            "Invalid ${TorOption.HiddenServiceDir}. Cannot be empty. Was `directory` not called?"
        }

        val hsVersion = when (_version) {
            3 -> HSV_3
            else -> throw IllegalArgumentException(
                "Invalid ${TorOption.HiddenServiceVersion} of $_version."
            )
        }

        val ports = _ports.toSet()
        require(ports.isNotEmpty()) {
            "A minimum of 1 ${TorOption.HiddenServicePort} must be configured."
        }

        val maxStreams = _maxStreams?.let { num ->
            TorOption.HiddenServiceMaxStreams.toLineItem(num.toString())
        }

        val numIntroductionPoints = _numIntroductionPoints?.let { num ->
            TorOption.HiddenServiceNumIntroductionPoints.toLineItem(num.toString())
        }

        // Required
        others.add(hsVersion)
        others.addAll(ports)

        // Tor defaults if undefined
        _allowUnknownPorts?.byte?.toString()?.let { argument ->
            others.add(TorOption.HiddenServiceAllowUnknownPorts.toLineItem(argument))
        }
        _dirGroupReadable?.byte?.toString()?.let { argument ->
            others.add(TorOption.HiddenServiceDirGroupReadable.toLineItem(argument))
        }
        _onionBalanceInstance?.byte?.toString()?.let { argument ->
            others.add(TorOption.HiddenServiceOnionBalanceInstance.toLineItem(argument))
        }
        maxStreams?.let { item -> others.add(item) }
        _maxStreamsCloseCircuit?.byte?.toString()?.let { argument ->
            others.add(TorOption.HiddenServiceMaxStreamsCloseCircuit.toLineItem(argument))
        }
        numIntroductionPoints?.let { item -> others.add(item) }

        // TODO: PoW options

        return try {
            super.build()
        } finally {
            others.clear()
        }
    }
}
