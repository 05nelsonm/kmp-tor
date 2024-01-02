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

@KmpTorDsl
public class UnixFlagBuilder private constructor(
    private val isControl: Boolean,
    private val flags: MutableSet<String>,
) {

    // To inhibit modification after closure
    private var isConfigured: Boolean = false

    @KmpTorDsl
    public fun GroupWritable(): UnixFlagBuilder {
        if (isConfigured) return this
        flags.add("GroupWritable")
        return this
    }

    @KmpTorDsl
    public fun WorldWritable(): UnixFlagBuilder {
        if (isConfigured) return this
        flags.add("WorldWritable")
        return this
    }

    /**
     * Only applicable for the Control Port
     * */
    @KmpTorDsl
    public fun RelaxDirModeCheck(): UnixFlagBuilder {
        if (isConfigured) return this
        if (!isControl) return this
        flags.add("RelaxDirModeCheck")
        return this
    }

    @InternalKmpTorApi
    public sealed interface DSL<out R: Any> {

        @KmpTorDsl
        public fun unixFlags(
            block: ThisBlock<UnixFlagBuilder>,
        ): R
    }

    internal companion object {

        @JvmSynthetic
        internal fun configure(
            isControl: Boolean,
            flags: MutableSet<String>,
            block: ThisBlock<UnixFlagBuilder>,
        ) { UnixFlagBuilder(isControl, flags).apply(block).isConfigured = true }
    }
}
