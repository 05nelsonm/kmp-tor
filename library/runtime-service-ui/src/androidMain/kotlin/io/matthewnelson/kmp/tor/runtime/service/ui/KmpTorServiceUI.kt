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
package io.matthewnelson.kmp.tor.runtime.service.ui

import android.app.Application
import android.content.res.Resources
import io.matthewnelson.kmp.tor.core.api.annotation.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.core.api.annotation.KmpTorDsl
import io.matthewnelson.kmp.tor.runtime.core.ThisBlock
import io.matthewnelson.kmp.tor.runtime.core.apply
import io.matthewnelson.kmp.tor.runtime.service.AndroidTorServiceUI
import io.matthewnelson.kmp.tor.runtime.service.NotificationInfo
import io.matthewnelson.kmp.tor.runtime.service.TorServiceConfig

/**
 * The "default" UI implementation for `kmp-tor:runtime-service`, serving
 * both as an example for others to model their own implementations after,
 * or to utilize when instantiating [TorServiceConfig.Foreground].
 *
 * @see [Factory]
 * */
@OptIn(ExperimentalKmpTorApi::class)
public class KmpTorServiceUI private constructor(
    args: Args,
): AndroidTorServiceUI<KmpTorServiceUI.Config>(args) {

    /**
     *
     * */
    public class Config(
        // TODO
    ): AndroidTorServiceUI.Config(mapOf(
        // TODO
        "TODO" to "TODO"
    )) {

        @Throws(Resources.NotFoundException::class)
        public override fun validate(app: Application) {
            // TODO
        }
    }

    /**
     * Factory class for [TorServiceConfig.Builder]
     *
     * e.g.
     *
     *     val factory = KmpTorServiceUI.Factory(
     *         defaultConfig = KmpTorServiceUI.Config(
     *             // ...
     *         ),
     *         info = NotificationInfo(
     *             // ...
     *         ),
     *         block = {
     *             // configure...
     *         }
     *     )
     *
     *     val config = TorServiceConfig.Foreground.Builder(factory) {
     *         // configure...
     *     }
     * */
    public class Factory private constructor(
        b: Builder,
    ): AndroidTorServiceUI.Factory<Config, KmpTorServiceUI>(
        b.defaultConfig,
        b.info
    ) {

        public constructor(
            defaultConfig: Config,
            info: NotificationInfo,
        ): this(defaultConfig, info, {})

        public constructor(
            defaultConfig: Config,
            info: NotificationInfo,
            block: ThisBlock<Builder>,
        ): this(Builder.of(defaultConfig, info).apply(block))

        @KmpTorDsl
        public class Builder private constructor(
            @JvmField
            public val defaultConfig: Config,
            @JvmField
            public val info: NotificationInfo,
        ) {

            // TODO: content pending intent
            // TODO: notification builder options

            internal companion object {

                @JvmSynthetic
                internal fun of(
                    defaultConfig: Config,
                    info: NotificationInfo,
                ): Builder = Builder(defaultConfig, info)
            }
        }

        protected override fun newInstanceProtected(args: Args): KmpTorServiceUI {
            return KmpTorServiceUI(args)
        }
    }
}
