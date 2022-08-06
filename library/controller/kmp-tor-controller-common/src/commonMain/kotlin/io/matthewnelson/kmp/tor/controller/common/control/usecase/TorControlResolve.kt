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
package io.matthewnelson.kmp.tor.controller.common.control.usecase

import io.matthewnelson.kmp.tor.common.address.IPAddressV4

/**
 * "RESOLVE" *Option *Address CRLF
 *
 * Option = "mode=reverse"
 * Address = a hostname or IPv4 address
 *
 * https://torproject.gitlab.io/torspec/control-spec/#resolve
 * */
interface TorControlResolve {

    suspend fun resolve(
        hostname: String,
        reverse: Boolean,
    ): Result<Any?>

    suspend fun resolve(
        ipAddress: IPAddressV4,
        reverse: Boolean,
    ): Result<Any?>

}
