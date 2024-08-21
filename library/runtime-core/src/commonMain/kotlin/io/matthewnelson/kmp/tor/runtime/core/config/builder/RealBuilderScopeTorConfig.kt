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
package io.matthewnelson.kmp.tor.runtime.core.config.builder

import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig2
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import kotlin.jvm.JvmSynthetic

/** @suppress */
@KmpTorDsl
@InternalKmpTorApi
public class RealBuilderScopeTorConfig private constructor(): TorConfig2.BuilderScope(INIT) {

    private val settings = LinkedHashMap<Int, TorSetting>(16, 1.0f)

    @KmpTorDsl
    public override fun put(
        setting: TorSetting,
    ): TorConfig2.BuilderScope {
        val root = setting.items.first()

        val key = if (root.option.isUnique) {
            root.option.hashCode()
        } else {
            setting.hashCode()
        }

        settings[key] = setting

        return this
    }

    internal companion object {

        @JvmSynthetic
        internal fun build(
            create: (Set<TorSetting>) -> TorConfig2,
            block: ThisBlock<TorConfig2.BuilderScope>,
        ): TorConfig2 {
            val b = RealBuilderScopeTorConfig().apply(block)
            val entries = b.settings.entries
            val settings = entries.mapTo(LinkedHashSet(entries.size, 1.0f)) { it.value }
            return create(settings)
        }
    }
}
