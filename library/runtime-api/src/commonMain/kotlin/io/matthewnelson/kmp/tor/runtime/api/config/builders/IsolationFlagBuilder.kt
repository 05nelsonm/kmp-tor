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

package io.matthewnelson.kmp.tor.runtime.api.config.builders

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.api.ThisBlock
import io.matthewnelson.kmp.tor.runtime.api.apply
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

/**
 * Configure a Ports Isolation Flags
 *
 * - `null`  - no action (default)
 * - `true`  - add the flag if not present
 * - `false` - remove the flag if present
 *
 * [SocksPort](https://2019.www.torproject.org/docs/tor-manual.html.en#SocksPort)
 * */
@KmpTorDsl
public class IsolationFlagBuilder private constructor() {

    @JvmField
    public var IsolateClientAddr: Boolean? = null
    @JvmField
    public var IsolateSOCKSAuth: Boolean? = null
    @JvmField
    public var IsolateClientProtocol: Boolean? = null
    @JvmField
    public var IsolateDestPort: Boolean? = null
    @JvmField
    public var IsolateDestAddr: Boolean? = null
    @JvmField
    public var KeepAliveIsolateSOCKSAuth: Boolean? = null

    private var sessionGroupId: Int? = null

    /**
     * Declaring an id greater than or equal to 0 will add
     * the flag.
     *
     * Declaring an id less than 0 will remove flag if present.
     * */
    @KmpTorDsl
    public fun SessionGroup(id: Int): IsolationFlagBuilder {
        sessionGroupId = id
        return this
    }

    @InternalKmpTorApi
    public interface DSL<out R: Any> {

        @KmpTorDsl
        public fun isolationFlags(
            block: ThisBlock<IsolationFlagBuilder>,
        ): R
    }

    internal companion object {

        @JvmSynthetic
        internal fun configure(
            flags: MutableSet<String>,
            block: ThisBlock<IsolationFlagBuilder>,
        ) {
            val b = IsolationFlagBuilder().apply(block)

            b.IsolateClientAddr?.let {
                val flag = "IsolateClientAddr"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.IsolateSOCKSAuth?.let {
                val flag = "IsolateSOCKSAuth"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.IsolateClientProtocol?.let {
                val flag = "IsolateClientProtocol"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.IsolateDestPort?.let {
                val flag = "IsolateDestPort"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.IsolateDestAddr?.let {
                val flag = "IsolateDestAddr"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.KeepAliveIsolateSOCKSAuth?.let {
                val flag = "KeepAliveIsolateSOCKSAuth"
                if (it) flags.add(flag) else flags.remove(flag)
            }
            b.sessionGroupId?.let { id ->
                val flag = "SessionGroup"
                // always remove
                flags.firstOrNull { it.startsWith(flag) }?.let { flags.remove(it) }
                // only add if positive
                if (id >= 0) flags.add("$flag=$id")
            }
        }
    }
}
