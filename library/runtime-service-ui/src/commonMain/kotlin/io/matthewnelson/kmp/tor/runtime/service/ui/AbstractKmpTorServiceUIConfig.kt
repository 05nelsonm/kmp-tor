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
@file:Suppress("OPTIONAL_DECLARATION_USAGE_IN_NON_COMMON_SOURCE")

package io.matthewnelson.kmp.tor.runtime.service.ui

import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.service.AbstractTorServiceUI
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic

@OptIn(ExperimentalKmpTorApi::class)
public abstract class AbstractKmpTorServiceUIConfig internal constructor(
    @JvmField
    public val enableActionRestart: Boolean,
    @JvmField
    public val enableActionStop: Boolean,
    fields: Map<String, Any?>,
    init: Any,
): AbstractTorServiceUI.Config(
    fields = mapOf(
        "enableActionRestart" to enableActionRestart,
        "enableActionStop" to enableActionStop,
    ).plus(fields)
) {

    protected companion object {

        @JvmSynthetic
        internal val INIT = Any()
    }

    init {
        check(init == INIT) { "AbstractKmpTorServiceUIConfig cannot be extended" }
    }
}
