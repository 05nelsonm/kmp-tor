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
package io.matthewnelson.kmp.tor.runtime.test

import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.common.api.ResourceLoader
import io.matthewnelson.kmp.tor.runtime.TorRuntime

@OptIn(ExperimentalKmpTorApi::class)
class TestServiceFactory(
    val initializer: Initializer,
    var serviceStart: ((Binder) -> Unit)? = null,
): TorRuntime.ServiceFactory(initializer) {

    val isLoaderExec: Boolean get() = environment().loader is ResourceLoader.Tor.Exec

    val testBinder: Binder get() = binder

    override fun startService() {
        serviceStart?.invoke(binder) ?: binder.onBind(emptySet(), null, emptySet(), emptySet())
    }
}
