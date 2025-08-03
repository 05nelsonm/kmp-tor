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
@file:Suppress("ConvertSecondaryConstructorToPrimary", "PropertyName", "RedundantVisibilityModifier", "UNUSED")

package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.tor.common.api.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress
import io.matthewnelson.kmp.tor.runtime.core.net.IPAddress.Companion.toIPAddress
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorOption.VirtualAddrNetworkIPv4]
 * and [TorOption.VirtualAddrNetworkIPv6].
 * */
@KmpTorDsl
public abstract class BuilderScopeVirtualAddr: TorSetting.BuilderScope {

    private constructor(option: TorOption): super(option, INIT)

    /**
     * A DSL builder scope for [TorOption.VirtualAddrNetworkIPv4].
     * */
    @KmpTorDsl
    public class NetworkIPv4: BuilderScopeVirtualAddr {

        private constructor(): super(TorOption.VirtualAddrNetworkIPv4)

        /**
         * Sets the address. If not changed, the default [IPAddress.V4]
         * address for this [TorOption] will be used.
         * */
        @KmpTorDsl
        public fun address(
            value: IPAddress.V4,
        ): NetworkIPv4 {
            _address = value
            return this
        }

        /**
         * Sets the bit value. If not changed, the default bit value
         * for this [TorOption] will be used.
         *
         * **NOTE:** Must be between `0` and `16` (inclusive), otherwise
         * will cause tor to error out.
         * */
        @KmpTorDsl
        public fun bits(
            value: Byte,
        ): NetworkIPv4 {
            _bits = value
            return this
        }

        internal companion object {

            @JvmSynthetic
            internal fun get(): NetworkIPv4 = NetworkIPv4()
        }
    }

    /**
     * A DSL builder scope for [TorOption.VirtualAddrNetworkIPv6].
     * */
    @KmpTorDsl
    public class NetworkIPv6: BuilderScopeVirtualAddr {

        private constructor(): super(TorOption.VirtualAddrNetworkIPv6)

        /**
         * Sets the address. If not changed the default [IPAddress.V6]
         * address for this [TorOption] will be used.
         * */
        @KmpTorDsl
        public fun address(
            value: IPAddress.V6,
        ): NetworkIPv6 {
            _address = value
            return this
        }

        /**
         * Sets the bit value. If not changed, the default bit value
         * for this [TorOption] will be used.
         *
         * **NOTE:** Must be between `0` and `104` (inclusive), otherwise
         * will cause tor to error out.
         * */
        @KmpTorDsl
        public fun bits(
            value: Byte,
        ): NetworkIPv6 {
            _bits = value
            return this
        }

        internal companion object {

            @JvmSynthetic
            internal fun get(): NetworkIPv6 = NetworkIPv6()
        }
    }

    @JvmField
    protected var _address: IPAddress = argument.toIPAddress()
    @JvmField
    protected var _bits: Byte = argument.substringAfterLast('/').toByte()

    @JvmSynthetic
    internal final override fun build(): TorSetting {
        val address = _address
        val bits = _bits

        val before = argument
        argument = address.canonicalHostName() + '/' + bits

        return try {
            super.build()
        } finally {
            argument = before
        }
    }
}
