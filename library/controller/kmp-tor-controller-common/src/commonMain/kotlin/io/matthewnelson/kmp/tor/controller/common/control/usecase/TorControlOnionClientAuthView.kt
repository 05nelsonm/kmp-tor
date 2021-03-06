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

import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.controller.common.config.ClientAuthEntry
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth

/**
 * "ONION_CLIENT_AUTH_VIEW" [SP HSAddress] CRLF
 *
 * https://torproject.gitlab.io/torspec/control-spec/#onion_client_auth_view
 *
 * @see [TorControlOnionClientAuth]
 * */
interface TorControlOnionClientAuthView {

    /**
     * Returns a list of all [ClientAuthEntry]'s for all [OnionAddressV3]'s
     * */
    suspend fun onionClientAuthView(): Result<List<ClientAuthEntry>>

    /**
     * Returns the current [ClientAuthEntry] for the given [OnionAddressV3]
     * */
    suspend fun onionClientAuthView(address: OnionAddressV3): Result<ClientAuthEntry>

}
