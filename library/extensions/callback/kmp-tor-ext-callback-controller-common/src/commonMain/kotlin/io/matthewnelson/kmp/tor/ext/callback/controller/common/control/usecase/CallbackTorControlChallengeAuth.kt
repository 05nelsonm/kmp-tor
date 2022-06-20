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

/**
 * "AUTHCHALLENGE" SP "SAFECOOKIE"
 *                 SP ClientNonce
 *                 CRLF
 *
 * ClientNonce = 2*HEXDIG / QuotedString
 *
 * https://torproject.gitlab.io/torspec/control-spec/#authchallenge
 * */
interface CallbackTorControlChallengeAuth {

    // TODO: Create wrapper for ClientNonce
//    suspend fun challengeAuth(clientNonce: String): Result<Map<String, String>>

}
