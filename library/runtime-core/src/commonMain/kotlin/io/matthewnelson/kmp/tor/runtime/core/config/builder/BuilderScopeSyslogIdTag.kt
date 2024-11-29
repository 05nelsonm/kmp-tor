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

import io.matthewnelson.kmp.tor.common.api.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import kotlin.jvm.JvmSynthetic

/**
 * A DSL builder scope for configuring [TorOption.SyslogIdentityTag].
 *
 * **NOTE:** [tag] must be called to change the [argument] from
 * its default value (an empty string) to the value desired. Must
 * be a non-blank, single line value. Otherwise, will cause a
 * failure when build is called.
 *
 * @see [TorOption.SyslogIdentityTag.asSetting]
 * */
@KmpTorDsl
public class BuilderScopeSyslogIdTag: TorSetting.BuilderScope {

    private constructor(): super(TorOption.SyslogIdentityTag, INIT)

    /**
     * Set the [argument] to the specified [value].
     *
     * **NOTE:** [tag] must be called to change the [argument] from
     * its default value (an empty string) to the value desired. Must
     * be a non-blank, single line value. Otherwise, will cause a
     * failure when build is called.
     * */
    @KmpTorDsl
    public fun tag(
        value: String,
    ): BuilderScopeSyslogIdTag {
        argument = value
        return this
    }

    internal companion object {

        @JvmSynthetic
        internal fun get(): BuilderScopeSyslogIdTag = BuilderScopeSyslogIdTag()
    }
}
