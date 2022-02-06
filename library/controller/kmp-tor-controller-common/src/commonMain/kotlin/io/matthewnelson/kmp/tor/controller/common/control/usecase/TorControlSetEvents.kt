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

import io.matthewnelson.kmp.tor.controller.common.events.TorEvent

/**
 * "SETEVENTS" [SP "EXTENDED"] *(SP EventCode) CRLF
 *
 * Any events not listed in the SETEVENTS line are turned off; thus, sending
 * SETEVENTS with an empty body turns off all event reporting.
 *
 * https://torproject.gitlab.io/torspec/control-spec/#setevents
 * */
interface TorControlSetEvents {

    suspend fun setEvents(events: Set<TorEvent>, extended: Boolean = false): Result<Any?>

}
