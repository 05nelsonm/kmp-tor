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
import io.matthewnelson.kmp.tor.controller.common.config.HiddenServiceEntry
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig
import io.matthewnelson.kmp.tor.controller.common.control.TorControlOnion

/**
 * "ADD_ONION" SP KeyType ":" KeyBlob
 *           [SP "Flags=" Flag *("," Flag)]
 *           [SP "MaxStreams=" NumStreams]
 *           1*(SP "Port=" VirtPort ["," Target])
 *           *(SP "ClientAuth=" ClientName [":" ClientBlob]) CRLF
 *
 * KeyType =
 *   "NEW"     / ; The server should generate a key of algorithm KeyBlob
 *   "RSA1024" / ; The server should use the 1024 bit RSA key provided in as KeyBlob (v2).
 *   "ED25519-V3"; The server should use the ed25519 v3 key provided in as KeyBlob (v3).
 *
 * KeyBlob =
 *   "BEST"    / ; The server should generate a key using the "best" supported algorithm
 *                 (KeyType == "NEW"). [As of 0.4.2.3-alpha, ED25519-V3 is used]
 *   "RSA1024" / ; The server should generate a 1024 bit RSA key (KeyType == "NEW") (v2).
 *   "ED25519-V3"; The server should generate an ed25519 private key (KeyType == "NEW") (v3).
 *   String      ; A serialized private key (without whitespace)
 *
 * Flag =
 *   "DiscardPK" / ; The server should not include the newly generated private key as part
 *                   of the response.
 *   "Detach"    / ; Do not associate the newly created Onion Service to the current control
 *                   connection.
 *   "BasicAuth" / ; Client authorization is required using the "basic" method (v2 only).
 *   "NonAnonymous" /; Add a non-anonymous Single Onion Service. Tor checks this flag matches
 *                     its configured hidden service anonymity mode.
 *   "MaxStreamsCloseCircuit"; Close the circuit is the maximum streams allowed is reached.
 *
 * NumStreams = A value between 0 and 65535 which is used as the maximum
 *              streams that can be attached on a rendezvous circuit. Setting
 *              it to 0 means unlimited which is also the default behavior.
 * VirtPort = The virtual TCP Port for the Onion Service (As in the
 *            HiddenServicePort "VIRTPORT" argument).
 * Target = The (optional) target for the given VirtPort (As in the
 *          optional HiddenServicePort "TARGET" argument).
 * ClientName = An identifier 1 to 16 characters long, using only
 *              characters in A-Za-z0-9+-_ (no spaces) (v2 only).
 * ClientBlob = Authorization data for the client, in an opaque format
 *              specific to the authorization method (v2 only).
 *
 * https://torproject.gitlab.io/torspec/control-spec/#add_onion
 *
 * @see [TorControlOnion]
 * */
interface TorControlOnionAdd {

    /**
     * Adds a HiddenService for the given [privateKey] to the running
     * instance of Tor.
     * */
    suspend fun onionAdd(
        privateKey: OnionAddress.PrivateKey,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?,
    ): Result<HiddenServiceEntry>

    /**
     * Generates new public(onion address)/private keys for the given [type]
     * and adds the HiddenService to the running instance of Tor.
     * */
    suspend fun onionAddNew(
        type: OnionAddress.PrivateKey.Type,
        hsPorts: Set<TorConfig.Setting.HiddenService.Ports>,
        flags: Set<Flag>?,
        maxStreams: TorConfig.Setting.HiddenService.MaxStreams?
    ): Result<HiddenServiceEntry>

    enum class Flag {
        DiscardPK,
        Detach,
        NonAnonymous,
        MaxStreamsCloseCircuit
    }
}
