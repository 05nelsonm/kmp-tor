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

import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.tor.core.api.annotation.InternalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.core.config.TorConfig
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.Companion.toSetting
import io.matthewnelson.kmp.tor.runtime.core.config.TorSetting.LineItem.Companion.toLineItem
import io.matthewnelson.kmp.tor.runtime.core.config.builder.BuilderScopePort.Companion.EXTRA_REASSIGNABLE
import io.matthewnelson.kmp.tor.runtime.core.internal.byte
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

/**
 * Helpers for `kmp-tor:runtime`.
 *
 * **NOTE:** These are internal APIs and should not
 * be relied on. They are subject to change.
 *
 * @suppress
 * */
@KmpTorDsl
@InternalKmpTorApi
public class RealBuilderScopeTorConfig private constructor(): TorConfig.BuilderScope(INIT) {

    private val settings = LinkedHashMap<Int, TorSetting>(16, 1.0f)

    @get:JvmName("containsControlPort")
    public var containsControlPort: Boolean = false
        private set

    @get:JvmName("containsSocksPort")
    public var containsSocksPort: Boolean = false
        private set

    @get:JvmName("containsDormantCanceledByStartup")
    public val containsDormantCanceledByStartup: Boolean get() {
        return settings[TorOption.DormantCanceledByStartup.hashCode()] != null
    }

    @get:JvmName("containsCacheDirectory")
    public val containsCacheDirectory: Boolean get() {
        return settings[TorOption.CacheDirectory.hashCode()] != null
    }

    @get:JvmName("containsControlPortWriteToFile")
    public val containsControlPortWriteToFile: Boolean get() {
        return settings[TorOption.ControlPortWriteToFile.hashCode()] != null
    }

    @get:JvmName("containsCookieAuthFile")
    public val containsCookieAuthFile: Boolean get() {
        return settings[TorOption.CookieAuthFile.hashCode()] != null
    }

    @get:JvmName("cookieAuthenticationOrNull")
    public val cookieAuthenticationOrNull: Boolean? get() {
        val setting = settings[TorOption.CookieAuthentication.hashCode()] ?: return null
        return setting.items.first().argument == true.byte.toString()
    }

    @get:JvmName("dataDirectoryOrNull")
    public val dataDirectoryOrNull: File? get() {
        return settings[TorOption.DataDirectory.hashCode()]
            ?.items
            ?.first()
            ?.argument
            ?.toFile()
    }

    public fun removeCookieAuthFile() {
        settings.remove(TorOption.CookieAuthFile.hashCode())
    }

    @KmpTorDsl
    public override fun put(
        setting: TorSetting,
    ): TorConfig.BuilderScope {
        val root = setting.items.first()

        val key = if (root.option.isUnique) {
            root.option.hashCode()
        } else {
            setting.hashCode()
        }

        settings[key] = setting

        when (root.option) {
            is TorOption.ControlPort,
            is TorOption.__ControlPort -> {
                containsControlPort = true
            }
            is TorOption.SocksPort,
            is TorOption.__SocksPort -> {
                containsSocksPort = true
            }
        }

        return this
    }

    public companion object {

        @JvmSynthetic
        internal fun build(
            create: (Set<TorSetting>) -> TorConfig,
            block: ThisBlock<TorConfig.BuilderScope>,
        ): TorConfig {
            val b = RealBuilderScopeTorConfig().apply(block)
            val entries = b.settings.entries
            val settings = entries.mapTo(LinkedHashSet(entries.size, 1.0f)) { it.value }
            return create(settings)
        }

        @JvmStatic
        @InternalKmpTorApi
        public fun reassignTCPPortAutoOrNull(setting: TorSetting): TorSetting? {
            val root = setting.items.first()
            if (root.isHiddenService) return null
            if (!root.isPortDistinct) return null
            if (setting.extras[EXTRA_REASSIGNABLE] != true) return null

            val items = setting.items.toMutableList()
            val removed = items.removeAt(0)
            val reassigned = removed.option.toLineItem(TorOption.AUTO, removed.optionals)
            items.add(0, reassigned)

            return items.toImmutableSet().toSetting(setting.extras)
        }
    }
}
