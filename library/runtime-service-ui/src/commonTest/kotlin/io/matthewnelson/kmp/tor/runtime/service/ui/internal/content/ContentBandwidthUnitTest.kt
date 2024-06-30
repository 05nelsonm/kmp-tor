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
import kotlin.test.assertIs
import kotlin.test.assertIsNot
import kotlin.test.assertTrue

class ContentBandwidthUnitTest {

    @Test
    fun givenCopy_whenDownZeroUpZero_thenIsSingletonZeroInstance() {
        val down = ContentBandwidth.ZERO.copy(down = 2)
        val up = ContentBandwidth.ZERO.copy(up = 2)
        assertIsNot<ContentBandwidth.ZERO>(down)
        assertIs<ContentBandwidth.ZERO>(down.copy(down = 0))
        assertIs<ContentBandwidth.ZERO>(up.copy(up = 0))
    }

    @Test
    fun givenZEROInstance_whenToString_thenNameIsContentBandwidth() {
        val zero = ContentBandwidth.ZERO.toString()
        assertTrue(zero.startsWith("ContentBandwidth"))
    }
}
