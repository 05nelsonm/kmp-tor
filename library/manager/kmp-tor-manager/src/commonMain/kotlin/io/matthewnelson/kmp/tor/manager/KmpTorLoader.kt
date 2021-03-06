/*
 * Copyright (c) 2021 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.matthewnelson.kmp.tor.manager

import io.matthewnelson.kmp.tor.controller.TorController
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.file.Path
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent
import io.matthewnelson.kmp.tor.manager.common.exceptions.TorManagerException
import io.matthewnelson.kmp.tor.manager.internal.TorStateMachine
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmSynthetic

expect abstract class KmpTorLoader {

    companion object {
        @JvmSynthetic
        internal fun removeInstanceRunLock(instanceId: String)
    }

    /**
     * Will exclude settings for the given [TorConfig.KeyWord]
     * when validating the provided config.
     * */
    protected open val excludeSettings: Set<TorConfig.KeyWord>

    @Throws(TorManagerException::class, CancellationException::class)
    protected abstract suspend fun startTor(
        configLines: List<String>,
        notify: (TorManagerEvent.Log) -> Unit,
    )

    @Throws(TorManagerException::class)
    protected open fun setUnixDirPermissions(dir: Path)

    @JvmSynthetic
    internal open suspend fun load(
        instanceId: String,
        managerScope: CoroutineScope,
        stateMachine: TorStateMachine,
        notify: (TorManagerEvent) -> Unit,
    ): Result<Pair<TorController, TorConfig?>>

    @JvmSynthetic
    internal open fun close()

    @JvmSynthetic
    internal open fun cancelTorJob()
}
