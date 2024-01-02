/*
 * Copyright (c) 2023 Matthew Nelson
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

package io.matthewnelson.kmp.tor.runtime.api.config

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.api.ThisBlock
import io.matthewnelson.kmp.tor.runtime.api.apply
import kotlin.jvm.JvmSynthetic

/**
 * [SocksPort](https://2019.www.torproject.org/docs/tor-manual.html.en#SocksPort)
 * */
@KmpTorDsl
public class IsolationFlagBuilder private constructor(private val flags: MutableSet<String>) {

    // To inhibit modification after closure
    private var isConfigured: Boolean = false
    // negative number to disable
    private var sessionGroupId: Int = -1

    @KmpTorDsl
    public fun IsolateClientAddr(): IsolationFlagBuilder {
        if (isConfigured) return this
        flags.add("IsolateClientAddr")
        return this
    }

    @KmpTorDsl
    public fun IsolateSOCKSAuth(): IsolationFlagBuilder {
        if (isConfigured) return this
        flags.add("IsolateSOCKSAuth")
        return this
    }

    @KmpTorDsl
    public fun IsolateClientProtocol(): IsolationFlagBuilder {
        if (isConfigured) return this
        flags.add("IsolateClientProtocol")
        return this
    }

    @KmpTorDsl
    public fun IsolateDestPort(): IsolationFlagBuilder {
        if (isConfigured) return this
        flags.add("IsolateDestPort")
        return this
    }

    @KmpTorDsl
    public fun IsolateDestAddr(): IsolationFlagBuilder {
        if (isConfigured) return this
        flags.add("IsolateDestAddr")
        return this
    }

    @KmpTorDsl
    public fun KeepAliveIsolateSOCKSAuth(): IsolationFlagBuilder {
        if (isConfigured) return this
        flags.add("KeepAliveIsolateSOCKSAuth")
        return this
    }

    /**
     * Declaring an [id] greater than or equal to 0 enables the flag.
     *
     * Declaring a negative [id] will disable the flag (the default).
     * */
    @KmpTorDsl
    public fun SessionGroup(id: Int): IsolationFlagBuilder {
        if (isConfigured) return this
        flags.firstOrNull {
            it.startsWith("SessionGroup=")
        }?.let { flags.remove(it) }

        sessionGroupId = id
        return this
    }

    @InternalKmpTorApi
    public sealed interface DSL<out R: Any> {

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
            val b = IsolationFlagBuilder(flags).apply(block)
            b.isConfigured = true
            b.sessionGroupId.let { id -> if (id >= 0) flags.add("SessionGroup=$id") }
        }
    }
}
