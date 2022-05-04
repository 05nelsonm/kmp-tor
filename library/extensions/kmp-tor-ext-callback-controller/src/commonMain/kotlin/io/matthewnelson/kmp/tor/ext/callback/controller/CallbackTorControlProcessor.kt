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
package io.matthewnelson.kmp.tor.ext.callback.controller

import io.matthewnelson.kmp.tor.ext.callback.controller.common.control.*
import io.matthewnelson.kmp.tor.ext.callback.controller.common.control.usecase.*

interface CallbackTorControlProcessor:
    CallbackTorControlAuthenticate,
    CallbackTorControlChallengeAuth,
    CallbackTorControlCircuit,
    CallbackTorControlConfig,
    CallbackTorControlDescriptorPost,
    CallbackTorControlDropGuards,
    CallbackTorControlHs,
    CallbackTorControlInfoGet,
    CallbackTorControlInfoProtocol,
    CallbackTorControlMapAddress,
    CallbackTorControlOnion,
    CallbackTorControlOnionClientAuth,
    CallbackTorControlOwnershipDrop,
    CallbackTorControlOwnershipTake,
    CallbackTorControlResolve,
    CallbackTorControlSetEvents,
    CallbackTorControlSignal,
    CallbackTorControlStream,
    CallbackTorControlUseFeature
