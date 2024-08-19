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
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import kotlin.jvm.JvmSynthetic

/** @suppress */
@KmpTorDsl
@InternalKmpTorApi
public class RealBuilderScopeTorConfig private constructor(): TorConfig2.BuilderScope(INIT) {

    private val settings = LinkedHashSet<TorSetting>(16, 1.0f)

    @KmpTorDsl
    public override fun put(
        setting: TorSetting,
    ): TorConfig2.BuilderScope {
        TODO("Not yet implemented")
    }

    internal companion object {

        @JvmSynthetic
        internal fun of(
            other: TorConfig2?,
            create: (Set<TorSetting>) -> TorConfig2,
            block: ThisBlock<TorConfig2.BuilderScope>,
        ): TorConfig2 {
            val b = RealBuilderScopeTorConfig()

            if (other != null) {
                // TODO
            }

            b.apply(block)

            return create(b.settings)
        }
    }
}
