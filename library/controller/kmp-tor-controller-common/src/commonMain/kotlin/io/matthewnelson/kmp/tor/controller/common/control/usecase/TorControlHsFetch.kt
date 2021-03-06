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

import io.matthewnelson.kmp.tor.common.address.OnionAddress
import io.matthewnelson.kmp.tor.common.server.Server
import io.matthewnelson.kmp.tor.controller.common.control.TorControlHs

/**
 * "HSFETCH" SP (HSAddress / "v" Version "-" DescId)
 *           *[SP "SERVER=" Server] CRLF
 *
 * HSAddress = 16*Base32Character / 56*Base32Character
 * Version = "2" / "3"
 * DescId = 32*Base32Character
 * Server = LongName
 *
 * https://torproject.gitlab.io/torspec/control-spec/#hsfetch
 *
 * @see [TorControlHs]
 * */
interface TorControlHsFetch {

    suspend fun hsFetch(address: OnionAddress): Result<Any?>

    suspend fun hsFetch(address: OnionAddress, server: Server.Fingerprint): Result<Any?>

    suspend fun hsFetch(address: OnionAddress, servers: Set<Server.Fingerprint>): Result<Any?>

}
