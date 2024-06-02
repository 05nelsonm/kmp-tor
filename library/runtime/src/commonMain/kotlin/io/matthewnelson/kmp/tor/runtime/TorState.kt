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
package io.matthewnelson.kmp.tor.runtime

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.resource.SynchronizedObject
import io.matthewnelson.kmp.tor.core.resource.synchronized
import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Holder for [TorRuntime] state.
 *
 * @see [TorRuntime.state]
 * @see [RuntimeEvent.STATE]
 * */
public class TorState private constructor(
    @JvmField
    public val daemon: Daemon,
    @JvmField
    public val network: Network,
    private val fid: String?,
) {

    public constructor(daemon: Daemon, network: Network): this(daemon, network, null)

    @JvmField
    public val isOff: Boolean = daemon is Daemon.Off
    @JvmField
    public val isOn: Boolean = daemon is Daemon.On
    @JvmField
    public val isStarting: Boolean = daemon is Daemon.Starting
    @JvmField
    public val isStopping: Boolean = daemon is Daemon.Stopping

    @JvmField
    public val isNetworkDisabled: Boolean = network is Network.Disabled
    @JvmField
    public val isNetworkEnabled: Boolean = network is Network.Enabled

    public operator fun component1(): Daemon = daemon
    public operator fun component2(): Network = network

    public fun copy(daemon: Daemon): TorState = copy(daemon, network)
    public fun copy(network: Network): TorState = copy(daemon, network)
    public fun copy(daemon: Daemon, network: Network): TorState {
        if (daemon == this.daemon && network == this.network) return this
        return TorState(daemon, network, fid)
    }

    /**
     * State of the tor process
     *
     * @param [bootstrap] The percent for which tor has bootstrapped to
     *   the network. Will **always** be between 0 and 100 (inclusive)
     * */
    public sealed class Daemon private constructor(
        @JvmField
        public val bootstrap: Byte
    ) {

        @JvmField
        public val isBootstrapped: Boolean = bootstrap == 100.toByte()

        /**
         * The tor process is **not** running
         * */
        public data object Off: Daemon(bootstrap = 0)

        /**
         * The tor process is running
         * */
        public class On(bootstrap: Byte): Daemon(bootstrap.coerceIn(0, 100)) {

            public override fun equals(other: Any?): Boolean = other is On && other.bootstrap == bootstrap
            public override fun hashCode(): Int = 17 * 42 + toString().hashCode() + bootstrap
        }

        /**
         * An intermediary state whereby [TorRuntime] is starting the
         * tor process.
         * */
        public data object Starting: Daemon(bootstrap = 0)

        /**
         * An intermediary state whereby [TorRuntime] is stopping the
         * tor process.
         * */
        public data object Stopping: Daemon(bootstrap = 0)

        public final override fun toString(): String = when (this) {
            is Off -> "Tor: Off"
            is On -> "Tor: On"
            is Starting -> "Tor: Starting"
            is Stopping -> "Tor: Stopping"
        }
    }

    /**
     * The [TorConfig.DisableNetwork] setting for which the tor
     * process currently has set.
     * */
    public sealed class Network private constructor() {

        public data object Disabled: Network()
        public data object Enabled: Network()

        public final override fun toString(): String = when (this) {
            is Disabled -> "Network: Disabled"
            is Enabled -> "Network: Enabled"
        }
    }

    public override fun equals(other: Any?): Boolean {
        return  other is TorState
                && other.daemon == daemon
                && other.network == network
    }

    public override fun hashCode(): Int {
        var result = 17
        result = result * 42 + daemon.hashCode()
        result = result * 42 + network.hashCode()
        return result
    }

    public override fun toString(): String = buildString {
        append("TorState[")
        if (!fid.isNullOrBlank()) {
            append("fid=")
            append(fid)
            append(", ")
        }

        append("daemon=")
        when (daemon) {
            is Daemon.Off -> append("Off")
            is Daemon.On -> {
                append("On{")
                append(daemon.bootstrap)
                append("%}")
            }
            is Daemon.Starting -> append("Starting")
            is Daemon.Stopping -> append("Stopping")
        }

        append(", network=")
        when (network) {
            is Network.Disabled -> "Disabled"
            is Network.Enabled -> "Enabled"
        }.let { append(it) }

        append(']')
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            daemon: Daemon,
            network: Network,
            fid: FileID?,
        ): TorState = TorState(
            daemon,
            network,
            fid?.fidEllipses,
        )
    }

    internal interface Manager {
        fun update(daemon: Daemon? = null, network: Network? = null)
    }

    @OptIn(InternalKmpTorApi::class)
    internal abstract class AbstractManager internal constructor(fid: FileID?): Manager {

        @Volatile
        private var _isReady: Boolean = false
        @Volatile
        private var _state: TorState = of(Daemon.Off, Network.Disabled, fid = fid)
        private val lock = SynchronizedObject()

        internal val isReady: Boolean get() = _isReady
        internal val state: TorState get() = _state

        protected abstract fun notify(old: TorState, new: TorState)
        protected abstract fun notifyReady()

        @Suppress("LocalVariableName")
        final override fun update(daemon: Daemon?, network: Network?) {
            if (daemon == null && network == null) return


            val (diff, notifyReady) = synchronized(lock) {
                val old = _state

                val _daemon = daemon ?: old.daemon
                val _network = if (_daemon is Daemon.Off) {
                    Network.Disabled
                } else {
                    network ?: old.network
                }

                val new = old.copy(_daemon, _network)
                val diff = Diff.of(old, new) ?: return@synchronized null

                _state = new

                val notifyReady = if (_isReady) {
                    if (!new.daemon.isBootstrapped) {
                        // Reset
                        _isReady = false
                    }
                    false
                } else {
                    if (new.daemon.isBootstrapped && new.isNetworkEnabled) {
                        _isReady = true
                        true
                    } else {
                        false
                    }
                }

                diff to notifyReady
            } ?: return

            notify(diff.old, diff.new)

            if (!notifyReady) return
            notifyReady()
        }

        private class Diff private constructor(val old: TorState, val new: TorState) {

            companion object {

                @JvmStatic
                fun of(old: TorState, new: TorState): Diff? {
                    // No changes
                    if (old == new) return null

                    // on -> off
                    // on -> stopping
                    // on -> on
                    if (old.isOn && new.isStarting) return null

                    // off -> starting
                    // off -> off
                    if (old.isOff && new.isOn) return null
                    if (old.isOff && new.isStopping) return null

                    // stopping -> off
                    // stopping -> starting
                    // stopping -> stopping
                    if (old.isStopping && new.isOn) return null

                    // starting -> on
                    // starting -> off
                    // starting -> stopping
                    // starting -> starting
                    return Diff(old, new)
                }
            }
        }
    }
}
