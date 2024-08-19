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
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.apply
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
 * At a minimum, tor requires [TorOption.HiddenServiceDir] and at least
 * `1` [TorOption.HiddenServicePort] be defined. Defaults for all other
 * Hidden Service options are utilized, unless specified.
 *
 * This builder scope takes it further in that it **also requires** the
 * definition of [TorOption.HiddenServiceVersion] via [version], as well.
 *
 * The requirement for the version expression is because if, in a future
 * release of the tor C library, the default value changes to a newer
 * version (and subsequently, the old version deprecated) that may be a
 * source of conflict if an explicit definition is not there (surprise
 * upgrade from `v3` -> `v4`?). In that event, `kmp-tor` consumers using this
 * builder scope will be unaffected by the change to the tor C library. A
 * conscious decision to migrate to the new version, and modifying their
 * builder usages would need to be made to update the [version] invocation.
 *
 * **NOTE:** Any misconfiguration will result in an [IllegalArgumentException]
 * when build is called.
 *
 * e.g. (Minimum viable configuration with [version] and [port] defined)
 *
 *     // Also available via HiddenServiceDir.asSetting
 *     TorConfig.Builder {
 *         TorOption.HiddenServiceDir.configure(directory = "/path/to/this/hs/dir".toFile()) {
 *             // Must be defined
 *             version(3)
 *
 *             // At least 1 port must be defined
 *             port(virtual = Port.HTTPS) {
 *                 try {
 *                     target(unixSocket = "/path/to/server/dir/uds.sock".toFile())
 *                 } catch (_: UnsupportedOperationException) {
 *                     target(port = 8443.toPort())
 *                 }
 *             }
 *
 *             // ...
 *         }
 *     }
 * */
@KmpTorDsl
@OptIn(ExperimentalKmpTorApi::class)
public class BuilderScopeHS: TorSetting.BuilderScope, BuilderScopeHSPort.DSL<BuilderScopeHS> {

    private constructor(directory: File): super(TorOption.HiddenServiceDir, INIT) {
        argument = directory.absoluteNormalizedFile.path
    }

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
     * Sets [TorOption.HiddenServiceVersion] for this Hidden Service
     * instance. Currently, the only supported version is `v3`. Anything
     * else will cause a build failure.
     *
     * **NOTE:** This is required to be defined.
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
     * Otherwise, the default will be used.
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
     * Otherwise, the default will be used.
     * */
    @KmpTorDsl
    public fun dirGroupReadable(
        enable: Boolean,
    ): BuilderScopeHS {
        _dirGroupReadable = enable
        return this
    }

    // TODO: [TorOption.HiddenServiceExportCircuitID]

    /**
     * Sets [TorOption.HiddenServiceOnionBalanceInstance], if desired.
     * Otherwise, the default will be used.
     * */
    @KmpTorDsl
    public fun onionBalanceInstance(
        enable: Boolean,
    ): BuilderScopeHS {
        _onionBalanceInstance = enable
        return this
    }

    /**
     * Sets [TorOption.HiddenServiceMaxStreams], if desired.
     * Otherwise, the default will be used.
     *
     * **NOTE:** Must be between [Port.MIN] and [Port.MAX] (inclusive).
     * Otherwise, will cause a build failure.
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
     * Otherwise, the default will be used.
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
     * Otherwise, the default will be used.
     *
     * **NOTE:** For [version] 3 Hidden Service, the acceptable range
     * is `1` to `20` (inclusive). Otherwise, will cause a build failure.
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
        @Throws(IllegalArgumentException::class)
        internal fun build(
            directory: File,
            block: ThisBlock<BuilderScopeHS>,
        ): TorSetting = BuilderScopeHS(directory)
            .apply(block)
            .build()

        private val HSV_3 by lazy {
            TorOption.HiddenServiceVersion.toLineItem(argument = 3.toString())
        }
    }

    @JvmSynthetic
    @Throws(IllegalArgumentException::class)
    internal override fun build(): TorSetting {
        val (hsVersion, maxIntroductionPoints) = when (_version) {
            3 -> HSV_3 to 20
            else -> throw IllegalArgumentException(
                "Invalid ${TorOption.HiddenServiceVersion} of $_version."
            )
        }

        val ports = _ports.toSet()
        require(ports.isNotEmpty()) {
            "A minimum of 1 ${TorOption.HiddenServicePort} must be configured."
        }

        val maxStreams = _maxStreams?.let { num ->
            require(num in Port.MIN..Port.MAX) {
                "Invalid ${TorOption.HiddenServiceMaxStreams} of $num." +
                " Must be between ${Port.MIN} and ${Port.MAX} (inclusive)."
            }

            TorOption.HiddenServiceMaxStreams.toLineItem(num.toString())
        }

        val numIntroductionPoints = _numIntroductionPoints?.let { num ->
            require(num in 1..maxIntroductionPoints) {
                "Invalid ${TorOption.HiddenServiceNumIntroductionPoints} of $num." +
                " Must be between 1 and $maxIntroductionPoints (inclusive) for $hsVersion."
            }

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
