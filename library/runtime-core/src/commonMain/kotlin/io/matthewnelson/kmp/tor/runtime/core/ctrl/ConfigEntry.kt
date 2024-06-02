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
package io.matthewnelson.kmp.tor.runtime.core.ctrl

import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.jvm.JvmField

/**
 * Holder for tor configuration entries.
 *
 * @see [TorCmd.Config.Get]
 * */
public data class ConfigEntry(

    /**
     * The [TorConfig] keyword for this entry.
     * */
    @JvmField
    public val keyword: TorConfig.Keyword,

    /**
     * The value tor is using for this [keyword], as returned by its
     * control connection.
     *
     * If empty, then tor's set value for the given [keyword] is the
     * default value, as defined by [TorConfig.Keyword.default].
     * */
    @JvmField
    public val setting: String,
) {

    /**
     * If tor is using the default value for the given [keyword] for this entry.
     * */
    @JvmField
    public val isDefault: Boolean = if (setting.isEmpty()) true else setting == keyword.default

    public override fun toString(): String = buildString {
        appendLine("ConfigEntry: [")
        append("    keyword: ")
        appendLine(keyword)
        append("    setting: ")
        appendLine(setting)
        append("    isDefault: ")
        appendLine(isDefault)
        append(']')
    }
}
