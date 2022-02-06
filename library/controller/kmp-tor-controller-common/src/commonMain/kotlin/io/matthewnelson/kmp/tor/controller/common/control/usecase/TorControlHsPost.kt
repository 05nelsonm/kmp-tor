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

import io.matthewnelson.kmp.tor.controller.common.control.TorControlHs

/**
 * "+HSPOST" *[SP "SERVER=" Server] [SP "HSADDRESS=" HSAddress]
 *           CRLF Descriptor CRLF "." CRLF
 *
 * Server = LongName
 * HSAddress = 56*Base32Character
 * Descriptor = The text of the descriptor formatted as specified
 *              in rend-spec.txt section 1.3.
 *
 * https://torproject.gitlab.io/torspec/control-spec/#hspost
 *
 * @see [TorControlHs]
 * */
interface TorControlHsPost {

    // TODO: Implement
//    suspend fun hsPost(): Result<Any?>

}
