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

class SyslogIdentityTagTest {

    @Test
    fun givenAndroidEmulator_whenSyslogTag_thenIncludesAndroidIdentityTag() {
        val tag = TorConfig.SyslogIdentityTag.Builder { tag = "android" }!!

        assertEquals(2, tag.items.size)
        assertEquals(1, tag.items.filter { it.keyword == TorConfig.AndroidIdentityTag }.size)
    }
}
