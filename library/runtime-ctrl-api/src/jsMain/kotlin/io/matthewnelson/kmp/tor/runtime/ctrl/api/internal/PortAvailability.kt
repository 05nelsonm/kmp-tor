/*
 * Copyright (c) 2024 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT")

package io.matthewnelson.kmp.tor.runtime.ctrl.api.internal

import io.matthewnelson.kmp.tor.runtime.ctrl.api.address.IPAddress

internal actual class PortAvailability private constructor() {

    internal actual fun isAvailable(port: Int): Boolean {
        // check exception error code
        TODO()
    }

    internal actual companion object {

        internal actual fun of(address: IPAddress): PortAvailability {
            TODO()
        }
    }
}
