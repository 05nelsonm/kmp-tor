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
package io.matthewnelson.kmp.tor.runtime.core.config

import kotlin.test.Test
import kotlin.test.assertEquals

class IntervalUnitUnitTest {

    @Test
    fun givenSECONDS_whenArgumentOf_thenFormatsAsExpected() {
        assertEquals("5 seconds", IntervalUnit.SECONDS.of(5))
    }

    @Test
    fun givenMINUTES_whenArgumentOf_thenFormatsAsExpected() {
        assertEquals("5 minutes", IntervalUnit.MINUTES.of(5))
    }

    @Test
    fun givenHOURS_whenArgumentOf_thenFormatsAsExpected() {
        assertEquals("5 hours", IntervalUnit.HOURS.of(5))
    }

    @Test
    fun givenDAYS_whenArgumentOf_thenFormatsAsExpected() {
        assertEquals("5 days", IntervalUnit.DAYS.of(5))
    }

    @Test
    fun givenWEEKS_whenArgumentOf_thenFormatsAsExpected() {
        assertEquals("5 weeks", IntervalUnit.WEEKS.of(5))
    }

    @Test
    fun givenMONTHS_whenArgumentOf_thenFormatsAsExpected() {
        assertEquals("5 months", IntervalUnit.MONTHS.of(5))
    }
}
