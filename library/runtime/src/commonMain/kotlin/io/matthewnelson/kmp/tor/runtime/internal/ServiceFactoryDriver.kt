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
package io.matthewnelson.kmp.tor.runtime.internal

import io.matthewnelson.kmp.tor.common.api.ExperimentalKmpTorApi
import io.matthewnelson.kmp.tor.runtime.AbstractTorRuntime
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.TorEvent
import kotlin.jvm.JvmSynthetic

@ExperimentalKmpTorApi
internal sealed class ServiceFactoryDriver(
    staticTag: String?,
    observersRuntimeEvent: Set<RuntimeEvent.Observer<*>>,
    defaultExecutor: OnEvent.Executor,
    observersTorEvent: Set<TorEvent.Observer>,
    syntheticAccess: Any,
): AbstractTorRuntime(
    staticTag,
    observersRuntimeEvent,
    defaultExecutor,
    observersTorEvent,
    syntheticAccess,
) {

    @get:JvmSynthetic
    internal abstract val binder: TorRuntime.ServiceFactory.Binder
}
