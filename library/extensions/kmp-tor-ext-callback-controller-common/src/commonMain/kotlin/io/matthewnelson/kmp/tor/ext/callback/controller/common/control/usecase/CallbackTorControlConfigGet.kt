/*
 * Copyright (c) 2022 Matthew Nelson
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
package io.matthewnelson.kmp.tor.ext.callback.controller.common.control.usecase

import io.matthewnelson.kmp.tor.controller.common.config.ConfigEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.ext.callback.common.Task
import io.matthewnelson.kmp.tor.ext.callback.common.TorCallback
import io.matthewnelson.kmp.tor.ext.callback.controller.common.control.CallbackTorControlConfig

/**
 * "GETCONF" 1*(SP keyword) CRLF
 *
 * https://torproject.gitlab.io/torspec/control-spec/#getconf
 *
 * @see [CallbackTorControlConfig]
 * */
interface CallbackTorControlConfigGet {

    fun configGet(
        keyword: TorConfig.KeyWord,
        failure: TorCallback<Throwable>?,
        success: TorCallback<List<ConfigEntry>>,
    ): Task

    fun configGet(
        keywords: Set<TorConfig.KeyWord>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<List<ConfigEntry>>,
    ): Task
}
