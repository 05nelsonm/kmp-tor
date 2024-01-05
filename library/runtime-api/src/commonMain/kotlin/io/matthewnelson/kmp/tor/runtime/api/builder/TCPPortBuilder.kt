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
package io.matthewnelson.kmp.tor.runtime.api.builder

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.api.ThisBlock
import io.matthewnelson.kmp.tor.runtime.api.address.Port
import io.matthewnelson.kmp.tor.runtime.api.apply
import io.matthewnelson.kmp.tor.runtime.api.TorConfig
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
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
        @get:JvmName("port")
        public var port: String = TorConfig.AUTO
            private set

        @KmpTorDsl
        public override fun auto(): Control {
            port = TorConfig.AUTO
            return this
        }

        @KmpTorDsl
        public override fun port(port: Port.Proxy): Control {
            this.port = port.toString()
            return this
        }

        internal companion object {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<Control>
            ): String = Control().apply(block).port
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
        @get:JvmName("port")
        public var port: String = "9050"
            private set

        @KmpTorDsl
        public override fun auto(): Socks {
            port = TorConfig.AUTO
            return this
        }

        @KmpTorDsl
        public override fun disable(): Socks {
            port = "0"
            return this
        }

        @KmpTorDsl
        public override fun port(port: Port.Proxy): Socks {
            this.port = port.toString()
            return this
        }

        internal companion object {

            @JvmSynthetic
            internal fun build(
                block: ThisBlock<Socks>
            ): String = Socks().apply(block).port
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
    public interface DSLPort<out R: Any> {

        /**
         * Specify a port
         * */
        @KmpTorDsl
        public fun port(port: Port.Proxy): R
    }
}
