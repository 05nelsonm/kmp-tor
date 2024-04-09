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
@file:Suppress("SpellCheckingInspection")

package io.matthewnelson.kmp.tor.runtime.core.builder

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.address.Port
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

// TODO: allowPortReassignment
//  not here, but in :runtime configuration DSL

@OptIn(InternalKmpTorApi::class)
public sealed class TCPPortBuilder private constructor() {

    /**
     * For configuring [TorConfig.__ControlPort] to use a TCP Port.
     * */
    @KmpTorDsl
    public class Control private constructor(): TCPPortBuilder(),
        DSLAuto<Control>,
        DSLPort<Control>
    {
        private var argument: String = TorConfig.AUTO
        private var allowReassign: Boolean = true

        @KmpTorDsl
        public override fun auto(): Control {
            argument = TorConfig.AUTO
            return this
        }

        @KmpTorDsl
        public override fun port(port: Port.Proxy): Control {
            argument = port.toString()
            return this
        }

        @KmpTorDsl
        public override fun reassignable(allow: Boolean): Control {
            allowReassign = allow
            return this
        }

        internal companion object {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<Control>
            ): Pair<Boolean, String> {
                val b = Control().apply(block)
                return b.allowReassign to b.argument
            }
        }
    }

    /**
     * For configuring [TorConfig.__SocksPort] to use a TCP Port.
     * */
    @KmpTorDsl
    public class Socks private constructor(): TCPPortBuilder(),
        DSLAuto<Socks>,
        DSLDisable<Socks>,
        DSLPort<Socks>
    {
        private var argument: String = "9050"
        private var allowReassign: Boolean = true

        @KmpTorDsl
        public override fun auto(): Socks {
            argument = TorConfig.AUTO
            return this
        }

        @KmpTorDsl
        public override fun disable(): Socks {
            argument = "0"
            return this
        }

        @KmpTorDsl
        public override fun port(port: Port.Proxy): Socks {
            argument = port.toString()
            return this
        }

        @KmpTorDsl
        public override fun reassignable(allow: Boolean): Socks {
            allowReassign = allow
            return this
        }

        internal companion object {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<Socks>
            ): Pair<Boolean, String> {
                val b = Socks().apply(block)
                return b.allowReassign to b.argument
            }
        }
    }

    /**
     * For configuring [TorConfig.HiddenServicePort] to use a specific
     * target TCP Port, instead of utilizing the specified
     * [TorConfig.HiddenServicePort.virtual] port.
     * */
    @KmpTorDsl
    public class HiddenService private constructor(): TCPPortBuilder() {

        @JvmField
        public var target: Port? = null

        internal companion object {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<HiddenService>
            ): String? = HiddenService().apply(block).target?.toString()
        }
    }

    @InternalKmpTorApi
    public interface DSL<out T: TCPPortBuilder, out R: Any> {

        /**
         * For a [TorConfig.Keyword] that can be configured to use
         * a TCP Port, or a Unix Socket.
         * */
        @KmpTorDsl
        public fun asPort(
            block: ThisBlock<T>,
        ): R
    }

    @InternalKmpTorApi
    public interface DSLAuto<out R: Any> {

        /**
         * Sets the port to "auto", indicating that Tor should
         * pick an available port.
         * */
        @KmpTorDsl
        public fun auto(): R
    }

    @InternalKmpTorApi
    public interface DSLDisable<out R: Any> {

        /**
         * Disables the [TorConfig.Keyword] by setting its port
         * to "0".
         * */
        @KmpTorDsl
        public fun disable(): R
    }

    // TODO: IPAddress/Localhost

    @InternalKmpTorApi
    public interface DSLPort<out R: Any>: DSLReassign<R> {

        /**
         * Specify a port
         * */
        @KmpTorDsl
        public fun port(port: Port.Proxy): R
    }

    @InternalKmpTorApi
    public interface DSLReassign<out R: Any> {

        /**
         * In the event that a configured TCP port is unavailable on the host
         * device, tor will fail to start.
         *
         * Setting this to true (the default value) will add
         * [TorConfig.Extra.AllowReassign] to the [TorConfig.Setting] extras,
         * resulting in reassignment of the unavailable TCP port argument to
         * "auto" prior to starting tor via `TorRuntime`.
         *
         * Port availability is verified just prior to each `TorRuntime` startup
         * in order to mitigate potential failures.
         *
         * If false, no port availability checks will be performed. This may
         * result in tor start failure if a configured port is taken, but that
         * **could** be a desired behavior depending on your use case of
         * `TorRuntime`.
         * */
        @KmpTorDsl
        public fun reassignable(allow: Boolean): R
    }
}
