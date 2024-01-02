/*
 * Copyright (c) 2023 Matthew Nelson
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
package io.matthewnelson.kmp.tor.runtime.api.config

import kotlin.test.Test
import kotlin.test.assertEquals

class IsolationFlagBuilderUnitTest {

    @Test
    fun givenSessionGroup_whenNegative_thenRemoves() {
        val flags = mutableSetOf<String>()

        IsolationFlagBuilder.configure(flags) { SessionGroup(5) }
        assertEquals("SessionGroup=5", flags.first())

        IsolationFlagBuilder.configure(flags) { SessionGroup(4) }
        assertEquals(1, flags.size)
        assertEquals("SessionGroup=4", flags.first())

        IsolationFlagBuilder.configure(flags) { SessionGroup(-1) }
        assertEquals(0, flags.size)
    }

    @Test
    fun givenLambdaClosure_whenConfigurationAttempt_thenDoesNothing() {
        val flags = mutableSetOf<String>()
        var builder: IsolationFlagBuilder? = null
        IsolationFlagBuilder.configure(flags) {
            builder = IsolateDestAddr()
        }

        assertEquals(1, flags.size)
        builder!!.IsolateClientProtocol()
        assertEquals(1, flags.size)
    }
}
