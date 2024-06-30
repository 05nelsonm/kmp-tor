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
package io.matthewnelson.kmp.tor.runtime.service.ui.internal.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContentMessageUnitTest {

    @Test
    fun givenRateLimit_whenParsed_thenIsSeconds() {
        val line = "Rate limiting NEWNYM request: delaying by 10 second(s)"
        val content = ContentMessage.NewNym.RateLimited.of(line)
        assertIs<ContentMessage.NewNym.RateLimited.Seconds>(content)
        assertEquals(10, content.value)
    }
}
