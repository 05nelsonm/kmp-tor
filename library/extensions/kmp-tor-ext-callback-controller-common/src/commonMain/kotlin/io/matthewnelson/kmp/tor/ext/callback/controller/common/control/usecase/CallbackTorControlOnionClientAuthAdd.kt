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

import io.matthewnelson.kmp.tor.common.address.OnionAddressV3
import io.matthewnelson.kmp.tor.common.clientauth.ClientName
import io.matthewnelson.kmp.tor.common.clientauth.OnionClientAuth.PrivateKey
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnionClientAuth.Flag
import io.matthewnelson.kmp.tor.ext.callback.controller.common.Task
import io.matthewnelson.kmp.tor.ext.callback.controller.common.TorCallback
import io.matthewnelson.kmp.tor.ext.callback.controller.common.control.CallbackTorControlOnionClientAuth

/**
 * "ONION_CLIENT_AUTH_ADD" SP HSAddress
 *                         SP KeyType ":" PrivateKeyBlob
 *                         [SP "ClientName=" Nickname]
 *                         [SP "Flags=" TYPE] CRLF
 *
 * HSAddress = 56*Base32Character
 * KeyType = "x25519" is the only one supported right now
 * PrivateKeyBlob = base64 encoding of x25519 key
 *
 * FLAGS is a comma-separated tuple of flags for this new client. For now, the
 * currently supported flags are:
 *
 *   "Permanent" - This client's credentials should be stored in the filesystem.
 *                 Filename will be in the following format:
 *
 *                     `HSAddress.auth_private`
 *
 *                 where `HSAddress` is the 56*Base32Character onion address w/o
 *                 the .onion appended.
 *
 *                 If this is not set, the client's credentials are ephemeral
 *                 and stored in memory.
 *
 * https://torproject.gitlab.io/torspec/control-spec/#onion_client_auth_add
 *
 * @see [CallbackTorControlOnionClientAuth]
 * */
interface CallbackTorControlOnionClientAuthAdd {

    fun onionClientAuthAdd(
        address: OnionAddressV3,
        key: PrivateKey,
        clientName: ClientName?,
        flags: Set<Flag>?,
        failure: TorCallback<Throwable>,
        success: TorCallback<Any?>,
    ): Task

}
