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

import io.matthewnelson.kmp.tor.runtime.FileID.Companion.fidEllipses
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.jvm.JvmField

/**
 * Holder for [TorRuntime] state.
 *
 * @see [RuntimeEvent.STATE]
 * */
public class TorState private constructor(
    @JvmField
    public val daemon: Daemon,
    @JvmField
    public val network: Network,
    private val fid: String,
) {

    public constructor(daemon: Daemon, network: Network): this(daemon, network, null)
    internal constructor(daemon: Daemon, network: Network, fid: FileID?): this(daemon, network, fid?.fidEllipses ?: "")

    public operator fun component1(): Daemon = daemon
    public operator fun component2(): Network = network

    public fun copy(daemon: Daemon): TorState {
        if (daemon == this.daemon) return this
        return TorState(daemon, network, fid)
    }

    public fun copy(network: Network): TorState {
        if (network == this.network) return this
        return TorState(daemon, network, fid)
    }

    public fun copy(daemon: Daemon, network: Network): TorState {
        if (daemon == this.daemon && network == this.network) return this
        return TorState(daemon, network, fid)
    }

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
        if (fid.isNotBlank()) {
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
}
