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

import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlMapAddress
import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlMapAddress.*
import io.matthewnelson.kmp.tor.ext.callback.common.Task
import io.matthewnelson.kmp.tor.ext.callback.common.TorCallback

/**
 * "MAPADDRESS" 1*(Address "=" Address SP) CRLF
 *
 * https://torproject.gitlab.io/torspec/control-spec/#mapaddress
 *
 * @see [TorControlMapAddress]
 * */
interface CallbackTorControlMapAddress {

    fun mapAddress(
        mapping: Mapping,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Mapped>,
    ): Task

    fun mapAddress(
        mappings: Set<Mapping>,
        failure: TorCallback<Throwable>?,
        success: TorCallback<Set<Mapped>>,
    ): Task

}
