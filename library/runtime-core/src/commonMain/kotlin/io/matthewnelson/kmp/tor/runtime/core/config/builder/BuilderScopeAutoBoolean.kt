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

import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.internal.byte
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorOption] that utilize
 * [argument] parameters of `auto` (i.e. let tor decide),
 * `1` (i.e. `true`, or "enable"), or `0` (i.e. `false`, or "disable")
 * */
@KmpTorDsl
public class BuilderScopeAutoBoolean: TorSetting.BuilderScope {

    private constructor(option: TorOption): super(option, INIT)

    /**
     * Sets the [argument] to `auto`.
     * */
    @KmpTorDsl
    public fun auto(): BuilderScopeAutoBoolean {
        argument = TorOption.AUTO
        return this
    }

    /**
     * Sets the [argument] to `0`.
     * */
    @KmpTorDsl
    public fun disable(): BuilderScopeAutoBoolean {
        argument = false.byte.toString()
        return this
    }

    /**
     * Sets the [argument] to `1`.
     * */
    @KmpTorDsl
    public fun enable(): BuilderScopeAutoBoolean {
        argument = true.byte.toString()
        return this
    }

    internal companion object {

        @JvmSynthetic
        internal fun TorOption.toBuilderScopeAutoBoolean(): BuilderScopeAutoBoolean {
            // TorOption.default is checked for "auto", "1", or "0"
            // in tests to ensure proper default is always had for
            // TorSetting.BuilderScope.argument, and mitigate runtime
            // overhead a `require` block would have here. Test fails
            // if default for given TorOption is invalid.
            return BuilderScopeAutoBoolean(this)
        }
    }
}
