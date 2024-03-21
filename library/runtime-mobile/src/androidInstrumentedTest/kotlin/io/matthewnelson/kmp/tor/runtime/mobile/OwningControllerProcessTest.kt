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
package io.matthewnelson.kmp.tor.runtime.mobile

import io.matthewnelson.kmp.tor.runtime.core.TorConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OwningControllerProcessTest {

    @Test
    fun givenSetting_whenDefault_thenIsAsExpected() {
        val setting = TorConfig.__OwningControllerProcess.Builder {
            // Default PID should match as it's using reflection
            assertEquals(android.os.Process.myPid(), processId)
        }
        assertNotNull(setting)
        println(setting)
    }
}
