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

class SocksFlagBuilderUnitTest {

    @Test
    fun givenLambdaClosure_whenConfigurationAttempt_thenDoesNothing() {
        val flags = mutableSetOf<String>()
        var builder: SocksFlagBuilder? = null
        SocksFlagBuilder.configure(flags) {
            builder = CacheDNS()
        }

        assertEquals(1, flags.size)
        builder!!.NoIPv4Traffic()
        assertEquals(1, flags.size)
    }
}
