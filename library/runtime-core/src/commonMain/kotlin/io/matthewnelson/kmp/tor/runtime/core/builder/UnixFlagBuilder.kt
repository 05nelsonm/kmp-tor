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
@file:Suppress("FunctionName", "PropertyName")

package io.matthewnelson.kmp.tor.runtime.core.builder

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Configure flags specific to [TorConfig.__ControlPort] and
 * [TorConfig.__SocksPort] when they are set up as Unix Sockets.
 *
 * Flags can be configured no matter if the current configuration
 * is a TCP Port or not. They will only be added to the [TorConfig.Setting]
 * if the final builder result is that of a Unix Socket.
 *
 * - `null`  - no action (default)
 * - `true`  - add the flag if not present
 * - `false` - remove the flag if present
 *
 * [tor-man#ControlPort](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#ControlPort)
 * [tor-man#OtherSocksPortFlags](https://github.com/05nelsonm/kmp-tor-resource/blob/master/docs/tor-man.adoc#OtherSocksPortFlags)
 * */
@KmpTorDsl
public class UnixFlagBuilder private constructor() {

    @JvmField
    public var GroupWritable: Boolean? = null

    @JvmField
    public var WorldWritable: Boolean? = null

//    /**
//     * Only applicable for [TorConfig.__ControlPort], otherwise
//     * is ignored if configured.
//     * */
    @JvmField
    public var RelaxDirModeCheck: Boolean? = null

    internal companion object {

        @JvmSynthetic
        internal fun configure(
            isControl: Boolean,
            flags: MutableSet<String>,
            block: ThisBlock<UnixFlagBuilder>,
        ) {
            val b = UnixFlagBuilder().apply(block)

            b.GroupWritable?.let {
                val flag = "GroupWritable"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.WorldWritable?.let {
                val flag = "WorldWritable"
                if (it) flags.add(flag) else flags.remove(flag)
            }

            if (!isControl) return
            b.RelaxDirModeCheck?.let {
                val flag = "RelaxDirModeCheck"
                if (it) flags.add(flag) else flags.remove(flag)
            }
        }
    }

    /**
     * Not meant for public use.
     * */
    @InternalKmpTorApi
    public interface DSL<out R: Any> {

        /**
         * Flag configuration for a [TorConfig.Keyword] that
         * has been assigned a Unix Socket path.
         * */
        @KmpTorDsl
        public fun unixFlags(
            block: ThisBlock<UnixFlagBuilder>,
        ): R
    }
}
